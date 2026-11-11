package org.artifactory.exceptions;

/**
 * @author Tomer Slutsky
 */
public class HaFederationPropagationException extends RuntimeException {

    public HaFederationPropagationException(String errorMessage) {
        super(errorMessage);
    }
}
