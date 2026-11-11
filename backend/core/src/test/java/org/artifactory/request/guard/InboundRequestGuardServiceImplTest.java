package org.artifactory.request.guard;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.aol.AolService;
import org.artifactory.common.ConstantValues;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.INBOUND_REQUEST_GUARD_TRIGGERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
class InboundRequestGuardServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ConfigsService configsService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    HaAddon haAddon;
    @Mock
    AolService aolService;

    InboundRequestGuardServiceImpl service;
    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        service = new InboundRequestGuardServiceImpl();
        service.setConfigsService(configsService);
        service.setAddonsManager(addonsManager);
        service.setAolService(aolService);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        // Default to self-hosted; SaaS-specific tests opt in via when(aolService.isAol()).thenReturn(true).
        lenient().when(aolService.isAol()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    private static InboundRequestGuardTriggersConfig triggers(InboundRequestGuardTriggersConfig.Mode mode,
            Set<String> headerNames, Set<String> userAgentSubs) {
        InboundRequestGuardTriggersConfig t = new InboundRequestGuardTriggersConfig();
        t.setMode(mode);
        t.setHeaderNames(headerNames);
        t.setUserAgentSubstrings(userAgentSubs);
        return t;
    }

    private static HttpServletRequest requestWith(String userAgent, List<String> headerNames) {
        return requestWith(userAgent, headerNames, "127.0.0.1", null);
    }

    private static HttpServletRequest requestWith(String userAgent, List<String> headerNames,
            String remoteAddr, String xForwardedFor) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        Enumeration<String> names = Collections.enumeration(headerNames);
        lenient().when(req.getHeaderNames()).thenReturn(names);
        lenient().when(req.getHeader("User-Agent")).thenReturn(userAgent);
        lenient().when(req.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        lenient().when(req.getRemoteAddr()).thenReturn(remoteAddr);
        lenient().when(req.getRequestURI()).thenReturn("/test/uri");
        return req;
    }

    private static InboundRequestGuardTriggersConfig triggersWithIps(InboundRequestGuardTriggersConfig.Mode mode,
            Set<String> ipAddresses) {
        InboundRequestGuardTriggersConfig t = new InboundRequestGuardTriggersConfig();
        t.setMode(mode);
        t.setIpAddresses(ipAddresses);
        return t;
    }

    @Test
    void shouldBlockReturnsFalseInOffMode() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_OFF,
                Set.of("X-Bad"), Set.of("badtool")));
        HttpServletRequest req = requestWith("badtool/1.0", List.of("X-Bad"));

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsFalseInProbeModeAndLogs() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE,
                Set.of("X-Bad"), Set.of()));
        HttpServletRequest req = requestWith("Mozilla", List.of("X-Bad"));

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsTrueOnHeaderMatchInActive() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Bad-Tool"), Set.of()));
        HttpServletRequest req = requestWith("Mozilla", List.of("Content-Type", "x-bad-tool"));

        assertTrue(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsTrueOnUserAgentSubstringInActive() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of(), Set.of("BadTool")));
        HttpServletRequest req = requestWith("Mozilla/5.0 badtool/2.1", List.of("Content-Type"));

        assertTrue(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsFalseWhenNeitherTriggerMatches() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Bad"), Set.of("BadTool")));
        HttpServletRequest req = requestWith("Mozilla", List.of("Content-Type"));

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsFalseWhenTriggersConfigIsEmpty() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE, Set.of(), Set.of()));
        HttpServletRequest req = requestWith("Mozilla", List.of("Content-Type"));

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsFalseForNullRequest() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Bad"), Set.of("BadTool")));

        assertFalse(service.shouldBlock(null));
    }

    @Test
    void shouldBlockTreatsNullModeAsProbe() {
        service.setTriggersForTest(triggers(null, Set.of("X-Bad"), Set.of()));
        HttpServletRequest req = requestWith("Mozilla", List.of("X-Bad"));

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockSkipsBlankUserAgent() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of(), Set.of("BadTool")));
        HttpServletRequest req = requestWith("", List.of("Content-Type"));

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockHandlesNullHeaderEnumeration() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Bad"), Set.of()));
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getHeaderNames()).thenReturn(null);
        lenient().when(req.getHeader("User-Agent")).thenReturn("Mozilla");
        lenient().when(req.getRequestURI()).thenReturn("/uri");

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void updateTriggersPersistsAndUpdatesCache() {
        InboundRequestGuardTriggersConfig t = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("x-bad"), Set.of("badtool"));

        service.updateTriggers(t);

        verify(configsService).addOrUpdateConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS), eq(t), anyLong());
        assertEquals(t, inboundTriggers());
    }

    @Test
    void updateTriggersNormalizesCaseBeforePersistingAndCaching() {
        InboundRequestGuardTriggersConfig input = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Bad-Tool", "X-Other"), Set.of("BadTool", "OtherAgent"));
        ArgumentCaptor<InboundRequestGuardTriggersConfig> persisted =
                ArgumentCaptor.forClass(InboundRequestGuardTriggersConfig.class);

        service.updateTriggers(input);

        verify(configsService).addOrUpdateConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS), persisted.capture(), anyLong());
        assertEquals(Set.of("x-bad-tool", "x-other"), persisted.getValue().getHeaderNames());
        assertEquals(Set.of("badtool", "otheragent"), persisted.getValue().getUserAgentSubstrings());
        assertEquals(Set.of("x-bad-tool", "x-other"), inboundTriggers().getHeaderNames());
        assertEquals(Set.of("badtool", "otheragent"), inboundTriggers().getUserAgentSubstrings());
    }

    @Test
    void updateTriggersWithNullReplacesWithSeededDefaults() {
        service.updateTriggers(null);

        verify(configsService).addOrUpdateConfig(
                eq(INBOUND_REQUEST_GUARD_TRIGGERS), any(Object.class), anyLong());
        assertEquals(InboundRequestGuardTriggersConfig.DEFAULT_HEADER_NAMES,
                inboundTriggers().getHeaderNames());
        assertTrue(inboundTriggers().getUserAgentSubstrings().isEmpty());
    }

    @Test
    void updateTriggersPropagatesWhenHaEnabled() {
        when(haAddon.isHaEnabled()).thenReturn(true);
        InboundRequestGuardTriggersConfig t = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Bad"), Set.of());

        service.updateTriggers(t);

        verify(haAddon).propagateInboundRequestGuardTriggersChange();
    }

    @Test
    void updateTriggersDoesNotPropagateWhenHaDisabled() {
        when(haAddon.isHaEnabled()).thenReturn(false);
        InboundRequestGuardTriggersConfig t = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Bad"), Set.of());

        service.updateTriggers(t);

        verify(haAddon, never()).propagateInboundRequestGuardTriggersChange();
    }

    @Test
    void reloadTriggersFromDbReplacesInMemoryCache() {
        InboundRequestGuardTriggersConfig fromDb = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("x-new"), Set.of("newtool"));
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(fromDb);

        service.reloadTriggersFromDb();

        assertEquals(fromDb, inboundTriggers());
    }

    @Test
    void reloadTriggersFromDbNormalizesLegacyMixedCaseData() {
        InboundRequestGuardTriggersConfig legacy = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("X-Legacy"), Set.of("LegacyTool"));
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(legacy);

        service.reloadTriggersFromDb();

        assertEquals(Set.of("x-legacy"), inboundTriggers().getHeaderNames());
        assertEquals(Set.of("legacytool"), inboundTriggers().getUserAgentSubstrings());
    }

    @Test
    void reloadTriggersFromDbDefaultsToSeededHeaderWhenNothingInDb() {
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.reloadTriggersFromDb();

        assertEquals(InboundRequestGuardTriggersConfig.DEFAULT_HEADER_NAMES,
                inboundTriggers().getHeaderNames());
        assertTrue(inboundTriggers().getUserAgentSubstrings().isEmpty());
    }

    @Test
    void onContextCreatedLoadsTriggersFromDb() {
        InboundRequestGuardTriggersConfig fromDb = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("x-bad"), Set.of("badtool"));
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(fromDb);

        service.onContextCreated();

        assertEquals(fromDb, inboundTriggers());
    }

    @Test
    void shouldBlockCatchesGuardExceptionImplicitlyByCallerNotImpl() {
        // The service itself never throws on valid input; defensive behavior is documented at the caller.
        // This test asserts the service's own no-trigger fast-path doesn't call into the request.
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE, Set.of(), Set.of()));
        HttpServletRequest req = mock(HttpServletRequest.class);

        assertFalse(service.shouldBlock(req));
        verify(req, never()).getHeaderNames();
        verify(req, never()).getHeader(any());
    }

    @Test
    void shouldBlockIgnoresUnsetUserAgentWhenOnlyUserAgentTriggerConfigured() {
        service.setTriggersForTest(triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of(), Set.of("BadTool")));
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getHeader("User-Agent")).thenReturn(null);
        lenient().when(req.getHeaderNames()).thenReturn(Collections.enumeration(List.of()));
        lenient().when(req.getRequestURI()).thenReturn("/uri");

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void getInboundRequestGuardTriggersConfigReturnsDefaultsBeforeFirstLoad() {
        InboundRequestGuardTriggersConfig defaults = inboundTriggers();
        assertEquals(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE, defaults.getMode());
        assertEquals(InboundRequestGuardTriggersConfig.DEFAULT_HEADER_NAMES, defaults.getHeaderNames());
        assertTrue(defaults.getUserAgentSubstrings().isEmpty());
    }

    @Test
    void freshConfigCarriesDefaultHeaderName() {
        InboundRequestGuardTriggersConfig fresh = new InboundRequestGuardTriggersConfig();

        assertEquals(InboundRequestGuardTriggersConfig.DEFAULT_HEADER_NAMES, fresh.getHeaderNames());
        assertTrue(fresh.getHeaderNames().contains("x-socket-request-id"));
    }

    @Test
    void unusedKeyConstantIsAccessible() {
        // sanity check that the key constant is wired
        assertEquals("inbound.request.guard.triggers.config",
                ConfigsServiceKey.INBOUND_REQUEST_GUARD_TRIGGERS.getKeyName());
    }

    // ---- IP address rules ----

    @Test
    void shouldBlockOnExactIpv4MatchInActive() {
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("203.0.113.5")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "203.0.113.5", null);

        assertTrue(service.shouldBlock(req));
    }

    @Test
    void shouldBlockOnIpv4CidrMatchInActive() {
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("203.0.113.0/24")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "203.0.113.42", null);

        assertTrue(service.shouldBlock(req));
    }

    @Test
    void shouldBlockOnIpv6CidrMatchInActive() {
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("2001:db8::/32")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "2001:db8::1", null);

        assertTrue(service.shouldBlock(req));
    }

    @Test
    void shouldNotBlockWhenIpDoesNotMatch() {
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("203.0.113.0/24", "2001:db8::/32")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "198.51.100.7", null);

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsFalseInOffModeEvenWhenIpMatches() {
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_OFF,
                Set.of("203.0.113.0/24")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "203.0.113.5", null);

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockReturnsFalseInProbeModeEvenWhenIpMatches() {
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE,
                Set.of("203.0.113.0/24")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "203.0.113.5", null);

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void shouldBlockHonorsXForwardedForOverRemoteAddr() {
        // Behind a reverse proxy: getRemoteAddr() is the proxy, X-Forwarded-For carries the real client IP.
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("203.0.113.0/24")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "10.0.0.1", "203.0.113.42, 10.0.0.2");

        assertTrue(service.shouldBlock(req));
    }

    @Test
    void shouldBlockIgnoresInvalidRemoteAddress() {
        // HttpUtils throws URISyntaxException when the remote address isn't a valid IP — guard logs + allows.
        service.setTriggersForTest(triggersWithIps(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("203.0.113.0/24")));
        HttpServletRequest req = requestWith("Mozilla", List.of(), "not-an-ip", null);

        assertFalse(service.shouldBlock(req));
    }

    @Test
    void updateTriggersRejectsInvalidCidr() {
        InboundRequestGuardTriggersConfig bad = new InboundRequestGuardTriggersConfig();
        bad.setMode(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE);
        bad.setIpAddresses(Set.of("not-a-cidr"));

        IllegalArgumentException ex =
                org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                        () -> service.updateTriggers(bad));

        assertTrue(ex.getMessage().contains("not-a-cidr"));
    }

    @Test
    void updateTriggersAcceptsMixedIpAndCidrEntries() {
        InboundRequestGuardTriggersConfig input = new InboundRequestGuardTriggersConfig();
        input.setMode(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE);
        input.setIpAddresses(Set.of("203.0.113.5", "198.51.100.0/24", "2001:db8::/32", " "));

        service.updateTriggers(input);

        Set<String> persisted = inboundTriggers().getIpAddresses();
        assertEquals(Set.of("203.0.113.5", "198.51.100.0/24", "2001:db8::/32"), persisted);
    }

    @Test
    void shouldBlockCombinesIpAndOtherTriggers() {
        // IP rule alone should be sufficient to trigger a block even when header/UA rules don't match.
        InboundRequestGuardTriggersConfig t = new InboundRequestGuardTriggersConfig();
        t.setMode(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE);
        t.setHeaderNames(Set.of("x-bad"));
        t.setUserAgentSubstrings(Set.of("badtool"));
        t.setIpAddresses(Set.of("203.0.113.0/24"));
        service.setTriggersForTest(t);
        HttpServletRequest req = requestWith("Mozilla", List.of("Content-Type"), "203.0.113.5", null);

        assertTrue(service.shouldBlock(req));
    }

    // ---- System-property default for mode ----

    @Test
    void onContextCreatedSeedsModeFromSaasConstantWhenNoDbRow() {
        // SaaS fresh install: nothing in DB. The SaaS constant is overridden to PFW3_ACTIVE, which must drive
        // the cached mode even though the in-memory field default is PFW3_PROBE.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasDownstreamBlockedMode, "PFW3_ACTIVE");
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.onContextCreated();

        assertEquals(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE, inboundTriggers().getMode());
    }

    @Test
    void shouldBlockUsesSaasConstantModeWhenDbRowMissing() {
        // No DB row + SaaS constant=PFW3_ACTIVE: a header match must block the request.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasDownstreamBlockedMode, "PFW3_ACTIVE");
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.onContextCreated();

        HttpServletRequest req = requestWith("Mozilla", List.of("x-socket-request-id"));
        assertTrue(service.shouldBlock(req));
    }

    @Test
    void dbModeWinsOverConstantWhenBothSet() {
        // Constant says PFW3_ACTIVE, DB row says PFW3_OFF — DB must win so the guard stays disabled.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasDownstreamBlockedMode, "PFW3_ACTIVE");
        InboundRequestGuardTriggersConfig fromDb = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_OFF,
                Set.of("x-bad"), Set.of());
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(fromDb);

        service.onContextCreated();

        HttpServletRequest req = requestWith("Mozilla", List.of("X-Bad"));
        assertFalse(service.shouldBlock(req));
    }

    @Test
    void constantWinsWhenDbRowHasNullMode() {
        // DB row exists but its mode is null (legacy / explicit). Per chosen semantics, the constant takes over
        // rather than falling back to PFW3_PROBE.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasDownstreamBlockedMode, "PFW3_ACTIVE");
        InboundRequestGuardTriggersConfig legacy = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE,
                Set.of("x-bad"), Set.of());
        legacy.setMode(null);
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(legacy);

        service.onContextCreated();

        HttpServletRequest req = requestWith("Mozilla", List.of("X-Bad"));
        assertTrue(service.shouldBlock(req));
    }

    @Test
    void invalidSaasConstantValueFallsBackToProbe() {
        // Garbage in the SaaS property must not throw — the resolver returns PFW3_PROBE so the guard stays
        // detect-only. The seeded header set would otherwise match under PFW3_ACTIVE.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasDownstreamBlockedMode, "NOT_A_MODE");
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.onContextCreated();

        assertEquals(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE, inboundTriggers().getMode());
        HttpServletRequest req = requestWith("Mozilla", List.of("x-socket-request-id"));
        assertFalse(service.shouldBlock(req));
    }

    @Test
    void onContextCreatedSeedsModeFromShConstantWhenNoDbRowOnSelfHosted() {
        // Self-hosted fresh install: nothing in DB. The SH constant is overridden to PFW3_ACTIVE, which must
        // drive the cached mode. isAol is already stubbed false in @BeforeEach.
        setStringSystemProperty(ConstantValues.unsupported3pfwShDownstreamBlockedMode, "PFW3_ACTIVE");
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.onContextCreated();

        assertEquals(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE, inboundTriggers().getMode());
    }

    @Test
    void shouldBlockUsesShConstantWhenDbRowMissingOnSelfHosted() {
        // No DB row, isAol=false, SH constant=PFW3_ACTIVE: header match blocks the request.
        setStringSystemProperty(ConstantValues.unsupported3pfwShDownstreamBlockedMode, "PFW3_ACTIVE");
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.onContextCreated();

        HttpServletRequest req = requestWith("Mozilla", List.of("x-socket-request-id"));
        assertTrue(service.shouldBlock(req));
    }

    @Test
    void selfHostedIgnoresSaasConstant() {
        // Cross-channel guard: an aggressive SaaS constant must not bleed into a self-hosted deployment.
        setStringSystemProperty(ConstantValues.unsupported3pfwSaasDownstreamBlockedMode, "PFW3_ACTIVE");
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.onContextCreated();

        assertEquals(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE, inboundTriggers().getMode());
        HttpServletRequest req = requestWith("Mozilla", List.of("x-socket-request-id"));
        assertFalse(service.shouldBlock(req));
    }

    @Test
    void onContextCreatedLogsResolvedMode() {
        // The startup log lets ops verify the active mode without inspecting state via REST.
        InboundRequestGuardTriggersConfig fromDb = triggers(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE,
                Set.of("x-bad"), Set.of());
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(fromDb);
        ListAppender<ILoggingEvent> appender = attachLogAppender();

        try {
            service.onContextCreated();

            String message = findStartupLog(appender);
            assertTrue(message.contains("mode=PFW3_ACTIVE"), message);
        } finally {
            detachLogAppender(appender);
        }
    }

    private static ListAppender<ILoggingEvent> attachLogAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(InboundRequestGuardServiceImpl.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        return appender;
    }

    private static void detachLogAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(InboundRequestGuardServiceImpl.class);
        logger.detachAppender(appender);
    }

    private static String findStartupLog(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.contains("Inbound request guard initialized"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("startup INFO log not found; captured: " + appender.list));
    }

    @Test
    void saasIgnoresShConstant() {
        // Mirror: an aggressive SH constant must not bleed into a SaaS deployment.
        when(aolService.isAol()).thenReturn(true);
        setStringSystemProperty(ConstantValues.unsupported3pfwShDownstreamBlockedMode, "PFW3_ACTIVE");
        when(configsService.getConfig(eq(INBOUND_REQUEST_GUARD_TRIGGERS),
                eq(InboundRequestGuardTriggersConfig.class))).thenReturn(null);

        service.onContextCreated();

        assertEquals(InboundRequestGuardTriggersConfig.Mode.PFW3_PROBE, inboundTriggers().getMode());
        HttpServletRequest req = requestWith("Mozilla", List.of("x-socket-request-id"));
        assertFalse(service.shouldBlock(req));
    }

    private InboundRequestGuardTriggersConfig inboundTriggers() {
        return service.getInboundRequestGuardTriggersConfig();
    }
}
