package org.artifactory.storage.db.repo.service.mapper;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads repository configuration blobs into {@link RepoConfig} objects and is the single place that enforces the
 * policy for an unknown {@link RepoType} (e.g. a type written by a newer Artifactory version before a rollback).
 *
 * <p>A dedicated {@link ObjectMapper} lets an unknown type fall back to {@link RepoType#Generic} rather than fail.
 * The {@link ConstantValues#repoConfigSkipUnknownPackageType} flag then decides what to do with such a repo: skip it
 * ({@code read} returns {@code null}; this is the default) or load it as the fallback Generic. The stored blob is
 * never rewritten in either case, so the original type survives a following re-upgrade. Each affected repository key
 * is logged once per JVM.</p>
 *
 * <p>This leniency is limited to this reader. The default {@link org.jfrog.common.JsonUtils} mapper stays strict, so
 * REST input and the config converters still reject unknown types, and any other deserialization failure propagates
 * as an {@link IllegalStateException}.</p>
 */
@Slf4j
final class RepoConfigBlobReader {

    private static final ObjectMapper BLOB_MAPPER = createBlobMapper();
    private static final Set<String> LOGGED_UNKNOWN_TYPE_KEYS = ConcurrentHashMap.newKeySet();

    private RepoConfigBlobReader() {
    }

    /**
     * Reads the blob of the given DB row into a {@link RepoConfig}.
     *
     * @return the deserialized config, or {@code null} when the row carries an unknown package type and the skip
     * policy is in effect
     * @throws IllegalStateException if the blob is missing, corrupt, or otherwise cannot be deserialized
     */
    @Nullable
    static RepoConfig read(DbRepositoryConfig dbRepo) {
        byte[] blob = dbRepo.getRepositoryInfoBlob();
        if (blob == null || blob.length == 0) {
            throw new IllegalStateException("Repository config blob is null or empty");
        }
        RepoConfig repoConfig;
        try {
            repoConfig = BLOB_MAPPER.readValue(blob, RepoConfig.class);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Failed to read repository config blob", e);
        }
        if (isCoercedToGeneric(repoConfig, dbRepo)) {
            boolean skip = ConstantValues.repoConfigSkipUnknownPackageType.getBoolean();
            logUnknownTypeOnce(dbRepo, skip);
            if (skip) {
                return null;
            }
        }
        return repoConfig;
    }

    /**
     * True when the blob was parsed to Generic but the DB column was not Generic (which happens when parsing falls
     * back to Generic because the stored type is unknown to this Artifactory version)
     */
    private static boolean isCoercedToGeneric(RepoConfig repoConfig, DbRepositoryConfig dbRepo) {
        return repoConfig.getPackageType() == RepoType.Generic
                && dbRepo.getPackageType() != null
                && !RepoType.Generic.getType().equalsIgnoreCase(dbRepo.getPackageType());
    }

    private static final String UNKNOWN_TYPE_MSG =
            "Unknown package type '{}' for repository '{}' (startup/cache load), likely after a rollback to a "
                    + "previous Artifactory version that predates the type; {}. The stored blob is left "
                    + "unchanged, so the original type is restored on re-upgrade.";

    private static void logUnknownTypeOnce(DbRepositoryConfig dbRepo, boolean skip) {
        if (!LOGGED_UNKNOWN_TYPE_KEYS.add(dbRepo.getKey())) {
            return;
        }
        String flag = ConstantValues.repoConfigSkipUnknownPackageType.getPropertyName();
        String action = skip
                ? "skipping the repository (set '" + flag + "=false' to load it as Generic instead)"
                : "loading it as Generic in memory (set '" + flag + "=true' to skip it instead)";
        log.error(UNKNOWN_TYPE_MSG, dbRepo.getPackageType(), dbRepo.getKey(), action);
    }

    @VisibleForTesting
    static void clearLoggedUnknownTypeKeys() {
        LOGGED_UNKNOWN_TYPE_KEYS.clear();
    }

    private static ObjectMapper createBlobMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // Ideally we'd start from a copy of the JsonUtils mapper to stay in sync with its configuration.
        // However, JsonUtils.getMapper() is protected and not accessible here. findAndRegisterModules()
        // mirrors JsonUtils's module-registration path (JAXB, parameter-names), so the two stay aligned
        // for all known package types (including "supportbundles" -> Support). If JsonUtils ever adds a
        // non-module configuration change, this mapper must be updated in tandem.
        mapper.findAndRegisterModules();
        mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public Object handleWeirdStringValue(DeserializationContext ctxt, Class<?> targetType,
                    String valueToConvert, String failureMsg) {
                if (RepoType.class.equals(targetType)) {
                    return RepoType.Generic;
                }
                return NOT_HANDLED;
            }
        });
        return mapper;
    }
}
