package org.artifactory.addon.release.bundle.airgap;

import lombok.NonNull;

/**
 * @author Yevdo Abramov
 * Created on 28/07/2020
 */
public class AirGapReleaseBundleValidationException extends Exception {

    public AirGapReleaseBundleValidationException(@NonNull String message) {
        super(message);
    }

    public AirGapReleaseBundleValidationException(@NonNull String message, @NonNull Throwable cause) {
        super(message, cause);
    }
}
