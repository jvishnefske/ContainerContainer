package org.artifactory.addon.retention;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;

import jakarta.annotation.Nullable;

/**
 * @author Aviv Anidjar
 */

@Data
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ArtifactRetentionLinkModel {
    private String serviceId;
    private ArtifactStorageType storageType;

    @Nullable
    private ArtifactRetentionColdLinkModel retentionColdLink;

    public ArtifactRetentionLinkModel() {
    }

    @Data
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class ArtifactRetentionColdLinkModel {
        private String url;
        private String serviceId;
    }
}