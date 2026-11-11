package org.artifactory.addon.release.bundle.airgap;

import lombok.NonNull;

/**
 * @author Yevdo Abramov
 * Created on 20/07/2020
 */
public class AirGapReleaseBundleException extends Exception {
    public AirGapReleaseBundleException(@NonNull Exception e) {
        super(e.getMessage(), e);
    }

    public AirGapReleaseBundleException(@NonNull String message) {
        super(message);
    }

    public AirGapReleaseBundleException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
