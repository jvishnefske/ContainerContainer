package org.artifactory.security.access;

import org.springframework.security.core.AuthenticationException;

public class AccessAuthenticationPreconditionException extends AuthenticationException {

    public AccessAuthenticationPreconditionException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
