package org.artifactory.retention.exceptions;

/**
 * Represents Retention REST API client error request which will get responded by a 400 HTTP status code.
 * {@see RetentionWrongInstanceExceptionMapper}
 *
 * @author Tamar Rosen
 */
public class ArtifactRetentionWrongInstanceException extends RuntimeException {

    public ArtifactRetentionWrongInstanceException(String message) {
        super(message);
    }
}
