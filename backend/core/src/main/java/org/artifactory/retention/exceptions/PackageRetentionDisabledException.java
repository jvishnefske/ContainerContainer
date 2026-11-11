package org.artifactory.retention.exceptions;

/**
 * @author Oleksandr Legenkiy
 */
public class PackageRetentionDisabledException extends RuntimeException {

    public PackageRetentionDisabledException(String msg) {
        super(msg);
    }
}
