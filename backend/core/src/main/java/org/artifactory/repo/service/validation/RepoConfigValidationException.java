package org.artifactory.repo.service.validation;

/**
 * This exception indicates a validation failure in repo config changes.
 *
 * @author Oleksandr Legenkiy
 */
public class RepoConfigValidationException extends RuntimeException {

    public RepoConfigValidationException(String message) {
        super(message);
    }
}
