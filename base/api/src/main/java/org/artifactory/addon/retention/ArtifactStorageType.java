package org.artifactory.addon.retention;

/**
 * @author volodymyrm
 */
public enum ArtifactStorageType {
    COLD("cold"),
    WARM("warm");

    private final String value;

    ArtifactStorageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}