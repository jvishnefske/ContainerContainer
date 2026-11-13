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

import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IA5: the pure must-change-password decisions. */
class MustChangePasswordRulesTest {

    @Test
    void onlyANamedAdministratorOtherThanTheTargetCounts() {
        assertTrue(MustChangePasswordRules.isWrittenByAnotherAdministrator("root", true, "bob"));
        assertFalse(MustChangePasswordRules.isWrittenByAnotherAdministrator("root", false, "bob"));
        assertFalse(MustChangePasswordRules.isWrittenByAnotherAdministrator("bob", true, "bob"));
        assertFalse(MustChangePasswordRules.isWrittenByAnotherAdministrator("BOB", true, "bob"));
        assertFalse(MustChangePasswordRules.isWrittenByAnotherAdministrator(SecurityService.USER_SYSTEM, true, "bob"));
        assertFalse(MustChangePasswordRules.isWrittenByAnotherAdministrator(UserInfo.ANONYMOUS, true, "bob"));
        assertFalse(MustChangePasswordRules.isWrittenByAnotherAdministrator(null, true, "bob"));
        assertFalse(MustChangePasswordRules.isWrittenByAnotherAdministrator(" ", true, "bob"));
    }

    @Test
    void onlyANonBlankChangedPasswordWithInternalLoginCounts() {
        assertTrue(MustChangePasswordRules.carriesNewPassword("new", null, false));
        assertTrue(MustChangePasswordRules.carriesNewPassword("new", "old", false));
        assertFalse(MustChangePasswordRules.carriesNewPassword("same", "same", false));
        assertFalse(MustChangePasswordRules.carriesNewPassword("", null, false));
        assertFalse(MustChangePasswordRules.carriesNewPassword(null, null, false));
        assertFalse(MustChangePasswordRules.carriesNewPassword("new", null, true));
    }

    @Test
    void setsFlagNeedsBoth() {
        assertTrue(MustChangePasswordRules.setsFlag("root", true, "bob", "new", null, false));
        assertFalse(MustChangePasswordRules.setsFlag("bob", true, "bob", "new", null, false));
        assertFalse(MustChangePasswordRules.setsFlag("root", true, "bob", "same", "same", false));
    }

    @Test
    void credentialsAreIssuedOnlyToUnflaggedOrUnknownUsers() {
        assertTrue(MustChangePasswordRules.mayIssueCredential(null));
        assertTrue(MustChangePasswordRules.mayIssueCredential(new UserInfoBuilder("bob").build()));
        assertFalse(MustChangePasswordRules.mayIssueCredential(
                new UserInfoBuilder("bob").mustChangePassword(true).build()));
    }
}
