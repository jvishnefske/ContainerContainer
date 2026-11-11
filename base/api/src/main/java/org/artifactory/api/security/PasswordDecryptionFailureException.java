package org.artifactory.api.security;

/**
 * Thrown on decryption failure
 */
public class PasswordDecryptionFailureException extends RuntimeException {

    public PasswordDecryptionFailureException(String msg, Exception inner) {
        super(msg, inner);
    }
}
