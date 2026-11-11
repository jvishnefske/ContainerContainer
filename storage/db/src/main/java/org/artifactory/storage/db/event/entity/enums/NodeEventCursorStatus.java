package org.artifactory.storage.db.event.entity.enums;

import jakarta.annotation.Nullable;

public enum NodeEventCursorStatus {
    HEALTHY,
    PENDING_FS,
    FS_RUNNING,
    OUT_OF_SYNC,
    EXHAUSTED,
    DISABLED,
    DISABLED_BY_SYSTEM,
    STOPPED,
    ;

    public static NodeEventCursorStatus forName(@Nullable String name) {
        for (NodeEventCursorStatus eventStatus : values()) {
            if (eventStatus.name().equals(name)) {
                return eventStatus;
            }
        }
        return null;
    }
}
