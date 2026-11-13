/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.security.SecurityService;

import java.util.Objects;

/**
 * Pure decisions about the must-change-password flag ({@link UserInfo#isMustChangePassword()}, requirement IA5).
 * <p>
 * The flag marks an account whose password was not chosen by its holder. {@link SecurityServiceImpl} sets it when an
 * administrator sets or resets <em>another</em> user's password (create user, update user, password reset without
 * validation) and clears it only when the holder changes the password through the change-password flow, which
 * already refuses a new password equal to the old one. These functions hold no state and do no I/O.
 */
public final class MustChangePasswordRules {

    private MustChangePasswordRules() {
    }

    /**
     * Whether a password write is done by an administrator on behalf of someone else.
     *
     * @param actingUsername the authenticated user performing the write; may be null
     * @param actingIsAdmin  whether that user is an administrator
     * @param targetUsername the user whose password is written
     * @return true only if the actor is a named administrator other than the target and other than the internal
     * system user (internal flows that re-save users run as the system user and are not password resets)
     */
    public static boolean isWrittenByAnotherAdministrator(String actingUsername, boolean actingIsAdmin,
            String targetUsername) {
        return actingIsAdmin
                && StringUtils.isNotBlank(actingUsername)
                && !SecurityService.USER_SYSTEM.equals(actingUsername)
                && !UserInfo.isAnonymous(actingUsername)
                && !actingUsername.equalsIgnoreCase(targetUsername);
    }

    /**
     * Whether a user write carries a new internal password.
     *
     * @param newPassword      the password carried by the write (raw, or whatever the user object holds)
     * @param storedPassword   the password the stored user object holds, null if none or the user is new
     * @param passwordDisabled whether the write disables internal password login
     * @return true if the write carries a non-blank password that differs from the stored one and internal login is
     * not being disabled
     */
    public static boolean carriesNewPassword(String newPassword, String storedPassword, boolean passwordDisabled) {
        return !passwordDisabled
                && StringUtils.isNotBlank(newPassword)
                && !Objects.equals(newPassword, storedPassword);
    }

    /**
     * Whether a password write sets the flag.
     *
     * @return {@link #isWrittenByAnotherAdministrator} and {@link #carriesNewPassword} both hold
     */
    public static boolean setsFlag(String actingUsername, boolean actingIsAdmin, String targetUsername,
            String newPassword, String storedPassword, boolean passwordDisabled) {
        return isWrittenByAnotherAdministrator(actingUsername, actingIsAdmin, targetUsername)
                && carriesNewPassword(newPassword, storedPassword, passwordDisabled);
    }

    /**
     * Whether a credential (access token, API key) may be issued for a user.
     *
     * @param user the user the credential would be issued for; null when the user is not known to the store
     *             (external or transient users), in which case there is no flag to honour
     * @return false if the user must change the password first
     */
    public static boolean mayIssueCredential(UserInfo user) {
        return user == null || !user.isMustChangePassword();
    }
}
