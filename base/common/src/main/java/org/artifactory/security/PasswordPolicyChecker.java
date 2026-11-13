/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security;

import java.util.Optional;

/**
 * Checks a new password against the local password policy (IA6) without network access. The implementation lives
 * in backend/core and is published as a Spring bean; layers below it, such as
 * {@link org.artifactory.util.SecurityUtils#validatePasswordPolicy}, find it by type.
 * <p>
 * The policy applies when a password is set: the initial-admin claim, every password change and administrator
 * resets. It is not a login-time check, because a login password may be an API key, an access token or an LDAP or
 * plugin realm password that this server neither chose nor stores.
 */
public interface PasswordPolicyChecker {

    /**
     * Checks a candidate password for a user.
     *
     * @param username the account name, or {@code null} when unknown (the username rule is then skipped)
     * @param password the candidate password, or {@code null}
     * @return empty when the password is acceptable, otherwise a message naming the violated rules; the message
     * never contains the password
     */
    Optional<String> violation(String username, String password);
}
