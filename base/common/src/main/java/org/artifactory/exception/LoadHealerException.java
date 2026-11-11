package org.artifactory.exception;

/**
 * LoadHealerException is a runtime exception that is thrown when load healer block an activity.
 *
 * @author Hussein Alshqerat
 */
public class LoadHealerException extends RuntimeException {

    public LoadHealerException(String message) {
        super(message);
    }
}
