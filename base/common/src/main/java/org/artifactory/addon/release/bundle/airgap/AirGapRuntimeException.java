package org.artifactory.addon.release.bundle.airgap;

import lombok.NonNull;

/**
 * @author Eli Skoran 29/07/2020
 */
public class AirGapRuntimeException extends RuntimeException {

    public AirGapRuntimeException(@NonNull Exception e) {
        super(e);
    }

    public AirGapRuntimeException(@NonNull String message) {
        super(message);
    }

    public AirGapRuntimeException(@NonNull String message, @NonNull Exception e) {
        super(message, e);
    }
}
