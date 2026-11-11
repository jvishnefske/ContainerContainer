package org.artifactory.cleanup.exceptions;

/**
 * Represents cleanup policies REST API client error. A corresponding request will get responded by a 400 HTTP status
 * code.
 *
 * @author Oleksandr Legenkiy
 */
public class PackageCleanupDisabledException extends RuntimeException {

    public PackageCleanupDisabledException(String message) {
        super(message);
    }
}
