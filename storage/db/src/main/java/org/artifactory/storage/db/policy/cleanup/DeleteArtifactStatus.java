package org.artifactory.storage.db.policy.cleanup;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @author Hussein Alshqerat
 */
@AllArgsConstructor
public enum DeleteArtifactStatus {

    DELETED(0),
    SKIPPED(1),
    ERROR(2),
    NOT_FOUND(3),
    ARCHIVED(4),
    DRY_RUN_COMPLETED(5),
    DRY_RUN_ERROR(6),
    RESTORE_NOT_INITIALIZED(7),
    PENDING_WARM(8),
    WARMED(9),
    RESTORED(10),
    PARTIALLY_DELETED(11),
    INTERNAL_ERROR(12);


    @Getter
    private final int code;

    public static String getByCode(int code) {
        DeleteArtifactStatus deleteStatus = getForCode(code);
        return deleteStatus != null ? deleteStatus.toString() : null;
    }

    public static DeleteArtifactStatus getForCode(int code) {
        return Arrays.stream(values())
                .filter(v -> v.getCode() == code)
                .findFirst().orElse(null);
    }
}