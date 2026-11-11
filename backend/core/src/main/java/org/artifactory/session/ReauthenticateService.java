package org.artifactory.session;

import org.artifactory.security.SimpleUser;

/**
 * @author Nadav Yogev
 */
public interface ReauthenticateService {

    /**
     * Reauthenticate a user from a given realm, and return the current user
     */
    SimpleUser reauthenticateByRealm(String username, String realm);
}
