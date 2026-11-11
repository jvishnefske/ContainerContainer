package org.artifactory.descriptor.retention;

public enum ArtifactRetentionOperationType {
    ARCHIVE("Archive"),
    ARTIFACT_RESTORE("Artifact Restore"),
    PACKAGE_RESTORE("Package Restore");

    private final String name;

    ArtifactRetentionOperationType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
