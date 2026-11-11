package org.artifactory.throttling.qrl.service;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.config.ConfigurationException;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.lifecycle.storage.db.servers.event.ServersUpdatedEvent;
import org.artifactory.metrics.core.CoreMetricsRegistry;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.common.model.RateLimiterSettings;
import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;
import org.artifactory.throtlling.qrl.model.QueryRateLimiterMetrics;
import org.artifactory.throttling.lh.service.LoadHealerRunnable;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.artifactory.throttling.qrl.type.ArtifactoryQueryRateLimiter;
import org.artifactory.throttling.qrl.type.EnabledQueryRateLimiter;
import org.artifactory.util.ThreadMarker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Collections.singleton;
import static org.artifactory.common.ConstantValues.qrlDefaultPermitsPerTimeFrame;
import static org.artifactory.common.ConstantValues.qrlDefaultTimeFrameMillis;
import static org.artifactory.common.ConstantValues.qrlDefaultTimeQuota;
import static org.artifactory.common.ConstantValues.qrlEnabled;
import static org.artifactory.common.ConstantValues.qrlLowPriorityAdditionalList;
import static org.artifactory.common.ConstantValues.qrlLowPriorityDefaultList;
import static org.artifactory.common.ConstantValues.qrlLowPriorityPermitsPerTimeFrame;
import static org.artifactory.common.ConstantValues.qrlLowPriorityTimeFrameMillis;
import static org.artifactory.common.ConstantValues.qrlLowPriorityTimeQuota;
import static org.artifactory.common.ConstantValues.qrlSimulationModeEnabled;
import static org.artifactory.state.ArtifactoryServerState.RUNNING;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_QUERY_RATE_LIMITER;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.DEFAULT;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throtlling.qrl.service.QueryRateLimiterService.AVOID_QRL_LOW_PRIORITY;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.QRL_LOW_PRIORITY_REQUESTS;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.QRL_TYPE_THREAD_LOCAL;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Andrey Tashlitsky
 */
@SuppressWarnings("unchecked")
class QueryRateLimiterServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaCommonAddon haCommonAddon;
    @Mock
    private ArtifactoryServersCommonService serversService;
    @Mock
    private ConfigsService configsService;
    @Mock
    private TaskService taskService;
    @Mock
    private CoreMetricsRegistry coreMetricsRegistry;
    @Mock
    private LoadHealerService loadHealerService;
    private QueryRateLimiterServiceImpl qrlService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        getBound().setProperty(qrlEnabled.getPropertyName(), "true");
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "false");
        getBound().setProperty(qrlDefaultPermitsPerTimeFrame.getPropertyName(), "1000");
        getBound().setProperty(qrlDefaultTimeFrameMillis.getPropertyName(), "1");
        getBound().setProperty(qrlDefaultTimeQuota.getPropertyName(), "2000");
        getBound().setProperty(qrlLowPriorityPermitsPerTimeFrame.getPropertyName(), "1000");
        getBound().setProperty(qrlLowPriorityTimeFrameMillis.getPropertyName(), "1");
        getBound().setProperty(qrlLowPriorityTimeQuota.getPropertyName(), "1000");
        getBound().setProperty(qrlLowPriorityTimeFrameMillis.getPropertyName(), "5000");
        getBound().setProperty(qrlLowPriorityDefaultList.getPropertyName(), "(/api/storage.*)|(/api/plugin/execute.*)");
        getBound().setProperty(qrlLowPriorityAdditionalList.getPropertyName(), "");
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(serversService.getActiveMembers()).thenReturn(List.of(mock(ArtifactoryServer.class)));
        qrlService =
                new QueryRateLimiterServiceImpl(jdbcHelper, addonsManager, serversService, configsService, taskService,
                        coreMetricsRegistry, loadHealerService);
        QRL_TYPE_THREAD_LOCAL.set(DEFAULT);
    }

    @AfterEach
    void tearDown() throws Exception {
        getBound().setProperty(qrlEnabled.getPropertyName(), qrlEnabled.getDefValue());
        mocks.close();
    }

    @Test
    void testFeatureDisabled() {
        getBound().setProperty(qrlEnabled.getPropertyName(), "false");
        qrlService =
                new QueryRateLimiterServiceImpl(jdbcHelper, addonsManager, serversService, configsService, taskService,
                        coreMetricsRegistry, loadHealerService);
        var rateLimiters = getField(qrlService, "rateLimiters");
        assertNull(rateLimiters);
        verify(jdbcHelper, times(0)).setQueryRateLimiter(qrlService);
    }

    @Test
    void testFeatureEnabled() {
        var rateLimiters = getField(qrlService, "rateLimiters");
        assertNotNull(rateLimiters);
        verify(jdbcHelper, times(1)).setQueryRateLimiter(qrlService);
    }

    @Test
    void testWithQueryRateLimiterSupplier() {
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        String wrapped = withQueryRateLimiter(SYSTEM, () -> {
            String wrappedInner = withQueryRateLimiter(LOW_PRIORITY, () -> {
                assertEquals(LOW_PRIORITY, QRL_TYPE_THREAD_LOCAL.get());
                return "cool string";
            });
            assertEquals(SYSTEM, QRL_TYPE_THREAD_LOCAL.get());
            return wrappedInner;
        });
        assertEquals("cool string", wrapped);
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    @Test
    void testWithQueryRateLimiterSupplierShouldBypassLowPriorityQRL() {
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        Supplier<String> supplierAvoidingLowPriority = () -> withQueryRateLimiter(LOW_PRIORITY, () -> {
            assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
            return "result";
        });
        String result = ThreadMarker.executeWithMarker(AVOID_QRL_LOW_PRIORITY,
                supplierAvoidingLowPriority);

        assertEquals("result", result);
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    @Test
    void testWithQueryRateLimiterRunnable() {
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        withQueryRateLimiter(SYSTEM, () -> {
            withQueryRateLimiter(LOW_PRIORITY, () -> assertEquals(LOW_PRIORITY, QRL_TYPE_THREAD_LOCAL.get()));
            assertEquals(SYSTEM, QRL_TYPE_THREAD_LOCAL.get());
        });
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    @Test
    void testWithQueryRateLimiterRunnableShouldBypassLowPriorityQRL() {
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        Runnable r = () -> withQueryRateLimiter(LOW_PRIORITY, () -> {
            assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        });
        ThreadMarker.executeWithMarker(AVOID_QRL_LOW_PRIORITY, r);
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    @Test
    void testGetConfig() {
        var qrlConfig = mock(RateLimiterConfig.class);
        when(configsService.getConfig(ARTIFACTORY_QUERY_RATE_LIMITER, RateLimiterConfig.class))
                .thenReturn(qrlConfig);
        var actualQrlConfig = qrlService.getConfig();
        assertSame(qrlConfig, actualQrlConfig);
    }

    @Test
    void testGetConfigFeatureDisabled() {
        getBound().setProperty(qrlEnabled.getPropertyName(), "false");
        assertThrows(ConfigurationException.class, () -> qrlService.getConfig());
    }

    @Test
    void testGetConfigDefault() {
        when(configsService.getConfig(ARTIFACTORY_QUERY_RATE_LIMITER, RateLimiterConfig.class)).thenReturn(null);
        RateLimiterConfig qrlDefaultConfig = (RateLimiterConfig) getField(qrlService, "qrlDefaultConfig");
        assertNotNull(qrlDefaultConfig);
        assertSame(qrlDefaultConfig, qrlService.getConfig());
    }

    @Test
    void testUpdateConfig() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, 10_000, 5_000, 2_000);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, 1_000, 1_000, 500);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        qrlService.updateConfig(qrlConfig);
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_QUERY_RATE_LIMITER),
                any(RateLimiterConfig.class), anyLong());
        verify(haCommonAddon, times(1)).propagateQueryRateLimiterRecalculation();
    }

    @Test
    void testUpdateConfigIgnoringNotPassed() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, null, null, null);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, null, null, null);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        final RateLimiterConfig existingConfig = qrlService.getConfig();
        qrlService.updateConfig(qrlConfig);
        final RateLimiterConfig afterUpdate = qrlService.getConfig();
        assertEquals(afterUpdate.rlSettings().size(), 2);
        final Optional<RateLimiterSettings> opLow =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == LOW_PRIORITY).findFirst();
        assertTrue(opLow.isPresent());
        final Optional<RateLimiterSettings> opDef =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == DEFAULT).findFirst();
        assertTrue(opDef.isPresent());
        final Optional<RateLimiterSettings> opExLow =
                existingConfig.rlSettings().stream().filter(it -> it.getRlType() == LOW_PRIORITY).findFirst();
        assertTrue(opExLow.isPresent());
        final Optional<RateLimiterSettings> opExDef =
                existingConfig.rlSettings().stream().filter(it -> it.getRlType() == DEFAULT).findFirst();
        assertTrue(opExDef.isPresent());
        assertEquals(opExLow.get().getPermitsPerTimeFrame(), opLow.get().getPermitsPerTimeFrame());
        assertEquals(opExLow.get().getTimeFrameMillis(), opLow.get().getTimeFrameMillis());
        assertEquals(opExLow.get().getTimeQuota(), opLow.get().getTimeQuota());
        assertEquals(opExDef.get().getPermitsPerTimeFrame(), opDef.get().getPermitsPerTimeFrame());
        assertEquals(opExDef.get().getTimeFrameMillis(), opDef.get().getTimeFrameMillis());
        assertEquals(opExDef.get().getTimeQuota(), opDef.get().getTimeQuota());
    }

    @Test
    void testUpdateConfigPartialIgnoringNotPassed() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, 100_000, null, null);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, null, 200_000, null);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        qrlService.updateConfig(qrlConfig);
        final RateLimiterConfig afterUpdate = qrlService.getConfig();
        assertEquals(afterUpdate.rlSettings().size(), 2);
        final Optional<RateLimiterSettings> opDef =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == DEFAULT).findFirst();
        assertTrue(opDef.isPresent());
        assertEquals(defaultSettings.getPermitsPerTimeFrame(), opDef.get().getPermitsPerTimeFrame());
        final Optional<RateLimiterSettings> opLow =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == LOW_PRIORITY).findFirst();
        assertTrue(opLow.isPresent());
        assertEquals(lowPrioritySettings.getTimeFrameMillis(), opLow.get().getTimeFrameMillis());
    }

    @Test
    void testUpdateConfigWithoutTimeQuota() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, 100_000, null, null);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, null, 200_000, null);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        qrlService.updateConfig(qrlConfig);
        final RateLimiterConfig afterUpdate = qrlService.getConfig();
        assertEquals(afterUpdate.rlSettings().size(), 2);
        final Optional<RateLimiterSettings> opDef =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == DEFAULT).findFirst();
        assertTrue(opDef.isPresent());
        assertEquals(qrlDefaultTimeQuota.getInt(), opDef.get().getTimeQuota());
        final Optional<RateLimiterSettings> opLow =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == LOW_PRIORITY).findFirst();
        assertTrue(opLow.isPresent());
        assertEquals(qrlLowPriorityTimeQuota.getInt(), opLow.get().getTimeQuota());
    }

    @Test
    void testUpdateConfigWithTimeQuota() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, 100_000, null, 1000);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, null, 200_000, 2000);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        qrlService.updateConfig(qrlConfig);
        final RateLimiterConfig afterUpdate = qrlService.getConfig();
        assertEquals(afterUpdate.rlSettings().size(), 2);
        final Optional<RateLimiterSettings> opDef =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == DEFAULT).findFirst();
        assertTrue(opDef.isPresent());
        assertEquals(1000, opDef.get().getTimeQuota());
        final Optional<RateLimiterSettings> opLow =
                afterUpdate.rlSettings().stream().filter(it -> it.getRlType() == LOW_PRIORITY).findFirst();
        assertTrue(opLow.isPresent());
        assertEquals(2000, opLow.get().getTimeQuota());
    }

    @Test
    void testUpdateConfigWhenExistsInDB() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, null, null, null);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, null, null, null);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        when(configsService.getConfig(ARTIFACTORY_QUERY_RATE_LIMITER, RateLimiterConfig.class)).thenReturn(qrlConfig);
        assertTrue(qrlConfig.rlSettings().stream()
                .anyMatch(it -> it.getTimeQuota() == null || it.getTimeFrameMillis() == null ||
                        it.getPermitsPerTimeFrame() == null));
        qrlService.recalculate();
        assertFalse(qrlConfig.rlSettings().stream()
                .anyMatch(it -> it.getTimeQuota() == null || it.getTimeFrameMillis() == null ||
                        it.getPermitsPerTimeFrame() == null));
    }

    @Test
    void testUpdateConfigFeatureDisabled() {
        getBound().setProperty(qrlEnabled.getPropertyName(), "false");
        var defaultSettings = new RateLimiterSettings(DEFAULT, 10_000, 5_000, 2_000);
        var lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY, 1_000, 1_000, 500);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        assertThrows(ConfigurationException.class, () -> qrlService.updateConfig(qrlConfig));
    }

    @Test
    void testUpdateConfigPartial() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, 10_000, 5_000, 2_000);
        var qrlConfig = new RateLimiterConfig(new HashSet<>(singleton(defaultSettings)));
        qrlService.updateConfig(qrlConfig);
        RateLimiterConfig qrlDefaultConfig = (RateLimiterConfig) getField(qrlService, "qrlDefaultConfig");
        assertNotNull(qrlDefaultConfig);
        var lowPrioritySettings = qrlDefaultConfig.rlSettings().stream()
                .filter(e -> e.getRlType() == LOW_PRIORITY)
                .findFirst()
                .orElseThrow();
        var expectedConfig = new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
        assertEquals(expectedConfig, qrlService.getConfig());
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_QUERY_RATE_LIMITER),
                any(RateLimiterConfig.class), anyLong());
        verify(haCommonAddon, times(1)).propagateQueryRateLimiterRecalculation();
    }

    @Test
    void testDeleteConfig() {
        qrlService.deleteConfig();
        verify(configsService, times(1)).deleteConfig(ARTIFACTORY_QUERY_RATE_LIMITER.getKeyName());
        verify(haCommonAddon, times(1)).propagateQueryRateLimiterRecalculation();
    }

    @Test
    void testDeleteConfigFeatureDisabled() {
        getBound().setProperty(qrlEnabled.getPropertyName(), "false");
        assertThrows(ConfigurationException.class, () -> qrlService.deleteConfig());
    }

    @Test
    void testAcquirePermitDefault() {
        var rateLimiters =
                (Map<QueryRateLimiterType, ArtifactoryQueryRateLimiter>) getField(qrlService, "rateLimiters");
        assertNotNull(rateLimiters);
        ArtifactoryQueryRateLimiter defaultQrl = spy(rateLimiters.get(DEFAULT));
        rateLimiters.put(DEFAULT, defaultQrl);
        ArtifactoryQueryRateLimiter lowPriorityQrl = spy(rateLimiters.get(LOW_PRIORITY));
        rateLimiters.put(LOW_PRIORITY, lowPriorityQrl);
        ArtifactoryQueryRateLimiter systemQrl = spy(rateLimiters.get(SYSTEM));
        rateLimiters.put(SYSTEM, systemQrl);
        qrlService.acquirePermit();
        verify(defaultQrl, times(1)).acquirePermit();
        verify(lowPriorityQrl, times(0)).acquirePermit();
        verify(systemQrl, times(0)).acquirePermit();
    }

    @Test
    void testAcquirePermitSystem() {
        QRL_TYPE_THREAD_LOCAL.set(SYSTEM);
        var rateLimiters =
                (Map<QueryRateLimiterType, ArtifactoryQueryRateLimiter>) getField(qrlService, "rateLimiters");
        assertNotNull(rateLimiters);
        ArtifactoryQueryRateLimiter defaultQrl = spy(rateLimiters.get(DEFAULT));
        rateLimiters.put(DEFAULT, defaultQrl);
        ArtifactoryQueryRateLimiter lowPriorityQrl = spy(rateLimiters.get(LOW_PRIORITY));
        rateLimiters.put(LOW_PRIORITY, lowPriorityQrl);
        ArtifactoryQueryRateLimiter systemQrl = spy(rateLimiters.get(SYSTEM));
        rateLimiters.put(SYSTEM, systemQrl);
        qrlService.acquirePermit();
        verify(defaultQrl, times(0)).acquirePermit();
        verify(lowPriorityQrl, times(0)).acquirePermit();
        verify(systemQrl, times(1)).acquirePermit();
    }

    @Test
    void testRecalculate() {
        var rateLimitersBefore = (Map<?, ?>) getField(qrlService, "rateLimiters");
        qrlService.recalculate();
        var rateLimitersAfter = (Map<?, ?>) getField(qrlService, "rateLimiters");
        assertNotSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Test
    void testRecalculateFeatureDisabled() {
        getBound().setProperty(qrlEnabled.getPropertyName(), "false");
        var rateLimitersBefore = (Map<?, ?>) getField(qrlService, "rateLimiters");
        qrlService.recalculate();
        var rateLimitersAfter = (Map<?, ?>) getField(qrlService, "rateLimiters");
        assertSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Test
    void testRecalculateException() {
        var defaultSettings = new RateLimiterSettings(DEFAULT, 10_000, 5_000, 2_000);
        var qrlConfig = new RateLimiterConfig(Set.of(defaultSettings));
        when(configsService.getConfig(ARTIFACTORY_QUERY_RATE_LIMITER, RateLimiterConfig.class))
                .thenReturn(qrlConfig);
        assertDoesNotThrow(() -> qrlService.recalculate());
    }

    @Test
    void testOnApplicationEvent() {
        var rateLimitersBefore = (Map<?, ?>) getField(qrlService, "rateLimiters");
        var servers = (AtomicReference<List<ArtifactoryServer>>) getField(qrlService, "servers");
        assertNotNull(servers);
        ArtifactoryServer server = mock(ArtifactoryServer.class);
        servers.set(List.of(server));
        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);
        ArtifactoryServer otherServer = mock(ArtifactoryServer.class);
        when(event.getUpdatedServers()).thenReturn(List.of(otherServer));
        qrlService.onApplicationEvent(event);
        var rateLimitersAfter = (Map<?, ?>) getField(qrlService, "rateLimiters");
        assertNotSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Test
    void testOnApplicationEventNoRecalculation() {
        var rateLimitersBefore = (Map<?, ?>) getField(qrlService, "rateLimiters");
        var servers = (AtomicReference<List<ArtifactoryServer>>) getField(qrlService, "servers");
        assertNotNull(servers);
        ArtifactoryServer server = mock(ArtifactoryServer.class);
        when(server.getServerState()).thenReturn(RUNNING);
        when(server.getLastHeartbeat()).thenReturn(System.currentTimeMillis());
        servers.set(List.of(server));
        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);
        when(event.getUpdatedServers()).thenReturn(List.of(server));
        qrlService.onApplicationEvent(event);
        var rateLimitersAfter = (Map<?, ?>) getField(qrlService, "rateLimiters");
        assertSame(rateLimitersBefore, rateLimitersAfter);
    }

    @Test
    void testOnApplicationEventFeatureDisabled() {
        getBound().setProperty(qrlEnabled.getPropertyName(), "false");
        var rateLimitersBefore = (Map<?, ?>) getField(qrlService, "rateLimiters");
        ServersUpdatedEvent event = mock(ServersUpdatedEvent.class);
        qrlService.onApplicationEvent(event);
        var rateLimitersAfter = (Map<?, ?>) getField(qrlService, "rateLimiters");
        assertSame(rateLimitersBefore, rateLimitersAfter);
    }

    @ParameterizedTest
    @MethodSource("onContextCreated")
    void testOnContextCreated(boolean enabled, int expectedInvocations) {
        getBound().setProperty(qrlEnabled.getPropertyName(), String.valueOf(enabled));
        qrlService.onContextCreated();
        verify(taskService, times(expectedInvocations)).startTask(any(Task.class), eq(false));
    }

    private static Stream<Arguments> onContextCreated() {
        return Stream.of(
                Arguments.of(true, 1),
                Arguments.of(false, 0)
        );
    }

    @Test
    void testCollectMetrics() {
        var rateLimiters =
                (Map<QueryRateLimiterType, ArtifactoryQueryRateLimiter>) getField(qrlService, "rateLimiters");
        assertNotNull(rateLimiters);
        EnabledQueryRateLimiter defaultQrl = (EnabledQueryRateLimiter) spy(rateLimiters.get(DEFAULT));
        rateLimiters.put(DEFAULT, defaultQrl);
        QueryRateLimiterMetrics qrlDefaultMetrics = new QueryRateLimiterMetrics();
        qrlDefaultMetrics.incrementTotalQueries();
        qrlDefaultMetrics.addTotalSlowedDownMillis(1000);
        qrlDefaultMetrics.addTotalSlowedDownByTimeMillis(1000);
        setField(defaultQrl, "qrlMetrics", qrlDefaultMetrics);
        qrlService.collectMetrics();
        verify(coreMetricsRegistry, times(1)).setQueryRateLimiterMetrics(any());
    }

    @ParameterizedTest
    @MethodSource("lists")
    void testWrapRequestWithLowPriorityNoMatch(String defaultList, String additionalList)
            throws ServletException, IOException {
        getBound().setProperty(qrlLowPriorityDefaultList.getPropertyName(), defaultList);
        getBound().setProperty(qrlLowPriorityAdditionalList.getPropertyName(), additionalList);
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/cool/path");
        qrlService.wrapRequestWithLowPriority(request,
                () -> assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get()));
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    private static Stream<Arguments> lists() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of("", "cool"),
                Arguments.of(qrlLowPriorityDefaultList.getDefValue(), ""),
                Arguments.of(qrlLowPriorityDefaultList.getDefValue(), "cool")
        );
    }

    @Test
    void testWrapRequestWithLowPriorityMatch() throws ServletException, IOException {
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/storage/path");
        qrlService.wrapRequestWithLowPriority(request,
                () -> assertEquals(LOW_PRIORITY, QRL_TYPE_THREAD_LOCAL.get()));
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        qrlService.wrapRequestWithLowPriority(request,
                () -> assertEquals(LOW_PRIORITY, QRL_TYPE_THREAD_LOCAL.get()));
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    @Test
    void loadHealerExceptionThrown() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/storage/path");
        doThrow(new LoadHealerException("Repo key throttled"))
                .when(loadHealerService).executeWithActivity(
                        eq(QRL_LOW_PRIORITY_REQUESTS), any(), any());
        assertThrows(LoadHealerException.class, () -> qrlService.wrapRequestWithLowPriority(request, () -> {}));
    }

    @Test
    void executeWithActivityShouldBeCalledForLowPriorityRequests() throws ServletException, IOException {
        // Arrange
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/storage/path");
        boolean[] runnableExecuted = {false};
        doAnswer(invocation -> {
            invocation.getArgument(2, LoadHealerRunnable.class).run();
            return null;
        }).when(loadHealerService).executeWithActivity(eq(QRL_LOW_PRIORITY_REQUESTS), any(), any());

        // Act
        qrlService.wrapRequestWithLowPriority(request, () -> runnableExecuted[0] = true);

        // Assert
        assertTrue(runnableExecuted[0]);
        verify(loadHealerService).executeWithActivity(eq(QRL_LOW_PRIORITY_REQUESTS), any(), any());
    }

    @Test
    void testWrapRequestWithLowPriorityFeatureDisabled() throws ServletException, IOException {
        getBound().setProperty(qrlEnabled.getPropertyName(), "false");
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/storage/path");
        qrlService.wrapRequestWithLowPriority(request, () -> assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get()));
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    @Test
    void testWrapRequestWithLowPriorityEmptyPattern() throws ServletException, IOException {
        getBound().setProperty(qrlLowPriorityDefaultList.getPropertyName(), "");
        qrlService =
                new QueryRateLimiterServiceImpl(jdbcHelper, addonsManager, serversService, configsService, taskService,
                        coreMetricsRegistry, loadHealerService);
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContextPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/api/storage/path");
        qrlService.wrapRequestWithLowPriority(request,
                () -> assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get()));
        assertEquals(DEFAULT, QRL_TYPE_THREAD_LOCAL.get());
    }

    @ParameterizedTest
    @MethodSource("getLowPriorityPattern")
    void testGetLowPriorityPattern(String defaultList, String additionalList) {
        getBound().setProperty(qrlLowPriorityDefaultList.getPropertyName(), defaultList);
        getBound().setProperty(qrlLowPriorityAdditionalList.getPropertyName(), additionalList);
        qrlService =
                new QueryRateLimiterServiceImpl(jdbcHelper, addonsManager, serversService, configsService, taskService,
                        coreMetricsRegistry, loadHealerService);
        Pattern lowPriorityPattern = (Pattern) getField(qrlService, "lowPriorityPattern");
        assertNull(lowPriorityPattern);
    }

    private static Stream<Arguments> getLowPriorityPattern() {
        return Stream.of(
                Arguments.of("abc", "abc["),
                Arguments.of("", "cool")
        );
    }
}
