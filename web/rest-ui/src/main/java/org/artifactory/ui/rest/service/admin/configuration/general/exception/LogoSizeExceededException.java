package org.artifactory.ui.rest.service.admin.configuration.general.exception;

/**
 * @author Andrey Tashlitsky
 */
public class LogoSizeExceededException extends RuntimeException {

    public LogoSizeExceededException(String message) {
        super(message);
    }
}
