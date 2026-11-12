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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.ResponseCtx;
import org.artifactory.addon.plugin.wasm.AbiCodec;
import org.artifactory.addon.plugin.wasm.EchoRealmFixture;
import org.artifactory.addon.plugin.wasm.Effect;
import org.artifactory.addon.plugin.wasm.EffectResult;
import org.artifactory.addon.plugin.wasm.HostEffects;
import org.artifactory.addon.plugin.wasm.WasmPlugin;
import org.artifactory.addon.plugin.wasm.WasmPluginLoader;
import org.artifactory.addon.plugin.wasm.WasmWorkers;
import org.artifactory.api.security.SecurityService;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.PropsAuthenticationToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W1-W5 with the real example module (the committed build of plugins/wasm-examples/echo-realm) loaded by the engine
 * next to Groovy plugins: WASM realm results go through the same P1-P6 enforcement as Groovy ones, WASM executions
 * through the same G4 authorization, method rule and P4 token minting.
 */
class WasmPluginEnforcementTest {

    private static final long NOW = 1_800_000_000L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path dir;

    private final SecurityService securityService = mock(SecurityService.class);
    private final List<String> minted = Collections.synchronizedList(new ArrayList<>());
    private final List<String> fetched = Collections.synchronizedList(new ArrayList<>());
    private volatile String fetchAnswer = "allow";
    private volatile Optional<AbiCodec.CallerRealm> callerRealm = Optional.empty();
    private Authentication current;
    private PluginTestRig rig;
    private InMemoryRealmDirectory directory;
    private PluginRealmAuthenticationProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
        directory = new InMemoryRealmDirectory()
                .group("readers", false).group("admins", true)
                .user("alice", "internal", false, "readers")
                .user("admin", "internal", true);
        WasmPlugin.RequestContext context = new WasmPlugin.RequestContext() {
            @Override
            public Map<String, List<String>> headers() {
                return Map.of("Authorization", List.of("Basic c2VjcmV0"), "Accept", List.of("*/*"));
            }

            @Override
            public Optional<AbiCodec.CallerRealm> callerRealm() {
                return callerRealm;
            }
        };
        WasmPluginLoader loader = new WasmPluginLoader(new WasmWorkers(2, 8), (pluginName, limits) -> {
            PluginSecurity security = new PluginSecurity(securityService, directory::findUser,
                    (username, seconds) -> {
                        minted.add(username + ":" + seconds);
                        return new PluginSecurity.Minted("tok-" + username, seconds);
                    }, () -> current, () -> NOW);
            HostEffects host = new HostEffects(HostEffects.client(1000), 1000, 1024, security);
            return effect -> {
                if (effect instanceof Effect.AllowedGet get) {
                    fetched.add(get.uri().toString());
                    return new EffectResult.HttpGet(true, 200, Map.of(),
                            fetchAnswer.getBytes(StandardCharsets.UTF_8), "");
                }
                return host.perform(effect);
            };
        }, context, () -> NOW);
        rig = new PluginTestRig(dir, loader);
        when(securityService.currentUsername()).thenAnswer(invocation -> rig.caller.username());
        when(securityService.isAnonymous()).thenAnswer(invocation -> rig.caller.anonymous());
        provider = new PluginRealmAuthenticationProvider(new PluginRealmAuthenticator(
                () -> rig.engine.snapshot().realms(), directory,
                (p, w, e) -> rig.reports.add(new PluginTestRig.Report(p, w, e)), () -> NOW));
        install("echo-realm", EchoRealmFixture.bytes(), EchoRealmFixture.manifest("limits: {stepTimeoutMs: 3000}"));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void install(String name, byte[] module, String manifest) throws Exception {
        Files.write(dir.resolve(name + ".wasm"), module);
        Files.writeString(dir.resolve(name + ".wasm.yaml"), manifest);
        PluginSnapshot snapshot = rig.reload();
        assertTrue(snapshot.results().stream().allMatch(r -> r instanceof PluginLoadResult.Loaded),
                String.valueOf(snapshot.results()));
    }

    private Optional<Authentication> bearer(String sub) {
        return provider.claim(new PropsAuthenticationToken(null, OauthManager.OAUTH_KEY,
                EchoRealmFixture.token(EchoRealmFixture.ISSUER, sub), null));
    }

    private static Set<String> groups(Authentication authentication) {
        Set<String> names = new TreeSet<>();
        for (UserGroupInfo group : ((SimpleUser) authentication.getPrincipal()).getDescriptor().getGroups()) {
            names.add(group.getGroupName());
        }
        return names;
    }

    private ResponseCtx run(String execution, String method) {
        return rig.addon.execute(execution, method, Map.of(), null, false);
    }

    private static JsonNode body(ResponseCtx ctx) throws Exception {
        return JSON.readTree(assertInstanceOf(HostedResponseCtx.class, ctx).getInputStream().readAllBytes());
    }

    @Test
    void theCommittedModuleMatchesItsRecordedHash() {
        assertEquals(EchoRealmFixture.recordedSha256(), EchoRealmFixture.sha256(EchoRealmFixture.bytes()));
    }

    @Test
    void wasmPluginsAreListedAndReportedLikeGroovyOnes() throws Exception {
        rig.write("groovy.groovy", "executions { hello { params -> message = 'hi' } }").reload();
        Map<String, String> statuses = rig.addon.getPluginsStatus();
        assertEquals(PluginsAddon.PLUGIN_STATUS_LOADED, statuses.get("echo-realm.wasm"));
        assertEquals(PluginsAddon.PLUGIN_STATUS_LOADED, statuses.get("groovy.groovy"));
        Map<String, String> echo = rig.addon.getPluginsInfoSupportBundleDumpAsMap().get("echo-realm");
        assertEquals("echo", echo.get("realms"));
        assertEquals("echoMint", echo.get("executions"));
        assertEquals(Set.of("echoMint", "hello"), Set.copyOf(rig.addon.getPluginInfo(null)
                .get(PluginReports.EXECUTIONS).stream().map(info -> info.getName()).toList()));
    }

    @Test
    void pluginAndRealmNamesStayUniqueAcrossGroovyAndWasm() throws Exception {
        rig.write("echo-realm.groovy", "executions { other { params -> } }")
                .write("later.groovy", "realms { echo { bearer { t -> realm.notMine() } } }");
        PluginSnapshot snapshot = rig.reload();
        Map<String, String> errors = new java.util.HashMap<>();
        snapshot.results().forEach(r -> errors.put(r.fileName(),
                r instanceof PluginLoadResult.Failed f ? f.error() : "loaded"));
        assertEquals("loaded", errors.get("echo-realm.groovy"));
        assertTrue(errors.get("echo-realm.wasm").contains("already taken by echo-realm.groovy"), errors.toString());
        assertEquals("loaded", errors.get("later.groovy"), "the WASM realm 'echo' is gone, so the name is free");
        Files.delete(dir.resolve("echo-realm.groovy"));
        snapshot = rig.reload();
        assertTrue(snapshot.results().stream().anyMatch(r -> r.fileName().equals("later.groovy")
                && r instanceof PluginLoadResult.Failed f && f.error().contains("Realm 'echo' is already declared")),
                String.valueOf(snapshot.results()));
    }

    @Test
    void aBearerTokenCreatesAJustInTimeUserInThePluginRealmWithOnlyExistingNonAdminGroups() {
        PluginRealmAuthentication auth = assertInstanceOf(PluginRealmAuthentication.class,
                bearer("ok:carol").orElseThrow());
        assertEquals("carol", auth.getName());
        assertEquals("echo-realm", auth.pluginName());
        assertEquals("echo", auth.realmName());
        assertEquals(Set.of("readers"), groups(auth));
        UserInfo stored = directory.findUser("carol").orElseThrow();
        assertEquals("plugin:echo", stored.getRealm());
        assertFalse(stored.isAdmin());
        assertFalse(auth.getPrincipal().isEffectiveAdmin());
    }

    @Test
    void aWasmRealmCanNeverGrantAdminOrTakeOverAnotherRealmsUser() {
        assertThrows(PluginRealmRejectedException.class, () -> bearer("ok:admin"));
        assertThrows(PluginRealmRejectedException.class, () -> bearer("ok:alice"));
        assertThrows(PluginRealmRejectedException.class, () -> bearer("reject:x"));
        assertFalse(directory.findUser("admin").orElseThrow().getRealm().startsWith("plugin:"));
    }

    @Test
    void tokensOfOtherIssuersAreNotMineAndBasicPasswordsReachAuthenticate() {
        assertEquals(Optional.empty(), provider.claim(new PropsAuthenticationToken(null, OauthManager.OAUTH_KEY,
                EchoRealmFixture.token("someone-else", "ok:carol"), null)));
        Authentication basic = provider.authenticate(new UsernamePasswordAuthenticationToken("erin", "echo:erin"));
        assertEquals("erin", assertInstanceOf(PluginRealmAuthentication.class, basic).getName());
        assertNull(provider.authenticate(new UsernamePasswordAuthenticationToken("erin", "wrong")));
    }

    @Test
    void anHttpGetEffectRoundTripsThroughTheHost() {
        assertEquals("dave", bearer("fetch:dave").orElseThrow().getName());
        assertEquals(List.of(EchoRealmFixture.FETCH_URL), fetched);
        fetchAnswer = "deny";
        assertThrows(PluginRealmRejectedException.class, () -> bearer("fetch:dave"));
    }

    @Test
    void trapsAndLoopsFailClosedAsNotMineAndAreReported() throws Exception {
        install("echo-realm", EchoRealmFixture.bytes(), EchoRealmFixture.manifest("limits: {stepTimeoutMs: 300}"));
        assertEquals(Optional.empty(), bearer("trap:x"));
        assertEquals(Optional.empty(), bearer("loop:x"));
        assertEquals(2, rig.reports.size(), String.valueOf(rig.reports));
        assertTrue(rig.reports.get(0).error().getMessage().contains("trap"), rig.reports.get(0).error().getMessage());
        assertTrue(rig.reports.get(1).error().getMessage().contains("timeout"),
                rig.reports.get(1).error().getMessage());
        assertEquals("echo-realm", rig.reports.get(1).pluginName());
        assertEquals("realms.echo.bearer", rig.reports.get(1).what());
        assertTrue(bearer("ok:carol").isPresent(), "the plugin keeps working after a killed step");
    }

    @Test
    void removingThePluginRevokesItsAuthenticationsAndRefusesItsTokens() throws Exception {
        PluginRealmAuthentication auth = (PluginRealmAuthentication) bearer("ok:carol").orElseThrow();
        assertFalse(auth.revoked());
        Files.delete(dir.resolve("echo-realm.wasm"));
        Files.delete(dir.resolve("echo-realm.wasm.yaml"));
        rig.reload();
        assertTrue(auth.revoked());
        assertEquals(Optional.empty(), bearer("ok:carol"));
    }

    @Test
    void executionsAreAuthorizedLikeGroovyOnesAndMintForTheCaller() throws Exception {
        rig.caller = new Caller("bob", false, Set.of());
        ResponseCtx ok = run("echoMint", "POST");
        assertEquals(200, ok.getStatus());
        assertEquals(List.of("application/json"),
                ((HostedResponseCtx) ok).responseHeaders().get("content-type"));
        JsonNode body = body(ok);
        assertEquals("tok-bob", body.get("access_token").asText());
        assertEquals(300, body.get("expires_in").asLong());
        assertEquals("bob", body.get("caller").asText());
        assertEquals(List.of("bob:300"), minted);
        assertEquals(405, run("echoMint", "GET").getStatus());
        assertEquals(404, run("nothing", "POST").getStatus());
        rig.caller = new Caller("anonymous", false, Set.of());
        assertEquals(403, run("echoMint", "POST").getStatus());
        assertEquals(List.of("bob:300"), minted);
    }

    @Test
    void mintedTokensNeverOutliveTheRealmCredentialOrNotAfter() throws Exception {
        rig.caller = new Caller("carol", false, Set.of());
        current = new PluginRealmAuthentication(new SimpleUser(new org.artifactory.api.security.UserInfoBuilder(
                "carol").build()), "echo-realm", "echo", OptionalLong.of(NOW + 120), null);
        callerRealm = Optional.of(new AbiCodec.CallerRealm("echo-realm", "echo", OptionalLong.of(NOW + 60)));
        JsonNode body = body(run("echoMint", "POST"));
        // not_after (from the realm the module sees) caps at 60 - 1 s margin, below the realm expiry cap of 119.
        assertEquals(59, body.get("expires_in").asLong());
        assertEquals(List.of("carol:59"), minted);
        callerRealm = Optional.of(new AbiCodec.CallerRealm("echo-realm", "echo", OptionalLong.of(NOW)));
        ResponseCtx refused = run("echoMint", "POST");
        assertEquals(403, refused.getStatus());
        assertTrue(body(refused).get("error").asText().contains("no lifetime"));
    }

    @Test
    void stateSurvivesAcrossCallsAndCountsOnlyCompletedOnes() throws Exception {
        rig.caller = new Caller("bob", false, Set.of());
        long first = body(run("echoMint", "POST")).get("calls").asLong();
        bearer("ok:carol");
        install("echo-realm", EchoRealmFixture.bytes(), EchoRealmFixture.manifest("limits: {stepTimeoutMs: 300}"));
        long afterReload = body(run("echoMint", "POST")).get("calls").asLong();
        assertEquals(1, first);
        assertEquals(1, afterReload, "a reload starts the plugin with no state");
        bearer("trap:x");
        bearer("ok:carol");
        assertEquals(3, body(run("echoMint", "POST")).get("calls").asLong(), "the trapped call stored nothing");
    }

    @Test
    void aFailingExecutionAnswers500AndIsReported() throws Exception {
        Files.write(dir.resolve("broken.wasm"), org.artifactory.addon.plugin.wasm.EchoRealmFixture.bytes());
        Files.writeString(dir.resolve("broken.wasm.yaml"), "abi: 1\nexecutions: [{name: boom, users: ['*']}]\n"
                + "config: {mint_seconds: 'not a number'}\n");
        rig.reload();
        rig.caller = new Caller("bob", false, Set.of());
        ResponseCtx failed = run("boom", "POST");
        // The module answers a malformed envelope with 400; a module that traps or replies off-schema gets 500.
        assertTrue(failed.getStatus() == 400 || failed.getStatus() == 500, String.valueOf(failed.getStatus()));
        Files.write(dir.resolve("broken.wasm"), trapModule());
        rig.reload();
        ResponseCtx trapped = run("boom", "POST");
        assertEquals(500, trapped.getStatus());
        assertFalse(trapped instanceof HostedResponseCtx);
        assertTrue(rig.reports.stream().anyMatch(r -> r.what().equals("executions.boom")));
    }

    private static byte[] trapModule() {
        // (module (memory (export "memory") 1) (func (export "_start") unreachable))
        return new byte[] {0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,
                0x01, 0x04, 0x01, 0x60, 0x00, 0x00,
                0x03, 0x02, 0x01, 0x00,
                0x05, 0x03, 0x01, 0x00, 0x01,
                0x07, 0x13, 0x02, 0x06, 0x5f, 0x73, 0x74, 0x61, 0x72, 0x74, 0x00, 0x00,
                0x06, 0x6d, 0x65, 0x6d, 0x6f, 0x72, 0x79, 0x02, 0x00,
                0x0a, 0x05, 0x01, 0x03, 0x00, 0x00, 0x0b};
    }
}
