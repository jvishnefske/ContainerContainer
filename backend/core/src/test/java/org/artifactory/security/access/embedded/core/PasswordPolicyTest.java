/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import org.artifactory.security.access.embedded.core.PasswordPolicy.Violation;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IA6: length, username and common-password rules. */
class PasswordPolicyTest {

    private static final PasswordPolicy POLICY = new PasswordPolicy(Set.of("CorrectHorseBattery", "password1234"));

    @Test
    void strongPasswordPasses() {
        assertTrue(POLICY.check("admin", "tumble-Orchid-47-quay").acceptable());
        assertInstanceOf(EaOutcome.Ok.class, POLICY.validate("admin", "tumble-Orchid-47-quay"));
    }

    @Test
    void tooShort() {
        assertEquals(Set.of(Violation.TOO_SHORT), POLICY.check("admin", "Short-pw-11").violations());
        assertTrue(POLICY.check("admin", "Exactly-12ch").acceptable());
        assertEquals(Set.of(Violation.TOO_SHORT), POLICY.check("admin", null).violations());
        assertEquals(Set.of(Violation.TOO_SHORT), POLICY.check("admin", "").violations());
    }

    @Test
    void lengthCountsCodePoints() {
        String elevenEmoji = "😀".repeat(11);
        assertEquals(Set.of(Violation.TOO_SHORT), POLICY.check("admin", elevenEmoji).violations());
        assertTrue(POLICY.check("admin", "😀".repeat(12)).acceptable());
    }

    @Test
    void equalToOrContainingUsernameIgnoringCase() {
        assertEquals(Set.of(Violation.CONTAINS_USERNAME),
                POLICY.check("longusername", "LongUserName").violations());
        assertEquals(Set.of(Violation.CONTAINS_USERNAME),
                POLICY.check("Admin", "my-ADMIN-secret-77").violations());
        assertTrue(POLICY.check(null, "my-admin-secret-77").acceptable());
        assertTrue(POLICY.check(" ", "my-admin-secret-77").acceptable());
    }

    @Test
    void commonPasswordIgnoringCase() {
        assertEquals(Set.of(Violation.COMMON_PASSWORD), POLICY.check("bob", "correcthorsebattery").violations());
        assertEquals(Set.of(Violation.COMMON_PASSWORD), POLICY.check("bob", "PASSWORD1234").violations());
    }

    @Test
    void reportsEveryViolation() {
        PasswordPolicy policy = new PasswordPolicy(Set.of("admin"));
        assertEquals(EnumSet.allOf(Violation.class), policy.check("admin", "Admin").violations());
        EaOutcome<EaOutcome.Done> refused = policy.validate("admin", "Admin");
        String reason = ((EaOutcome.Refused<EaOutcome.Done>) refused).reason();
        assertTrue(reason.contains("12 characters") && reason.contains("username") && reason.contains("common"));
    }

    @Test
    void parsesListSkippingBlanksAndComments() {
        assertEquals(Set.of("a", "b"), PasswordPolicy.parseList(Stream.of("# comment", "", "  a  ", "b", "a")));
    }

    @Test
    void bundledListLoadsAndRejectsObviousPasswords() {
        PasswordPolicy bundled = PasswordPolicy.withBundledList().toOptional().orElseThrow();
        assertTrue(bundled.commonPasswordCount() >= 100, "bundled list has " + bundled.commonPasswordCount());
        for (String obvious : new String[]{"password1234", "administrator", "123456789012", "Qwertyuiop123",
                "correcthorsebatterystaple", "changethispassword"}) {
            assertTrue(bundled.check("someone", obvious).violations().contains(Violation.COMMON_PASSWORD), obvious);
        }
        assertTrue(bundled.check("someone", "tumble-Orchid-47-quay").acceptable());
    }
}
