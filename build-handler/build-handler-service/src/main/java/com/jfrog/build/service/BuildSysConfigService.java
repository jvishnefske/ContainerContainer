package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@RequiredArgsConstructor
public class BuildSysConfigService {

    public static final String SYS_EVIDENCE_ENABLED =
            "artifactory.evidence.enabled";
    public static final String SYS_DB_OPERATIONS_BULK_SIZE =
            "artifactory.db.operations.bulk.size";
    public static final String SYS_BUILD_RETENTION_ENABLED =
            "artifactory.build.retention.enabled";
    public static final String SYS_BUILD_RETENTION_ALWAYS_ASYNC =
            "artifactory.build.retention.always.async";
    public static final String SYS_BUILD_ARTIFACTS_QUERY_BY_BUILD_DATE_ENABLED =
            "artifactory.build.artifacts.query.by.build.date.enabled";
    public static final String SYS_BUILD_ARTIFACTS_QUERY_DEDUPLICATION_ENABLED =
            "artifactory.build.artifacts.query.deduplication.enabled";
    public static final String SYS_BUILD_FORCED_DELETE_ARTIFACTS =
            "artifactory.build.forced.delete.artifacts";
    public static final String SYS_BUILD_SEARCH_MAX_BATCH_SIZE =
            "artifactory.build.search.maxBatchSize";
    public static final String SYS_BUILD_UI_SKIP_DELETE_PERMISSION_CHECK =
            "artifactory.build.ui.skip.delete.permission.check";
    public static final String SYS_BUILD_BLOCK_DUPLICATE_ENTRIES =
            "artifactory.build.block.duplicate.entries";
    public static final String SYS_SEARCH_USER_QUERY_LIMIT =
            "artifactory.search.userQueryLimit";

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    @NonNull
    public String getDateTimeFormat() {
        return bridgeToArtifactory.getDateTimeFormat();
    }

    public boolean isEvidenceEnabled() {
        return bridgeToArtifactory.getSysConfig().getBoolean(SYS_EVIDENCE_ENABLED, true);
    }

    public boolean isBuildRetentionEnabled() {
        return getArtifactoryPropertyBoolean(SYS_BUILD_RETENTION_ENABLED);
    }

    public boolean isBuildRetentionAlwaysAsync() {
        return getArtifactoryPropertyBoolean(SYS_BUILD_RETENTION_ALWAYS_ASYNC);
    }

    public boolean isBuildArtifactsQueryByBuildDateEnabled() {
        return getArtifactoryPropertyBoolean(SYS_BUILD_ARTIFACTS_QUERY_BY_BUILD_DATE_ENABLED);
    }

    public boolean isBuildArtifactsQueryDeduplicationEnabled() {
        return getArtifactoryPropertyBoolean(SYS_BUILD_ARTIFACTS_QUERY_DEDUPLICATION_ENABLED);
    }

    public boolean isBuildForcedDeleteArtifacts() {
        return getArtifactoryPropertyBoolean(SYS_BUILD_FORCED_DELETE_ARTIFACTS);
    }

    public boolean isBuildBlockDuplicateEntries() {
        return getArtifactoryPropertyBoolean(SYS_BUILD_BLOCK_DUPLICATE_ENTRIES);
    }

    public boolean isBuildUiSkipDeletePermissionCheck() {
        return getArtifactoryPropertyBoolean(SYS_BUILD_UI_SKIP_DELETE_PERMISSION_CHECK);
    }

    public int getBuildSearchMaxBatchSize() {
        return getArtifactoryPropertyInteger(SYS_BUILD_SEARCH_MAX_BATCH_SIZE);
    }

    public int getSearchUserQueryLimit() {
        return getArtifactoryPropertyInteger(SYS_SEARCH_USER_QUERY_LIMIT);
    }

    public int getDbOperationsBulkSize() {
        return getArtifactoryPropertyInteger(SYS_DB_OPERATIONS_BULK_SIZE);
    }

    public void assertNotArtifactoryColdInstance() {
        if (bridgeToArtifactory.isColdInstance()) {
            throw badRequestException("Build Feature is not allowed in Cold instance");
        }
    }

    private boolean getArtifactoryPropertyBoolean(@NonNull String key) {
        return getArtifactoryProperty(key, Boolean.class);
    }

    private int getArtifactoryPropertyInteger(@NonNull String key) {
        return getArtifactoryProperty(key, Integer.class);
    }

    private <T> T getArtifactoryProperty(@NonNull String key, @NonNull Class<T> clazz) {
        return bridgeToArtifactory.getArtifactoryProperty(key, clazz);
    }
}
