package org.artifactory.security.exceptions;

import org.springframework.security.authentication.DisabledException;

/**
 * Thrown when user is 'disabled'.
 *
 * @author Aviv Anidjar
 */

public class UserDisabledException extends DisabledException {


    public UserDisabledException(String msg) {
        super(msg);
    }

    public UserDisabledException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Produces UserDisabledException
     *
     * @return {@link UserDisabledException}
     */
    public static UserDisabledException instance() {
        return new UserDisabledException("User is disabled");
    }
}
