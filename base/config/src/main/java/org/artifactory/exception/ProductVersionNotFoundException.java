package org.artifactory.exception;

/**
 * @author Andrey Tashlitsky
 */
public class ProductVersionNotFoundException extends RuntimeException {
    public ProductVersionNotFoundException(String message) {
        super(message);
    }
}
