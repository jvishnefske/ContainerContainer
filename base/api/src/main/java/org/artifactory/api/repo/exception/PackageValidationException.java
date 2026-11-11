package org.artifactory.api.repo.exception;

import java.io.IOException;

/**
 * @author Ron Malka
 */
public class PackageValidationException extends IOException {
    public PackageValidationException(String message) {
        super(message);
    }
}
