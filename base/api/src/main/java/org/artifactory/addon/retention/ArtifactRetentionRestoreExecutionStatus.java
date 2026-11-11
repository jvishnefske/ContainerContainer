package org.artifactory.addon.retention;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author Omri Ziv
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ArtifactRetentionRestoreExecutionStatus extends ArtifactRetentionExecutionStatus {
    private DestinationConfiguration destinationConfiguration;
    private String fallbackLocation;

    public ArtifactRetentionRestoreExecutionStatus(DestinationConfiguration destinationConfiguration,
            String fallbackLocation) {
        this.destinationConfiguration = destinationConfiguration;
        this.fallbackLocation = fallbackLocation;
    }

    @AllArgsConstructor
    public enum DestinationConfiguration {
        ORIGINAL("Original"),
        CENTRAL("Central");

        String displayName;

        @JsonValue
        public String getDisplayName() {
            return displayName;
        }

        public static DestinationConfiguration fromOriginal(boolean useOriginal) {
            return useOriginal ? ORIGINAL : CENTRAL;
        }
    }
}
