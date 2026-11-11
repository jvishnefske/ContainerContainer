package org.artifactory.sensitive;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.DynamicSensitiveKeyRotationCapable;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.repo.remote.CustomHttpHeader;
import org.jfrog.security.crypto.EncryptionWrapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sensitive handler for remote repository custom HTTP headers.
 */
@Component
public class CustomHttpHeadersSensitiveHandler
        implements DynamicSensitiveKeyRotationCapable<List<CustomHttpHeader>> {

    private ArtifactoryHome artifactoryHome;

    @PostConstruct
    public void init() {
        artifactoryHome = ArtifactoryHome.get();
    }

    @Override
    public void validate(@Nullable List<CustomHttpHeader> value) {
        // Structural validation is handled by RemoteRepositoryCreateUpdateHelper.
    }

    @Override
    public void encrypt(@Nullable List<CustomHttpHeader> value) {
        if (value == null) {
            return;
        }
        for (CustomHttpHeader header : value) {
            if (isSensitiveWithValue(header)) {
                header.setValue(CryptoHelper.encryptIfNeeded(artifactoryHome, header.getValue()));
            }
        }
    }

    @Override
    public void decrypt(@Nullable List<CustomHttpHeader> value) {
        if (value == null) {
            return;
        }
        for (CustomHttpHeader header : value) {
            if (isSensitiveWithValue(header)) {
                header.setValue(CryptoHelper.decryptIfNeeded(artifactoryHome, header.getValue()));
            }
        }
    }

    @Override
    public List<CustomHttpHeader> mask(@Nullable List<CustomHttpHeader> value) {
        if (value == null) {
            return null;
        }
        for (CustomHttpHeader header : value) {
            if (header != null && header.isSensitive() && StringUtils.isNotBlank(header.getValue())) {
                header.setValue("***");
            }
        }
        return value;
    }

    @Override
    public Set<String> sensitiveLeafPaths(@Nullable List<CustomHttpHeader> value, String pathDelimiter) {
        if (value == null) {
            return Set.of();
        }
        return value.stream()
                .filter(header -> header != null && header.isSensitive())
                .map(header -> header.getName() + pathDelimiter + "value")
                .collect(Collectors.toSet());
    }

    @Override
    public void encryptForKeyRotation(EncryptionWrapper newArtifactoryWrapper,
            @Nullable List<CustomHttpHeader> value) {
        Objects.requireNonNull(newArtifactoryWrapper, "newArtifactoryWrapper");
        if (value == null) {
            return;
        }
        for (CustomHttpHeader header : value) {
            if (isSensitiveWithValue(header)) {
                header.setValue(
                        CryptoHelper.encryptIfNeeded(artifactoryHome, newArtifactoryWrapper, header.getValue()));
            }
        }
    }

    private static boolean isSensitiveWithValue(CustomHttpHeader header) {
        return header != null && header.isSensitive() && StringUtils.isNotBlank(header.getValue());
    }
}
