package org.artifactory.security.ldap;

import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

/**
 * @author Nadav Yogev
 */
public class UnavailableRememberMeException extends RememberMeAuthenticationException {

    public UnavailableRememberMeException(String msg, Throwable t) {
        super(msg, t);
    }

    public UnavailableRememberMeException(String msg) {
        super(msg);
    }
}
