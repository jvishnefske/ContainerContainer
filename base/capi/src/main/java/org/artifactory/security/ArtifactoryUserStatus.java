package org.artifactory.security;

import java.util.Arrays;

/**
 * @author Aviv Anidjar
 */
public enum ArtifactoryUserStatus {

    INVITED("invited"),
    ENABLED("enabled"),
    DISABLED("disabled"),
    LOCKED("locked");

    private final String status;

    ArtifactoryUserStatus(String status) {
        this.status = status;
    }

    public static ArtifactoryUserStatus getByValue(String status) {
        return Arrays.stream(ArtifactoryUserStatus.values())
                .filter(elem -> elem.status.equals(status))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user status value: " + status));
    }
}
