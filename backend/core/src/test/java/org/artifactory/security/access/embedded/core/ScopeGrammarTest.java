/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.core;

import org.artifactory.security.access.embedded.core.ScopeGrammar.Admin;
import org.artifactory.security.access.embedded.core.ScopeGrammar.Api;
import org.artifactory.security.access.embedded.core.ScopeGrammar.AppliedGroups;
import org.artifactory.security.access.embedded.core.ScopeGrammar.AppliedUser;
import org.artifactory.security.access.embedded.core.ScopeGrammar.Extension;
import org.artifactory.security.access.embedded.core.ScopeGrammar.Internal;
import org.artifactory.security.access.embedded.core.ScopeGrammar.MemberOfGroups;
import org.artifactory.security.access.embedded.core.ScopeGrammar.RepoPath;
import org.artifactory.security.access.embedded.core.ScopeGrammar.ResourcePermission;
import org.artifactory.security.access.embedded.core.ScopeGrammar.ScopeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scope grammar accepts what Artifactory accepts and refuses the rest. */
class ScopeGrammarTest {

    private static final ScopeGrammar GRAMMAR = ScopeGrammar.standard();

    private static ScopeToken one(String token) {
        EaOutcome<ScopeToken> parsed = GRAMMAR.parseToken(token);
        assertTrue(parsed.isOk(), () -> "expected accept: " + token + " got " + parsed);
        return parsed.toOptional().orElseThrow();
    }

    @Test
    void classifiesEachKind() {
        assertInstanceOf(Api.class, one("api:*"));
        assertInstanceOf(AppliedUser.class, one("applied-permissions/user"));
        assertInstanceOf(RepoPath.class, one("path:libs-release/org/foo.jar:read"));
        assertInstanceOf(Internal.class, one("internal:mpu:x"));
        assertInstanceOf(Internal.class, one("internal:service-trust/pairing/abc"));
        assertInstanceOf(Internal.class, one("internal:artifactory-cold/x"));
        assertInstanceOf(Internal.class, one("internal:federated-repo/r1"));
        assertInstanceOf(ResourcePermission.class, one("artifact:libs-release:r"));
    }

    @Test
    void adminForms() {
        Admin v2 = assertInstanceOf(Admin.class, one("applied-permissions/admin"));
        assertTrue(v2.service().isEmpty());
        assertTrue(v2.appliesTo("jfrt@abc"));
        Admin v1 = assertInstanceOf(Admin.class, one("jfrt@abc:admin"));
        assertEquals("jfrt@abc", v1.serviceId());
        assertTrue(v1.appliesTo("jfrt@abc"));
        assertFalse(v1.appliesTo("jfrt@other"));
        assertTrue(v1.adminOnly());
        assertFalse(one("api:*").adminOnly());
    }

    @Test
    void groupsAreParsedInOrder() {
        AppliedGroups applied = assertInstanceOf(AppliedGroups.class, one("applied-permissions/groups:b,a,c"));
        assertEquals(List.of("b", "a", "c"), List.copyOf(applied.groups()));
        MemberOfGroups member = assertInstanceOf(MemberOfGroups.class, one("member-of-groups:readers"));
        assertEquals(List.of("readers"), List.copyOf(member.groups()));
    }

    @Test
    void checksumRepoPath() {
        RepoPath path = assertInstanceOf(RepoPath.class,
                one("path.checksum:f781a2f9a1c1e4e4b3c6a5e8d9f0a1b2c3d4e5f6:read"));
        assertTrue(path.checksumPath());
        RepoPath plain = assertInstanceOf(RepoPath.class, one("path:repo/a:read"));
        assertFalse(plain.checksumPath());
        assertEquals("repo/a", plain.path());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "api", "api:read", "applied-permissions/groups:", "member-of-groups:",
            "applied-permissions/superuser", "jfxr@abc:admin", "path:repo/a:write", "internal:other", "hello",
            "applied-permissions/user extra"})
    void rejects(String token) {
        assertInstanceOf(EaOutcome.Refused.class, GRAMMAR.parseToken(token), token);
    }

    @Test
    void parsesSpaceSeparatedScopeAndFormatsBack() {
        EaOutcome<List<ScopeToken>> parsed = GRAMMAR.parse("applied-permissions/user api:*");
        List<ScopeToken> tokens = parsed.toOptional().orElseThrow();
        assertEquals(2, tokens.size());
        assertEquals("applied-permissions/user api:*", ScopeGrammar.format(tokens));
    }

    @Test
    void refusesEmptyScopeAndEmptyTokens() {
        assertInstanceOf(EaOutcome.Refused.class, GRAMMAR.parse(""));
        assertInstanceOf(EaOutcome.Refused.class, GRAMMAR.parse((String) null));
        assertInstanceOf(EaOutcome.Refused.class, GRAMMAR.parse(List.of()));
        assertInstanceOf(EaOutcome.Refused.class, GRAMMAR.parse("api:*  applied-permissions/user"));
        EaOutcome<List<ScopeToken>> bad = GRAMMAR.parse("api:* bogus");
        assertTrue(((EaOutcome.Refused<List<ScopeToken>>) bad).reason().contains("bogus"));
    }

    @Test
    void grantsAdminOnlyForThisService() {
        List<ScopeToken> v1 = GRAMMAR.parse("api:* jfrt@abc:admin").toOptional().orElseThrow();
        assertTrue(ScopeGrammar.grantsAdmin(v1, "jfrt@abc"));
        assertFalse(ScopeGrammar.grantsAdmin(v1, "jfrt@xyz"));
        List<ScopeToken> user = GRAMMAR.parse("applied-permissions/user").toOptional().orElseThrow();
        assertFalse(ScopeGrammar.grantsAdmin(user, "jfrt@abc"));
    }

    @Test
    void extraPatternsAreAcceptedOnlyWhenRegistered() {
        assertInstanceOf(EaOutcome.Refused.class, GRAMMAR.parseToken("addon:thing"));
        ScopeGrammar extended = GRAMMAR.withExtraPatterns(List.of(Pattern.compile("addon:.*")));
        assertInstanceOf(Extension.class, extended.parseToken("addon:thing").toOptional().orElseThrow());
        assertInstanceOf(EaOutcome.Refused.class, GRAMMAR.parseToken("addon:thing"), "standard is unchanged");
    }
}
