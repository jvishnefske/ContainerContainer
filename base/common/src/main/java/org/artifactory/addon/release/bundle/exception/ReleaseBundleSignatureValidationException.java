package org.artifactory.addon.release.bundle.exception;

import lombok.NonNull;

/**
 * @author Yevdo Abramov
 * Created on 30/07/2020
 */
public class ReleaseBundleSignatureValidationException extends Exception {

    public ReleaseBundleSignatureValidationException(@NonNull String message) {
        super(message);
    }
}
