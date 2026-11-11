package org.artifactory.throttling.generic.rrl.service;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.config.ConfigurationException;
import org.artifactory.lifecycle.storage.db.servers.event.ServersUpdatedEvent;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.generic.rrl.model.IRateLimiterConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimitConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimiterSettings;
import org.artifactory.throttling.rrl.strategy.InternalRequestRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.rrlGenericEnabled;
import static org.artifactory.common.ConstantValues.rrlGenericIsBlockingModeEnabled;
import static org.artifactory.throttling.common.Utils.MONITORING_UNLIMITED_PERMITS;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_REQUEST_RATE_LIMITER;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_REQUEST_RATE_LIMITER_V2;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class RequestRateLimiterServiceV2ImplTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private ArtifactoryServersCommonService serversService;
    @Mock
    private ConfigsService configsService;
    private RequestRateLimiterServiceV2Impl requestRateLimiterService;
    private AutoCloseable mocks;

    private static final RequestRateLimiterHost DOCKERHUB = new RequestRateLimiterHost(".docker.io", "dockerhub");
    private static final RequestRateLimiterHost GCR = new RequestRateLimiterHost(".gcr.io", "gcr");
    private static final RequestRateLimiterHost QUAY = new RequestRateLimiterHost(".quay.io", "quay");

    public static Stream<Arguments> UpdateConfigWithIllegalDomainSuffixArgsProvider() {
        return Stream.of(
                Arguments.of(StringUtils.EMPTY, 1_000, "domainSuffix can not be empty"),
                Arguments.of("    ", 1_000, "domainSuffix can not be empty"),
                Arguments.of(DOCKERHUB.domainSuffix, 0, "Illegal permitsPerTimeFrame")
        );
    }

    public static Stream<Arguments> acquirePermitSystemProvider() {
        return Stream.of(
                Arguments.of(false, false, true, 998, "test" + DOCKERHUB.domainSuffix()),
                Arguments.of(true, true, true, 998, "test" + DOCKERHUB.domainSuffix()),
                Arguments.of(false, true, true, 998, "test" + DOCKERHUB.domainSuffix()),
                Arguments.of(true, false, true, 998, "test" + DOCKERHUB.domainSuffix()),
                Arguments.of(false, false, false, 998, "test" + DOCKERHUB.domainSuffix() + "/"),
                Arguments.of(true, true, false, 0, "test" + DOCKERHUB.domainSuffix() + "/"),
                Arguments.of(false, true, false, 999, "test" + DOCKERHUB.domainSuffix() + "/"),
                Arguments.of(true, false, false, 999, "test" + DOCKERHUB.domainSuffix() + "/")
        );
    }

    public static Stream<Arguments> updateConfigProvider() {
        return Stream.of(
                Arguments.of(true),
                Arguments.of(false)
        );
    }

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "true");
        getBound().setProperty(rrlGenericIsBlockingModeEnabled.getPropertyName(), "true");
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(serversService.getActiveMembers()).thenReturn(List.of(mock(ArtifactoryServer.class)));
        requestRateLimiterService = new RequestRateLimiterServiceV2Impl(addonsManager, serversService, configsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), rrlGenericEnabled.getDefValue());
        mocks.close();
    }

    @Test
    void testFeatureDisabled() {
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "false");
        requestRateLimiterService =
                new RequestRateLimiterServiceV2Impl(addonsManager, serversService, configsService);
        var rateLimiters = getField(requestRateLimiterService, "rateLimiters");
        assertNull(rateLimiters);
    }

    @Test
    void testFeatureEnabled() {
        var rateLimiters = getField(requestRateLimiterService, "rateLimiters");
        assertNotNull(rateLimiters);
    }

    @Test
    void testGetConfig() {
        var rrlConfig = mock(RequestRateLimitConfig.class);
        when(configsService.getConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2, RequestRateLimitConfig.class))
                .thenReturn(rrlConfig);
        var actualConfig = requestRateLimiterService.getConfig();
        assertSame(rrlConfig, actualConfig);
    }

    @Test
    void testGetConfigFeatureDisabled() {
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "false");
        assertThrows(ConfigurationException.class, () -> requestRateLimiterService.getConfig());
    }

    @Test
    void testGetConfigDefault() {
        when(configsService.getConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2, RequestRateLimitConfig.class))
                .thenReturn(null);

        IRateLimiterConfig config = requestRateLimiterService.getConfig();
        assertNotNull(config);

        assertTrue(config instanceof RequestRateLimitConfig);
        RequestRateLimitConfig rateLimitConfig = (RequestRateLimitConfig) config;

        assertEquals(7, rateLimitConfig.rlSettings().size());

        RequestRateLimiterSettings dockerHubSetting = findSettingByDomain(rateLimitConfig, ".docker.io");
        assertEquals("DockerHub", dockerHubSetting.getName());
        assertEquals(1000, dockerHubSetting.getPermitsPerTimeFrame());
        assertEquals(60000, dockerHubSetting.getTimeFrameMillis());
        assertTrue(dockerHubSetting.isIncludeHead());

        assertMonitoringDomain(rateLimitConfig, "repository.apache.org", "ApacheMaven");
        assertMonitoringDomain(rateLimitConfig, "downloads.apache.org", "ApacheDownloads");
        assertMonitoringDomain(rateLimitConfig, "dhi.io", "DHI");
        assertMonitoringDomain(rateLimitConfig, "registry.npmjs.com", "npm");
        assertMonitoringDomain(rateLimitConfig, "proxy.golang.org", "Golang");
        assertMonitoringDomain(rateLimitConfig, "huggingface.co", "HuggingFace");
    }

    private RequestRateLimiterSettings findSettingByDomain(RequestRateLimitConfig config, String domainSuffix) {
        return config.rlSettings().stream()
                .filter(s -> s.getDomainSuffix().equals(domainSuffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No setting found for domain: " + domainSuffix));
    }

    private void assertMonitoringDomain(RequestRateLimitConfig config, String domainSuffix, String expectedName) {
        RequestRateLimiterSettings setting = findSettingByDomain(config, domainSuffix);
        assertEquals(expectedName, setting.getName());
        assertEquals(MONITORING_UNLIMITED_PERMITS, setting.getPermitsPerTimeFrame());
        assertEquals(60000, setting.getTimeFrameMillis());
        assertTrue(setting.isIncludeHead());
    }

    @ParameterizedTest
    @MethodSource("updateConfigProvider")
    void testUpdateConfig() {
        var rrlConfig = getRequestRateLimitBasicConfig(false);
        requestRateLimiterService.updateConfig(rrlConfig);

        verify(configsService, times(1))
                .addOrUpdateConfig(eq(ARTIFACTORY_REQUEST_RATE_LIMITER_V2), eq(rrlConfig), anyLong());
        verify(haCommonAddon, times(1)).propagateRequestRateLimiterRecalculation();
    }

    @ParameterizedTest
    @MethodSource("UpdateConfigWithIllegalDomainSuffixArgsProvider")
    void testUpdateConfigWithIllegalDomainSuffix(String domainSuffix, int permitPerTimeFrame, String msg) {
        var rrlConfig = getRequestRateLimitConfigByDomain(domainSuffix, permitPerTimeFrame);
        ConfigurationException thrown =
                assertThrows(ConfigurationException.class, () -> requestRateLimiterService.updateConfig(rrlConfig));
        assertTrue(thrown.getMessage().contains(msg));
        verify(configsService, times(0))
                .addOrUpdateConfig(eq(ARTIFACTORY_REQUEST_RATE_LIMITER_V2), eq(rrlConfig), anyLong());
        verify(haCommonAddon, times(0)).propagateRequestRateLimiterRecalculation();
    }

    @Nonnull
    private static RequestRateLimitConfig getRequestRateLimitBasicConfig(boolean includeHead) {
        var conf1 = new RequestRateLimiterSettings(DOCKERHUB.name(),
                DOCKERHUB.domainSuffix(), 1_000, 1_000, includeHead);
        var conf2 = new RequestRateLimiterSettings(QUAY.name(),
                QUAY.domainSuffix(), 1_000, 1_000, includeHead);
        var conf3 =
                new RequestRateLimiterSettings(GCR.name(),
                        GCR.domainSuffix(),
                        1_000, 1_000, includeHead);
        Set<RequestRateLimiterSettings> conf = Set.of(conf1, conf2, conf3);

        var rrlConfig = new RequestRateLimitConfig(conf);
        return rrlConfig;
    }

    @Nonnull
    private static RequestRateLimitConfig getRequestRateLimitConfigByDomain(String domainSuffix,
            int permitPerTimeFrame) {
        var conf1 = new RequestRateLimiterSettings(DOCKERHUB.name(),
                domainSuffix, permitPerTimeFrame, 1_000, true);
        Set<RequestRateLimiterSettings> conf = Set.of(conf1);

        var rrlConfig = new RequestRateLimitConfig(conf);
        return rrlConfig;
    }

    @Test
    void testUpdateConfigFeatureDisabled() {
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "false");
        var defaultSettings = new RequestRateLimiterSettings();
        defaultSettings.setDomainSuffix(""); // Unit tests fail without this line because domainSuffix is @Nonnull
        var dockerhubSettings = new RequestRateLimiterSettings(DOCKERHUB.name(),
                DOCKERHUB.domainSuffix(), 1_000, 1_000, true);
        var rrlConfig = new RequestRateLimitConfig(Set.of(defaultSettings, dockerhubSettings));
        assertThrows(ConfigurationException.class, () -> requestRateLimiterService.updateConfig(rrlConfig));
    }

    @Test
    void testUpdateConfigException() {
        //var defaultSettings = new RequestRateLimiterSettings();
        var rrlConfig = new RequestRateLimitConfig(Set.of());
        assertThrows(ConfigurationException.class, () -> requestRateLimiterService.updateConfig(rrlConfig));
        verify(configsService, times(0)).addOrUpdateConfig(eq(ARTIFACTORY_REQUEST_RATE_LIMITER),
                any(RateLimiterConfig.class), anyLong());
        verify(haCommonAddon, times(0)).propagateRequestRateLimiterRecalculation();
    }

    @Test
    void testDeleteConfig() {
        requestRateLimiterService.deleteConfig();
        verify(configsService, times(1)).deleteConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2.getKeyName());
        verify(haCommonAddon, times(1)).propagateRequestRateLimiterRecalculation();
    }

    @Test
    void testDeleteConfigFeatureDisabled() {
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "false");
        assertThrows(ConfigurationException.class, () -> requestRateLimiterService.deleteConfig());
    }

    @Test
    void testRecalculate() {
        var rateLimitersBefore = (Map<?, ?>) getField(requestRateLimiterService, "rateLimiters");
        requestRateLimiterService.recalculate();
        var rateLimitersAfter = (Map<?, ?>) getField(requestRateLimiterService, "rateLimiters");
        assertNotSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Test
    void testRecalculateFeatureDisabled() {
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "false");
        var rateLimitersBefore = (Map<?, ?>) getField(requestRateLimiterService, "rateLimiters");
        requestRateLimiterService.recalculate();
        var rateLimitersAfter = (Map<?, ?>) getField(requestRateLimiterService, "rateLimiters");
        assertSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Nonnull
    private Map<String, InternalRequestRateLimiter> getRateLimiters(boolean includeHead) {
        var rrlConfig = getRequestRateLimitBasicConfig(includeHead);
        when(configsService.getConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2, RequestRateLimitConfig.class))
                .thenReturn(rrlConfig);
        requestRateLimiterService.recalculate();
        var rateLimiters = (Map<String, InternalRequestRateLimiter>)
                getField(requestRateLimiterService, "rateLimiters");
        assertNotNull(rateLimiters);
        return rateLimiters;
    }

    @Test
    void testAcquirePermitDisabled() {
        var rateLimiters = getRateLimiters(false);

        InternalRequestRateLimiter gcpRrl = spy(rateLimiters.get(GCR.domainSuffix()));
        rateLimiters.put(GCR.domainSuffix, gcpRrl);
        InternalRequestRateLimiter dockerhubRateLimiter = spy(rateLimiters.get(DOCKERHUB.domainSuffix()));
        rateLimiters.put(DOCKERHUB.domainSuffix, dockerhubRateLimiter);
        InternalRequestRateLimiter quayRateLimiter = spy(rateLimiters.get(QUAY.domainSuffix()));
        rateLimiters.put(QUAY.domainSuffix, quayRateLimiter);
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "false");
        requestRateLimiterService.acquirePermit(DOCKERHUB.domainSuffix(), false);

        verify(gcpRrl, times(0)).acquirePermit();
        verify(dockerhubRateLimiter, times(0)).acquirePermit();
        verify(quayRateLimiter, times(0)).acquirePermit();
    }

    @ParameterizedTest
    @MethodSource("acquirePermitSystemProvider")
    void testAcquirePermitSystem(boolean isHeadRequest, boolean isHeadRequest2, boolean shouldCountHeadRequest,
            int expectedAvailablePermits, String fullDomain) {
        var rateLimiters = getRateLimiters(shouldCountHeadRequest);

        InternalRequestRateLimiter gcpRrl = spy(rateLimiters.get(GCR.domainSuffix()));
        rateLimiters.put(GCR.domainSuffix, gcpRrl);
        InternalRequestRateLimiter dockerhubRateLimiter = spy(rateLimiters.get(DOCKERHUB.domainSuffix()));
        rateLimiters.put(DOCKERHUB.domainSuffix, dockerhubRateLimiter);
        InternalRequestRateLimiter quayRateLimiter = spy(rateLimiters.get(QUAY.domainSuffix()));
        rateLimiters.put(QUAY.domainSuffix, quayRateLimiter);

        boolean shouldSkipRequest1 = !shouldCountHeadRequest && isHeadRequest;
        requestRateLimiterService.acquirePermit(fullDomain, isHeadRequest);

        verify(gcpRrl, times(0)).acquirePermit();
        verify(dockerhubRateLimiter, times(shouldSkipRequest1 ? 0 : 1)).acquirePermit();
        verify(quayRateLimiter, times(0)).acquirePermit();

        var metrics = requestRateLimiterService.getMetrics();
        assertEquals(3, metrics.size());
        metrics.forEach((domain, rlMetrics) ->
                assertEquals(0, rlMetrics.get("Drop Count"))
        );

        requestRateLimiterService.acquirePermit(fullDomain, isHeadRequest2);
        assertEquals(expectedAvailablePermits, (int) getField(dockerhubRateLimiter, "availablePermits"));
    }

    @Test
    void testOnApplicationEvent() {
        var rateLimitersBefore = getRateLimiters(false);
        var servers = (AtomicReference<List<ArtifactoryServer>>) getField(requestRateLimiterService, "servers");
        ArtifactoryServer server = mock(ArtifactoryServer.class);
        assertNotNull(servers);
        servers.set(List.of(server));
        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);
        ArtifactoryServer otherServer = mock(ArtifactoryServer.class);
        when(event.getUpdatedServers()).thenReturn(List.of(otherServer));

        requestRateLimiterService.onApplicationEvent(event);

        var rateLimitersAfter = getRateLimiters(false);
        assertNotSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Test
    void testOnApplicationEventFeatureDisabled() {
        getBound().setProperty(rrlGenericEnabled.getPropertyName(), "false");
        var rateLimitersBefore = getRateLimiters(false);
        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);

        requestRateLimiterService.onApplicationEvent(event);

        var rateLimitersAfter = getRateLimiters(false);
        assertSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Test
    void testOnApplicationEventWithPrint() {
        var rateLimiters = getRateLimiters(false);
        var GenericDropRateLimiter = rateLimiters.get(DOCKERHUB.domainSuffix);
        setField(GenericDropRateLimiter, "rateLimitedRequestsCounter", new AtomicLong(30_000));
        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);

        requestRateLimiterService.onApplicationEvent(event);
    }

    @Test
    void testMonitoringDomainsPreservedAfterHaPermitSplit() {
        when(configsService.getConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2, RequestRateLimitConfig.class))
                .thenReturn(null);

        ArtifactoryServer server1 = createRunningServerMock("server1");
        ArtifactoryServer server2 = createRunningServerMock("server2");
        ArtifactoryServer server3 = createRunningServerMock("server3");
        var servers = (AtomicReference<List<ArtifactoryServer>>) getField(requestRateLimiterService, "servers");
        servers.set(List.of(createRunningServerMock("initial-server")));

        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);
        when(event.getUpdatedServers()).thenReturn(List.of(server1, server2, server3));
        requestRateLimiterService.onApplicationEvent(event);

        var rateLimiters = (Map<String, InternalRequestRateLimiter>) getField(
                requestRateLimiterService, "rateLimiters");
        assertNotNull(rateLimiters);

        InternalRequestRateLimiter npmLimiter = rateLimiters.get("registry.npmjs.com");
        assertNotNull(npmLimiter, "Monitoring domain should have a rate limiter");
        int npmPermits = (int) getField(npmLimiter, "permitsPerTimeFrame");
        assertEquals(MONITORING_UNLIMITED_PERMITS, npmPermits,
                "Monitoring permits should not be divided across servers");
    }

    @Test
    void testOnApplicationEvent_NoIllegalPermitsPerSecondError() {
        // Given: Configuration that caused failure with 2 servers
        // permitsPerTimeFrame=5, timeFrameMillis=5000 (5 permits per 5 seconds)
        RequestRateLimiterSettings dockerSettings = new RequestRateLimiterSettings(
                "docker",           // name
                "docker.io",        // domainSuffix
                5,                  // permitsPerTimeFrame - This will cause the issue!
                5000,               // timeFrameMillis
                true                // includeHead
        );

        RequestRateLimitConfig config = new RequestRateLimitConfig(Set.of(dockerSettings));
        when(configsService.getConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2, RequestRateLimitConfig.class))
                .thenReturn(config);

        // When: We simulate having 2 RUNNING servers with recent heartbeat (HA scenario)
        ArtifactoryServer server1 = createRunningServerMock("server1");
        ArtifactoryServer server2 = createRunningServerMock("server2");
        List<ArtifactoryServer> twoServers = List.of(server1, server2);

        // Initially set the service to have 1 server to establish baseline
        var servers = (AtomicReference<List<ArtifactoryServer>>) getField(requestRateLimiterService, "servers");
        servers.set(List.of(createRunningServerMock("initial-server")));

        // Simulate ServersUpdatedEvent with 2 servers (count change triggers recalculation)
        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);
        when(event.getUpdatedServers()).thenReturn(twoServers);

        // Then: The recalculation should fail with "rate must be positive"
        assertDoesNotThrow(() -> requestRateLimiterService.onApplicationEvent(event));
    }

    private ArtifactoryServer createRunningServerMock(String serverId) {
        ArtifactoryServer server = mock(ArtifactoryServer.class);
        when(server.getServerId()).thenReturn(serverId);
        when(server.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(server.getLastHeartbeat()).thenReturn(System.currentTimeMillis()); // Recent heartbeat
        return server;
    }

    public record RequestRateLimiterHost(String domainSuffix, String name) {
    }
}