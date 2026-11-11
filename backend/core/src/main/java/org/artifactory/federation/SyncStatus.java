package org.artifactory.federation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SyncStatus {
    FEDERATED("federated"),
    DELAYED("delayed"),
    OUT_OF_SYNC("out_of_sync");
    private final String displayName;

    SyncStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static SyncStatus fromValue(String value) {
        for (SyncStatus status : values()) {
            if (status.getDisplayName().equals(value)) {
                return status;
            }
        }
        return null;
    }
}