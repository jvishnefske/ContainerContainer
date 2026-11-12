/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.security.SimpleUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P4 and P5: the realm and execution parts of the {@code security} binding, and {@code users: ['*']}. */
class PluginSecurityRealmBindingTest {

    private static final long NOW = 1_800_000_000L;

    @TempDir
    Path dir;

    private final SecurityService securityService = mock(SecurityService.class);
    private final List<String> minted = new ArrayList<>();
    private Authentication current;
    private PluginTestRig rig;

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        when(securityService.currentUsername()).thenAnswer(invocation -> rig.caller.username());
        when(securityService.isAnonymous()).thenAnswer(invocation -> rig.caller.anonymous());
        InMemoryRealmDirectory directory = new InMemoryRealmDirectory().group("ci", false)
                .user("ci-bot", "internal", false, "ci");
        PluginSecurity security = new PluginSecurity(securityService, directory::findUser,
                (username, seconds) -> {
                    minted.add(username + ":" + seconds);
                    if ("refused".equals(username)) {
                        throw new IllegalStateException("Access said no");
                    }
                    return new PluginSecurity.Minted("tok-" + username, seconds);
                },
                () -> current, () -> NOW);
        rig.extraBindings.put("security", security);
        rig.write("x.groovy", """
                download { beforeDownloadRequest { request, repoPath ->
                    calls << security.createAccessToken(60).reason
                    calls << security.findUser('ci-bot').isPresent()
                } }
                realms { r { bearer { t -> calls << security.findUser('ci-bot').get().groups
                        calls << security.createAccessToken(60).issued; realm.notMine() } } }
                executions {
                    mint(users: ['*']) { params ->
                        def t = security.createAccessToken(params['s'] as long)
                        def ra = security.realmAuthentication
                        message = t.issued ? "${t.accessToken} ${t.tokenType} ${t.expiresIn} ${t.scope} " +
                                "${ra.map { it.realm }.orElse('none')}" : "refused: ${t.reason}"
                    }
                    who(users: ['*']) { params -> message = security.findUser(params['u']).map { it.groups }
                            .orElse(null) }
                }
                """);
        rig.reload();
        assertTrue(rig.reports.isEmpty(), String.valueOf(rig.reports));
    }

    private ResponseCtx run(String name, Map<String, ?> params) {
        return rig.addon.execute(name, "POST", params, null, false);
    }

    @Test
    void anyAuthenticatedCallerMayRunAStarExecutionButAnonymousMayNot() {
        rig.caller = new Caller("bob", false, Set.of());
        assertEquals(200, run("mint", Map.of("s", 60)).getStatus());
        rig.caller = new Caller("anonymous", false, Set.of());
        assertEquals(403, run("mint", Map.of("s", 60)).getStatus());
        assertEquals(List.of("bob:60"), minted);
    }

    @Test
    void executionsMintTokensForTheCallerWithTheUserScopeAndACappedLifetime() {
        rig.caller = new Caller("bob", false, Set.of());
        assertEquals("tok-bob Bearer 3600 applied-permissions/user none",
                run("mint", Map.of("s", 999_999)).getMessage());
        current = new PluginRealmAuthentication(new SimpleUser(new UserInfoBuilder("bob").build()), "x", "r",
                OptionalLong.of(NOW + 120), null);
        assertEquals("tok-bob Bearer 119 applied-permissions/user r", run("mint", Map.of("s", 600)).getMessage());
        assertEquals(List.of("bob:3600", "bob:119"), minted);
    }

    @Test
    void mintingIsRefusedForAnonymousForExpiredRealmCredentialsAndWhenAccessFails() {
        rig.caller = new Caller("anonymous", true, Set.of());
        assertEquals("refused: the caller is not authenticated", run("mint", Map.of("s", 60)).getMessage());
        rig.caller = new Caller("bob", false, Set.of());
        current = new PluginRealmAuthentication(new SimpleUser(new UserInfoBuilder("bob").build()), "x", "r",
                OptionalLong.of(NOW), null);
        assertEquals("refused: no lifetime is left for the token", run("mint", Map.of("s", 60)).getMessage());
        current = new UsernamePasswordAuthenticationToken("bob", "");
        assertEquals("refused: no lifetime is left for the token", run("mint", Map.of("s", 0)).getMessage());
        rig.caller = new Caller("refused", false, Set.of());
        assertEquals("refused: Access refused to create the token", run("mint", Map.of("s", 60)).getMessage());
        assertEquals(List.of("refused:60"), minted);
    }

    @Test
    void findUserGivesAnImmutableViewInExecutionsAndRealms() {
        rig.caller = new Caller("bob", false, Set.of());
        assertEquals("[ci]", run("who", Map.of("u", "ci-bot")).getMessage());
        assertEquals(null, run("who", Map.of("u", "ghost")).getMessage());
        PluginRealm realm = rig.engine.snapshot().realms().get(0);
        RealmInvoker.result(realm, realm.bearer().orElseThrow(), "t", (p, w, e) -> { }, "tok");
        assertEquals(List.of(Set.of("ci"), false), rig.calls);
    }

    @Test
    void otherPluginCodeCannotMintOrLookUpUsers() {
        rig.caller = new Caller("bob", false, Set.of());
        rig.engine.dispatch(org.artifactory.addon.plugin.download.BeforeDownloadRequestAction.class,
                new org.artifactory.addon.plugin.download.DownloadCtx(), null, null);
        assertEquals(List.of("createAccessToken is available to plugin executions only", false), rig.calls);
        assertTrue(minted.isEmpty());
        assertFalse(new PluginSecurity(securityService).findUser("ci-bot").isPresent());
    }

    @Test
    void lifetimeRule() {
        assertEquals(OptionalLong.of(3600), PluginSecurity.tokenLifetime(10_000, NOW, Long.MAX_VALUE,
                OptionalLong.empty()));
        assertEquals(OptionalLong.of(29), PluginSecurity.tokenLifetime(60, NOW, NOW + 30, OptionalLong.empty()));
        assertEquals(OptionalLong.of(9), PluginSecurity.tokenLifetime(60, NOW, NOW + 30, OptionalLong.of(NOW + 10)));
        // the one-second margin: a credential expiring next second leaves nothing to mint
        assertEquals(OptionalLong.empty(), PluginSecurity.tokenLifetime(60, NOW, Long.MAX_VALUE,
                OptionalLong.of(NOW + 1)));
        assertEquals(OptionalLong.empty(), PluginSecurity.tokenLifetime(60, NOW, Long.MAX_VALUE,
                OptionalLong.of(NOW - 1)));
        assertEquals(OptionalLong.empty(), PluginSecurity.tokenLifetime(-5, NOW, Long.MAX_VALUE,
                OptionalLong.empty()));
        assertEquals(Optional.empty(), PluginCallScope.current());
    }
}
