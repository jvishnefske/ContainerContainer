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

import org.artifactory.api.security.UserInfoBuilder;
import org.artifactory.security.InternalUsernamePasswordAuthenticationToken;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-P3, P6: the plugin realm provider in the provider chain and as the token gateway, with a real plugin loaded by
 * the engine and an in-memory user store.
 */
class PluginRealmAuthenticationProviderTest {

    private static final long NOW = 1_800_000_000L;
    private static final String PLUGIN = """
            String subOf(String jwt) {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(jwt.tokenize('.')[1].decodeBase64Url())
                        .get('sub').asText()
            }
            def decide(String sub) {
                switch (sub) {
                    case 'jit': return realm.authenticated('oidc-jit', groups: ['readers', 'admins', 'ghosts'],
                            jit: true, expiresAt: 4000000000L, email: 'jit@example.com')
                    case 'alice': return realm.authenticated('alice', groups: ['ci'])
                    case 'admin': return realm.authenticated('admin')
                    case 'reject': return realm.rejected('bad token')
                    default: return realm.notMine()
                }
            }
            realms {
                corp(autoCreateUsers: true) {
                    bearer { token -> calls << 'bearer'; decide(subOf(token)) }
                    authenticate { username, password ->
                        calls << 'basic:' + username
                        if (password == 'plugin-pass') {
                            return realm.authenticated(username)
                        }
                        password.count('.') == 2 ? decide(subOf(password)) : realm.notMine()
                    }
                    userExists { username -> username == 'remote-only' }
                }
            }
            """;

    @TempDir
    Path dir;

    private PluginTestRig rig;
    private InMemoryRealmDirectory directory;
    private PluginRealmAuthenticationProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        directory = new InMemoryRealmDirectory()
                .group("readers", false).group("ci", false).group("admins", true)
                .user("alice", "internal", false, "readers")
                .user("admin", "internal", true);
        provider = new PluginRealmAuthenticationProvider(new PluginRealmAuthenticator(
                () -> rig.engine.snapshot().realms(), directory,
                (p, w, e) -> rig.reports.add(new PluginTestRig.Report(p, w, e)), () -> NOW));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loadPlugin() throws Exception {
        rig.write("corp.groovy", PLUGIN).reload();
        assertEquals(1, rig.engine.snapshot().realms().size(), String.valueOf(rig.engine.snapshot().results()));
    }

    private static String token(String sub) {
        return CredentialRoutingTest.jwt("{\"alg\":\"RS256\",\"kid\":\"k\"}",
                "{\"iss\":\"https://login.example.com/\",\"sub\":\"" + sub + "\"}");
    }

    private static PropsAuthenticationToken bearer(String token) {
        return new PropsAuthenticationToken(null, OauthManager.OAUTH_KEY, token, null);
    }

    private static Set<String> groups(Authentication authentication) {
        Set<String> names = new TreeSet<>();
        for (UserGroupInfo group : ((SimpleUser) authentication.getPrincipal()).getDescriptor().getGroups()) {
            names.add(group.getGroupName());
        }
        return names;
    }

    @Test
    void bearerJwtCreatesAJustInTimeUserInThePluginRealmWithOnlyAllowedGroups() throws Exception {
        loadPlugin();
        Authentication auth = provider.claim(bearer(token("jit"))).orElseThrow();
        PluginRealmAuthentication realmAuth = assertInstanceOf(PluginRealmAuthentication.class, auth);
        assertEquals("oidc-jit", auth.getName());
        assertEquals("corp", realmAuth.realmName());
        assertEquals("corp", realmAuth.pluginName());
        assertEquals(OptionalLong.of(4000000000L), realmAuth.expiresAt());
        assertEquals(Set.of("readers"), groups(auth));
        assertFalse(realmAuth.getPrincipal().isEffectiveAdmin());
        assertEquals("", auth.getCredentials());
        UserInfo stored = directory.users.get("oidc-jit");
        assertEquals("plugin:corp", stored.getRealm());
        assertTrue(stored.isPasswordDisabled());
        assertFalse(stored.isAdmin());
        assertEquals("jit@example.com", stored.getEmail());
        assertEquals(Set.of("readers"), directory.groupsOf("oidc-jit"));
        assertEquals(List.of("bearer"), rig.calls);
    }

    @Test
    void aRealmUserHasItsStoredGroupsReplacedOnTheNextLogin() throws Exception {
        loadPlugin();
        directory.user("oidc-jit", "plugin:corp", false, "ci");
        Authentication auth = provider.claim(bearer(token("jit"))).orElseThrow();
        assertEquals(Set.of("readers"), groups(auth));
        assertEquals(Set.of("readers"), directory.groupsOf("oidc-jit"));
        assertEquals(List.of("groups oidc-jit [readers]"), directory.writes);
    }

    @Test
    void anExistingUserGetsTheRealmGroupsForTheRequestOnly() throws Exception {
        loadPlugin();
        Authentication auth = provider.claim(bearer(token("alice"))).orElseThrow();
        assertEquals("alice", auth.getName());
        assertEquals(Set.of("ci", "readers"), groups(auth));
        assertEquals(Set.of("readers"), directory.groupsOf("alice"));
        assertTrue(directory.writes.isEmpty());
    }

    @Test
    void neverAdminAndRejectionsAreFinalWithAGenericMessage() throws Exception {
        loadPlugin();
        AccountStatusException admin = assertThrows(PluginRealmRejectedException.class,
                () -> provider.claim(bearer(token("admin"))));
        assertEquals(PluginRealmRejectedException.MESSAGE, admin.getMessage());
        String rejectedToken = token("reject");
        PluginRealmRejectedException rejected = assertThrows(PluginRealmRejectedException.class,
                () -> provider.claim(bearer(rejectedToken)));
        assertFalse(rejected.getMessage().contains(rejectedToken));
        assertTrue(directory.writes.isEmpty());
    }

    @Test
    void accessIssuedTokensAreNeverShownToPlugins() throws Exception {
        loadPlugin();
        assertEquals(Optional.empty(), provider.claim(bearer(CredentialRoutingTest.accessJwt())));
        assertEquals(Optional.empty(), provider.claim(
                new UsernamePasswordAuthenticationToken("admin", CredentialRoutingTest.accessJwt())));
        assertEquals(Optional.empty(), provider.claim(bearer("cmVmdGtuOjAxOjE3MDAwMDAwMDA6opaque")));
        assertTrue(rig.calls.isEmpty(), String.valueOf(rig.calls));
    }

    @Test
    void foreignJwtsInOtherHeadersAreNotOffered() throws Exception {
        loadPlugin();
        assertEquals(Optional.empty(), provider.claim(
                new PropsAuthenticationToken(null, ApiKeyManager.API_KEY, token("jit"), null)));
        assertEquals(Optional.empty(), provider.claim(
                new InternalUsernamePasswordAuthenticationToken("x", token("jit"))));
        assertTrue(rig.calls.isEmpty(), String.valueOf(rig.calls));
    }

    @Test
    void basicWithAJwtPasswordReachesAuthenticateWithAnyUsername() throws Exception {
        loadPlugin();
        Authentication auth = provider.claim(new UsernamePasswordAuthenticationToken("x", token("jit")))
                .orElseThrow();
        assertEquals("oidc-jit", auth.getName());
        assertEquals(List.of("basic:x"), rig.calls);
    }

    @Test
    void unclaimedTokensReturnEmptySoTheStockPathRuns() throws Exception {
        loadPlugin();
        assertEquals(Optional.empty(), provider.claim(bearer(token("someone-else"))));
        assertEquals(List.of("bearer"), rig.calls);
    }

    @Test
    void inTheChainOrdinaryPasswordsReachAuthenticateAndUnclaimedOnesFallThrough() throws Exception {
        loadPlugin();
        Authentication auth = provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "plugin-pass"));
        assertEquals("alice", auth.getName());
        assertNull(provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "secret")));
        assertTrue(provider.supports(UsernamePasswordAuthenticationToken.class));
        assertFalse(provider.supports(InternalUsernamePasswordAuthenticationToken.class));
        assertFalse(provider.supports(PropsAuthenticationToken.class));
        assertEquals("plugin", provider.getRealm());
        assertTrue(provider.userExists("remote-only"));
        assertFalse(provider.userExists("alice"));
    }

    @Test
    void withNoRealmLoadedNothingIsClaimedAndNoUserDataIsRead() {
        rig.reload();
        assertEquals(Optional.empty(), provider.claim(bearer(token("jit"))));
        assertEquals(Optional.empty(), provider.claim(new UsernamePasswordAuthenticationToken("x", token("jit"))));
        assertNull(provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "plugin-pass")));
        Authentication some = new UsernamePasswordAuthenticationToken("alice", "x");
        assertSame(some, provider.applyAdditive(some));
        assertFalse(provider.userExists("remote-only"));
        assertEquals(0, directory.lookups);
        assertTrue(directory.writes.isEmpty());
    }

    @Test
    void additiveRealmsAddAllowedGroupsToPasswordLoginsOnly() throws Exception {
        rig.write("extra.groovy", """
                realms {
                    extra(realmPolicy: 'additive') {
                        authenticate { username, credentials ->
                            calls << [username, credentials]
                            realm.authenticated(username, groups: ['ci', 'admins'])
                        }
                    }
                    other(realmPolicy: 'additive') {
                        authenticate { username, credentials -> realm.authenticated('admin', groups: ['readers']) }
                    }
                }
                """).reload();
        SimpleUser alice = new SimpleUser(directory.users.get("alice"));
        Authentication login = new UsernamePasswordAuthenticationToken(alice, "", alice.getAuthorities());
        Authentication added = provider.applyAdditive(login);
        assertEquals(Set.of("ci", "readers"), groups(added));
        assertEquals(List.of(java.util.Arrays.asList("alice", null)), rig.calls);

        Authentication token = new TokenLike(alice);
        assertSame(token, provider.applyAdditive(token));
        SimpleUser admin = new SimpleUser(directory.users.get("admin"));
        Authentication adminLogin = new UsernamePasswordAuthenticationToken(admin, "", admin.getAuthorities());
        assertSame(adminLogin, provider.applyAdditive(adminLogin));
        assertNull(provider.authenticate(new UsernamePasswordAuthenticationToken("alice", "plugin-pass")),
                "additive realms never authenticate");

        GroovyPluginsAddon addon = new GroovyPluginsAddon(rig.engine, () -> rig.caller, Runnable::run,
                () -> provider);
        SecurityContextHolder.getContext().setAuthentication(login);
        addon.executeAdditiveRealmPlugins(null);
        assertEquals(Set.of("ci", "readers"), groups(SecurityContextHolder.getContext().getAuthentication()));
    }

    @Test
    void additiveAppliesOnlyToScopeFreeAuthenticationTypes() {
        SimpleUser alice = new SimpleUser(new UserInfoBuilder("alice").build());
        assertTrue(PluginRealmAuthenticator.additiveApplies(
                new UsernamePasswordAuthenticationToken(alice, "", alice.getAuthorities())));
        assertTrue(PluginRealmAuthenticator.additiveApplies(
                new PluginRealmAuthentication(alice, "p", "r", OptionalLong.empty(), null)));
        assertFalse(PluginRealmAuthenticator.additiveApplies(new TokenLike(alice)));
        assertFalse(PluginRealmAuthenticator.additiveApplies(new UsernamePasswordAuthenticationToken("alice", "x")));
        assertFalse(PluginRealmAuthenticator.additiveApplies(null));
        SimpleUser anonymous = new SimpleUser(new UserInfoBuilder(UserInfo.ANONYMOUS).build());
        assertFalse(PluginRealmAuthenticator.additiveApplies(
                new UsernamePasswordAuthenticationToken(anonymous, "", anonymous.getAuthorities())));
    }

    /** Stands for a token authentication (such as an Access token) whose scope must not be rebuilt. */
    private static final class TokenLike extends AbstractAuthenticationToken {
        private final SimpleUser user;

        TokenLike(SimpleUser user) {
            super(user.getAuthorities());
            this.user = user;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return "";
        }

        @Override
        public Object getPrincipal() {
            return user;
        }
    }
}
