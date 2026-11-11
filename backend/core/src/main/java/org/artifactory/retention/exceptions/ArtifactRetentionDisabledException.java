package org.artifactory.retention.exceptions;

/**
 * Represents Retention REST API client error request which will get responded by a 400 HTTP status code.
 * {@see RetentionExceptionMapper}
 *
 * @author Tamar Rosen
 */
public class ArtifactRetentionDisabledException extends RuntimeException {

    public ArtifactRetentionDisabledException(String message) {
        super(message);
    }
}
