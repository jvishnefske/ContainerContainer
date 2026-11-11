package org.artifactory.probes.tomcat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.util.TomcatListeningDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;

import javax.management.InstanceNotFoundException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test to verify that TomcatConnectorsMonitor#getConnectorMetric recovers when an
 * InstanceNotFoundException is thrown during attribute access by re-discovering connectors and retrying.
 */
class TomcatConnectorsMonitorInstanceNotFoundTest {

    private static final String CONNECTOR_NAME = "\"http-nio-8081\"";

    private TomcatConnectorsMonitor monitor;
    private MBeanServerProxy flakyProxy;
    private MockedStatic<TomcatListeningDetector> detectorMockedStatic;

    @BeforeEach
    void setup() throws Exception {
        flakyProxy = mock(MBeanServerProxy.class);
        // create a real monitor and spy on it so we can stub findMBeanServer()
        monitor = spy(new TomcatConnectorsMonitor());
        doReturn(flakyProxy).when(monitor).findMBeanServer();

        detectorMockedStatic = mockStatic(TomcatListeningDetector.class);
        detectorMockedStatic.when(TomcatListeningDetector::detectMainListeningEndpoint)
                .thenReturn(CONNECTOR_NAME);

        TomcatConnector connector = TomcatConnector.builder()
                .name(CONNECTOR_NAME)
                .maxThreads(200)
                .maxConnections(300)
                .threadPool(new ObjectName("Catalina:type=ThreadPool,name=" + CONNECTOR_NAME))
                .requestProcessor(new ObjectName("Catalina:type=GlobalRequestProcessor,name=" + CONNECTOR_NAME))
                .build();

        Map<String, TomcatConnector> connectors = new HashMap<>();
        connectors.put(CONNECTOR_NAME, connector);

        when(monitor.findConnectors()).thenReturn(connectors);

        Field activeNamesField = TomcatConnectorsMonitor.class.getDeclaredField("activeConnectorsNames");
        activeNamesField.setAccessible(true);
        activeNamesField.set(monitor, Set.of(CONNECTOR_NAME));

        monitor.init();
    }

    @AfterEach
    void tearDown() {
        detectorMockedStatic.close();
    }

    @FunctionalInterface
    private interface LogCaptureAssertions {
        void accept(ListAppender<ILoggingEvent> appender) throws Exception;
    }

    /**
     * Attaches a {@link ListAppender} to the monitor's logger at INFO level, runs the supplied
     * assertions against the captured events, and always detaches the appender and restores the
     * previous level afterwards.
     */
    private void withInfoLogCapture(LogCaptureAssertions assertions) throws Exception {
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger logbackLogger = (Logger) LoggerFactory.getLogger(TomcatConnectorsMonitor.class);
        Level previousLevel = logbackLogger.getLevel();
        try {
            logbackLogger.setLevel(Level.INFO);
            listAppender.start();
            logbackLogger.addAppender(listAppender);
            assertions.accept(listAppender);
        } finally {
            logbackLogger.detachAppender(listAppender);
            listAppender.stop();
            logbackLogger.setLevel(previousLevel);
        }
    }

    private static boolean hasLog(ListAppender<ILoggingEvent> appender, Level level, String substring) {
        return appender.list.stream()
                .filter(e -> level.equals(e.getLevel()))
                .anyMatch(e -> e.getFormattedMessage() != null && e.getFormattedMessage().contains(substring));
    }

    @Test
    void shouldRecoverFromInstanceNotFoundOnFirstAttributeAccess() throws Exception {
        // Configure the proxy:
        // - Throw InstanceNotFoundException on the FIRST getAttribute call
        // - Return numeric strings for subsequent getAttribute calls depending on the attribute name
        // - Return non-empty results for queryNames/queryMBeans so re-discovery succeeds
        AtomicBoolean firstThrown = new AtomicBoolean(false);
        when(flakyProxy.getAttribute(any(ObjectName.class), anyString())).thenAnswer(inv -> {
            if (!firstThrown.getAndSet(true)) {
                throw new InstanceNotFoundException("simulated");
            }
            String attr = inv.getArgument(1);
            return switch (attr) {
                case "maxThreads" -> "200";
                case "maxConnections" -> "300";
                case "currentThreadsBusy" -> "5";
                case "connectionCount" -> "10";
                case "requestCount" -> "42";
                default -> "0";
            };
        });

        // queryNames: a one-element result lets getJmxDomain() resolve a domain; the ObjectName is ignored
        when(flakyProxy.queryNames(any(ObjectName.class), any()))
                .thenReturn(Set.of(mock(ObjectName.class)));

        // queryMBeans: return an ObjectInstance so findConnectors builds connector maps
        when(flakyProxy.queryMBeans(any(ObjectName.class), any())).thenAnswer(inv -> {
            ObjectName on = new ObjectName("Catalina:type=ThreadPool,name=" + CONNECTOR_NAME);
            ObjectInstance oi = new ObjectInstance(on, "java.lang.Object");
            return Set.of(oi);
        });

        Optional<ConnectorMetric> metric = monitor.getConnectorMetric(CONNECTOR_NAME);
        assertTrue(metric.isPresent());

        ConnectorMetric m = metric.get();
        assertEquals(CONNECTOR_NAME, m.name());
        // Values returned by the mocked proxy for the attributes
        assertEquals(200L, m.maxThreads());
        assertEquals(300L, m.maxConnections());
        assertEquals(5L, m.currentThreadsBusy());
        assertEquals(10L, m.currentConnections());
        assertEquals(42L, m.processedCount());

        // Verify that the proxy did encounter an InstanceNotFound on first attempt
        assertTrue(firstThrown.get());
    }

    @Test
    void shouldReportCacheFreshWhenAnotherThreadAlreadyReDiscovered() throws Exception {
        // Concurrent path: another thread swapped the connector cache while this thread waited for the
        // write lock. reDiscoverConnectors() reads connectorsRef.get() once before locking (the backup)
        // and once after; returning two different map instances makes the "already re-discovered" guard
        // fire deterministically, with no real threads. The private final field is injected via
        // reflection (setAccessible), so no access modifier change is required.
        @SuppressWarnings("unchecked")
        AtomicReference<Map<String, TomcatConnector>> connectorsRef =
                (AtomicReference<Map<String, TomcatConnector>>) mock(AtomicReference.class);
        when(connectorsRef.get())
                .thenReturn(new HashMap<>())   // pre-lock read: the backup snapshot
                .thenReturn(new HashMap<>());  // post-lock read: a different instance -> concurrent path

        Field connectorsRefField = TomcatConnectorsMonitor.class.getDeclaredField("connectorsRef");
        connectorsRefField.setAccessible(true);
        connectorsRefField.set(monitor, connectorsRef);

        withInfoLogCapture(listAppender -> {
            boolean cacheFresh = monitor.reDiscoverConnectors();

            assertAll(
                    () -> assertTrue(cacheFresh,
                            "Concurrent re-discovery must report the cache as fresh"),
                    () -> assertTrue(hasLog(listAppender, Level.INFO,
                                    "Connectors were already re-discovered by another thread"),
                            "Expected INFO noting another thread re-discovered the connectors"),
                    // The method must short-circuit before running its own re-discovery: on this path
                    // it never overwrites connectorsRef.
                    () -> verify(connectorsRef, never()).set(any()));
        });
    }

    @Test
    void shouldKeepExistingConnectorsWhenReDiscoveryThrows() throws Exception {
        doCallRealMethod().when(monitor).findConnectors();

        // The MBean is absent, so the InstanceNotFoundException path triggers re-discovery.
        when(flakyProxy.getAttribute(any(ObjectName.class), anyString()))
                .thenThrow(new InstanceNotFoundException("simulated"));

        // queryNames: a one-element result lets getJmxDomain() resolve a domain; the ObjectName is ignored
        when(flakyProxy.queryNames(any(ObjectName.class), any()))
                .thenReturn(Set.of(mock(ObjectName.class)));
        // queryMBeans throws: re-discovery fails outright (not merely empty), exercising the catch
        // path of findConnectors(false), which preserves the cache and logs the temporary-failure WARN.
        when(flakyProxy.queryMBeans(any(ObjectName.class), any()))
                .thenThrow(new RuntimeException("simulated JMX failure during re-discovery"));

        withInfoLogCapture(listAppender -> {
            Optional<ConnectorMetric> metric = monitor.getConnectorMetric(CONNECTOR_NAME);

            assertAll(
                    () -> assertFalse(metric.isPresent()),
                    () -> assertEquals(Set.of(CONNECTOR_NAME), monitor.getConnectorNames()),
                    () -> assertTrue(hasLog(listAppender, Level.WARN,
                            "Tomcat connectors were temporarily unavailable during re-discovery")),
                    () -> assertFalse(hasLog(listAppender, Level.ERROR, "Failed to get connector metric")));
        });
    }

    @Test
    void shouldNotLogErrorWhenRetryFailsDuringTransientJmxEmptyWindow() throws Exception {
        ObjectName staleThreadPool = new ObjectName("Catalina:type=ThreadPool,name=" + CONNECTOR_NAME);

        doCallRealMethod().when(monitor).findConnectors();

        // Every getAttribute call throws — models a full transient window where the MBean
        // remains absent for the entire poll cycle (both first attempt and any retry)
        when(flakyProxy.getAttribute(any(ObjectName.class), anyString()))
                .thenThrow(new InstanceNotFoundException("simulated transient window"));

        // queryNames: a one-element result lets getJmxDomain() resolve a domain; the ObjectName is ignored
        when(flakyProxy.queryNames(any(ObjectName.class), any()))
                .thenReturn(Set.of(mock(ObjectName.class)));

        // queryMBeans: empty — JMX temporarily has no ThreadPool/GlobalRequestProcessor MBeans
        when(flakyProxy.queryMBeans(any(ObjectName.class), any())).thenReturn(Collections.emptySet());

        withInfoLogCapture(listAppender -> {
            Optional<ConnectorMetric> metric = monitor.getConnectorMetric(CONNECTOR_NAME);

            String preservedCacheWarn = "re-discovery found no connectors; keeping cached connectors";
            assertAll(
                    () -> assertTrue(metric.isEmpty(),
                            "Expected empty metric during transient JMX window"),
                    () -> assertFalse(hasLog(listAppender, Level.ERROR, "Failed to get connector metric"),
                            "No ERROR log expected during transient window"),
                    () -> assertTrue(hasLog(listAppender, Level.WARN, preservedCacheWarn),
                            "Expected WARN log about preserved cache"),
                    () -> assertEquals(Set.of(CONNECTOR_NAME), monitor.getConnectorNames(),
                            "Cache should be preserved despite transient JMX window"),
                    // The retry must be skipped when re-discovery preserved the cache: the stale
                    // ObjectName is accessed exactly once (the initial attempt), never a second time.
                    () -> verify(flakyProxy, times(1)).getAttribute(staleThreadPool, "currentThreadsBusy"));
        });
    }

    @Test
    void shouldNotLogErrorWhenMBeanDisappearsAgainAfterSuccessfulReDiscovery() throws Exception {
        ObjectName threadPool = new ObjectName("Catalina:type=ThreadPool,name=" + CONNECTOR_NAME);
        ObjectName requestProcessor =
                new ObjectName("Catalina:type=GlobalRequestProcessor,name=" + CONNECTOR_NAME);

        doCallRealMethod().when(monitor).findConnectors();

        // Double flash: live metric reads (currentThreadsBusy, ...) always throw, so both the initial
        // attempt and the post-re-discovery retry fail. maxThreads/maxConnections succeed so that
        // re-discovery itself rebuilds the connector — the cache IS updated and the connector IS found.
        when(flakyProxy.getAttribute(any(ObjectName.class), anyString())).thenAnswer(inv -> {
            String attr = inv.getArgument(1);
            return switch (attr) {
                case "maxThreads" -> "200";
                case "maxConnections" -> "300";
                default -> throw new InstanceNotFoundException("simulated double flash");
            };
        });

        // queryNames: a one-element result lets getJmxDomain() resolve a domain; the ObjectName is ignored
        when(flakyProxy.queryNames(any(ObjectName.class), any()))
                .thenReturn(Set.of(mock(ObjectName.class)));

        // queryMBeans: re-discovery finds the same connector back, so the cache is rebuilt with it
        when(flakyProxy.queryMBeans(any(ObjectName.class), any())).thenAnswer(inv -> {
            ObjectName pattern = inv.getArgument(0);
            ObjectName on = "ThreadPool".equals(pattern.getKeyProperty("type")) ? threadPool : requestProcessor;
            return Set.of(new ObjectInstance(on, "java.lang.Object"));
        });

        withInfoLogCapture(listAppender -> {
            Optional<ConnectorMetric> metric = monitor.getConnectorMetric(CONNECTOR_NAME);

            String reappearWarn = "disappeared again immediately after re-discovery";
            assertAll(
                    () -> assertTrue(metric.isEmpty(),
                            "Expected empty metric when the MBean re-vanishes during the retry"),
                    () -> assertFalse(hasLog(listAppender, Level.ERROR, "Failed to get connector metric"),
                            "No ERROR log expected on a second transient flash"),
                    () -> assertTrue(hasLog(listAppender, Level.WARN, reappearWarn),
                            "Expected WARN about the MBean disappearing again after re-discovery"),
                    // The retry must actually run on this path: currentThreadsBusy is read once on the
                    // initial attempt and once more on the retry, i.e. exactly twice.
                    () -> verify(flakyProxy, times(2)).getAttribute(threadPool, "currentThreadsBusy"));
        });
    }

    @Test
    void shouldReturnEmptyWhenConnectorNotFoundAfterReDiscovery() throws Exception {
        String replacementConnector = "\"http-nio-127.0.0.1-9090\"";
        ObjectName staleThreadPool = new ObjectName("Catalina:type=ThreadPool,name=" + CONNECTOR_NAME);
        ObjectName replacementThreadPool =
                new ObjectName("Catalina:type=ThreadPool,name=" + replacementConnector);
        ObjectName replacementRequestProcessor =
                new ObjectName("Catalina:type=GlobalRequestProcessor,name=" + replacementConnector);

        doCallRealMethod().when(monitor).findConnectors();

        // Throw only for the stale connector's ObjectName; succeed for the replacement connector
        // so that re-discovery builds a fresh cache containing replacementConnector but not CONNECTOR_NAME
        when(flakyProxy.getAttribute(any(ObjectName.class), anyString())).thenAnswer(inv -> {
            ObjectName on = inv.getArgument(0);
            if (on.equals(staleThreadPool)) {
                throw new InstanceNotFoundException("simulated stale ObjectName");
            }
            String attr = inv.getArgument(1);
            return switch (attr) {
                case "maxThreads" -> "200";
                case "maxConnections" -> "300";
                default -> "0";
            };
        });

        // queryNames: a one-element result lets getJmxDomain() resolve a domain; the ObjectName is ignored
        when(flakyProxy.queryNames(any(ObjectName.class), any()))
                .thenReturn(Set.of(mock(ObjectName.class)));

        // queryMBeans: re-discovery finds only replacementConnector — CONNECTOR_NAME is permanently gone
        when(flakyProxy.queryMBeans(any(ObjectName.class), any())).thenAnswer(inv -> {
            ObjectName pattern = inv.getArgument(0);
            ObjectName on = "ThreadPool".equals(pattern.getKeyProperty("type"))
                    ? replacementThreadPool : replacementRequestProcessor;
            return Set.of(new ObjectInstance(on, "java.lang.Object"));
        });

        withInfoLogCapture(listAppender -> {
            Optional<ConnectorMetric> metric = monitor.getConnectorMetric(CONNECTOR_NAME);

            assertAll(
                    () -> assertTrue(metric.isEmpty()),
                    () -> assertFalse(monitor.getConnectorNames().contains(CONNECTOR_NAME),
                            "Stale connector should have been replaced in cache after re-discovery"),
                    () -> assertTrue(monitor.getConnectorNames().contains(replacementConnector),
                            "Replacement connector should be in cache after re-discovery"),
                    // Confirm the InstanceNotFoundException path was actually entered: the stale
                    // ObjectName must have been queried at least once before re-discovery ran.
                    () -> verify(flakyProxy, atLeastOnce()).getAttribute(eq(staleThreadPool), anyString()),
                    // The retry is skipped on this path (cache was updated but lacks the connector),
                    // so the outer ERROR catch must not fire.
                    () -> assertFalse(hasLog(listAppender, Level.ERROR, "Failed to get connector metric"),
                            "No ERROR log expected when a connector is permanently removed"));
        });
    }
}
