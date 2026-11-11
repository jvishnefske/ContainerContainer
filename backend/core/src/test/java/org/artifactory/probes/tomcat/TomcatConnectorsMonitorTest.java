package org.artifactory.probes.tomcat;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import java.lang.management.ManagementFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author Igor Usenko
 */
class TomcatConnectorsMonitorTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String CONNECTOR_NAME = "connector-name";
    private static final String MAX_THREADS = "5";
    private static final String CURRENT_THREADS_BUSY = "3";
    private static final String REQUEST_COUNT = "42";

    MockedStatic<ManagementFactory> managementFactoryMockedStatic;

    private TomcatConnectorsMonitorForTest monitor;

    @BeforeEach
    void setUp() {
        managementFactoryMockedStatic = mockStatic(ManagementFactory.class);
        MBeanServer mBeanServer = mock(MBeanServer.class);
        managementFactoryMockedStatic.when(ManagementFactory::getPlatformMBeanServer).thenReturn(mBeanServer);
        ObjectName connectorMock = mock(ObjectName.class);
        when(mBeanServer.queryNames(any(ObjectName.class), any())).thenReturn(Set.of(connectorMock));
        when(connectorMock.getKeyProperty("port")).thenReturn("8081");
        when(connectorMock.getKeyProperty("address")).thenReturn("\"127.0.0.1\"");

        TestProxy proxy = new TestProxy();
        monitor = new TomcatConnectorsMonitorForTest(proxy);
        monitor.init();
    }

    @AfterEach
    void tearDown() {
        managementFactoryMockedStatic.close();
    }

    @Test
    void getConnectorNames() {
        Set<String> names = monitor.getConnectorNames();
        Assertions.assertEquals(Set.of(CONNECTOR_NAME), names);
    }

    @Test
    void getConnectorMetrics() {
        ConnectorMetric metric = monitor.getConnectorMetric(CONNECTOR_NAME).get();
        assertEquals(new ConnectorMetric("connector-name", 5, 3, 42, 5, 5), metric);
    }

    @Test
    void forSonar() throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        DisabledReadinessMonitor disabledReadinessMonitor = new DisabledReadinessMonitor("off");
        disabledReadinessMonitor.isReady();
        disabledReadinessMonitor.postConstruct();

        NoopMBeanServer noopMBeanServer = new NoopMBeanServer();
        noopMBeanServer.getAttribute(null, null);
        noopMBeanServer.queryMBeans(null, null);
        noopMBeanServer.queryNames(null, null);
    }

    @Test
    void whenExceptions() {
        TestProxy proxy = new TestProxy(true);
        TomcatConnectorsMonitorForTest monitor = new TomcatConnectorsMonitorForTest(proxy);
        monitor.init();
        Assertions.assertTrue(monitor.getConnectorNames().isEmpty());
        Assertions.assertTrue(monitor.getConnectorMetric("name").isEmpty());
    }

    @Test
    void getMBeanServer() {
        Assertions.assertNotNull(monitor.getMBeanServer());
    }

    @Test
    void testInitSetsActiveConnectorsNames() throws Exception {
        // Use reflection to access private field
        java.lang.reflect.Field field = TomcatConnectorsMonitor.class.getDeclaredField("activeConnectorsNames");
        field.setAccessible(true);
        Set<String> activeNames = (Set<String>) field.get(monitor);
        Assertions.assertTrue(activeNames.contains("\"https-jsse-nio-8443\""));
        Assertions.assertTrue(activeNames.stream().anyMatch(s -> s.startsWith("\"http-nio-")));
    }

    @Test
    void forSonar_testGetActiveConnectorsMetrics() {
        TestProxy proxy = new TestProxy();
        TomcatConnectorsMonitorForTest monitor = new TomcatConnectorsMonitorForTest(proxy);
        monitor.init();
        Set<ConnectorMetric> metrics = monitor.getActiveConnectorsMetrics();
        Assertions.assertTrue(metrics.isEmpty());
    }

    @Test
    void findMBeanServerReturnsNoopOnException() {
        try (MockedStatic<MBeanServerFactory> mbeanFactoryStatic = mockStatic(MBeanServerFactory.class)) {
            mbeanFactoryStatic.when(() -> MBeanServerFactory.findMBeanServer(null))
                    .thenThrow(new RuntimeException("boom"));
            TomcatConnectorsMonitor monitor = new TomcatConnectorsMonitor();
            MBeanServerProxy server = monitor.getMBeanServer();
            Assertions.assertSame(NoopMBeanServer.NOOP_M_BEAN_SERVER, server);
        }
    }

    @Test
    void testGetConnectorMetric_findMBeanServerThrowsException_returnsEmptyOptional() {
        try (MockedStatic<MBeanServerFactory> mbeanFactoryStatic = mockStatic(MBeanServerFactory.class)) {
            mbeanFactoryStatic.when(() -> MBeanServerFactory.findMBeanServer(null))
                    .thenThrow(new RuntimeException("boom"));
            TomcatConnectorsMonitor monitor = spy(new TomcatConnectorsMonitor());
            monitor.init();
            when(monitor.getTomcatConnector(anyString())).thenReturn(mock(TomcatConnector.class));
            Assertions.assertTrue(monitor.getConnectorMetric("any").isEmpty());
        }
    }

    private static class TomcatConnectorsMonitorForTest extends TomcatConnectorsMonitor {

        private final TestProxy proxy;

        public TomcatConnectorsMonitorForTest(TestProxy proxy) {
            this.proxy = proxy;
        }

        @Override
        public MBeanServerProxy getMBeanServer() {
            return proxy;
        }
    }

    private static class TestProxy implements MBeanServerProxy {

        private final boolean throwException;

        public TestProxy() {
            this(false);
        }

        public TestProxy(boolean throwException) {
            this.throwException = throwException;
        }

        @Override
        public Object getAttribute(ObjectName name, String attribute) {
            if (throwException) {
                throw new RuntimeException();
            }
            String canonicalName = name.getCanonicalName();
            if (canonicalName.contains("ThreadPool")) {
                if (attribute.equals("maxThreads")) {
                    return MAX_THREADS;
                }
                if (attribute.equals("currentThreadsBusy")) {
                    return CURRENT_THREADS_BUSY;
                }
                if (attribute.equals("maxConnections")) {
                    return MAX_THREADS;
                }
                if (attribute.equals("connectionCount")) {
                    return MAX_THREADS;
                }
            }
            if (canonicalName.contains("GlobalRequestProcessor")) {

                if (attribute.equals("requestCount")) {
                    return REQUEST_COUNT;
                }
            }
            return null;
        }

        @Override
        public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
            if (throwException) {
                throw new RuntimeException();
            }
            return Set.of(mock(ObjectName.class));
        }

        @Override
        public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
            if (throwException) {
                throw new RuntimeException();
            }
            String canonicalName = name.getCanonicalName();
            if (canonicalName.contains("ThreadPool")) {
                ObjectInstance instance = createObjectInstanceMock("ThreadPool");
                return Set.of(instance);
            }
            if (canonicalName.contains("GlobalRequestProcessor")) {
                ObjectInstance instance = createObjectInstanceMock("GlobalRequestProcessor");
                return Set.of(instance);
            }
            return null;
        }

        private ObjectInstance createObjectInstanceMock(String canonicalName) {
            ObjectInstance instance = mock(ObjectInstance.class);
            ObjectName objectName = mock(ObjectName.class);
            when(objectName.getKeyProperty("name")).thenReturn(CONNECTOR_NAME);
            when(instance.getObjectName()).thenReturn(objectName);
            when(objectName.getCanonicalName()).thenReturn(canonicalName);
            return instance;
        }
    }
}

