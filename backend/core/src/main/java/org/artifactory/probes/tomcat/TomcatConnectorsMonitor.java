package org.artifactory.probes.tomcat;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnExpression;
import org.artifactory.util.TomcatListeningDetector;
import org.springframework.stereotype.Component;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;
import static org.artifactory.probes.tomcat.ConnectorsMonitor.CONNECTORS_MONITOR_ENABLED_EXPRESSION;
import static org.artifactory.probes.tomcat.NoopMBeanServer.NOOP_M_BEAN_SERVER;

/**
 * @author Igor Usenko
 */
@Slf4j
@Component
@ConditionalOnExpression(CONNECTORS_MONITOR_ENABLED_EXPRESSION)
public class TomcatConnectorsMonitor implements ConnectorsMonitor {

    private static final String HTTPS_JSSE_NIO_NAME = "\"https-jsse-nio-8443\"";
    private static final String MAIN_LISTENING_ENDPOINT_TEMPLATE = "\"%s\"";
    private static final String MBEAN_ANY_DOMAIN = "*";
    private Set<String> activeConnectorsNames;

    @Getter(lazy = true)
    private final MBeanServerProxy mBeanServer = findMBeanServer();
    private final AtomicReference<Map<String, TomcatConnector>> connectorsRef = new AtomicReference<>();
    private final ReadWriteLock connectorsLock = new ReentrantReadWriteLock();

    @PostConstruct
    public void init() {
        activeConnectorsNames = Set.of(getMainListeningEndpoint(), HTTPS_JSSE_NIO_NAME);
        connectorsRef.set(findConnectors());
    }

    @Override
    public Set<String> getConnectorNames() {
        connectorsLock.readLock().lock();
        try {
            return connectorsRef.get().keySet();
        } finally {
            connectorsLock.readLock().unlock();
        }
    }

    @Override
    public Optional<ConnectorMetric> getConnectorMetric(String connectorName) {
        TomcatConnector connector = getTomcatConnector(connectorName);
        if (connector == null) {
            return Optional.empty();
        }
        try {
            MBeanServerProxy server = getMBeanServer();
            ConnectorMetric.ConnectorMetricBuilder builder = ConnectorMetric.builder();
            boolean populated = findAndPopulateAttributes(server, connector, builder, connectorName);
            if (!populated) {
                return Optional.empty();
            }
            builder.name(connector.name());
            builder.maxConnections(connector.maxConnections());
            builder.maxThreads(connector.maxThreads());
            return Optional.of(builder.build());
        } catch (Exception e) {
            log.error("Failed to get connector metric for connector: {}", connectorName, e);
            return Optional.empty();
        }
    }

    private boolean findAndPopulateAttributes(MBeanServerProxy server, TomcatConnector connector,
            ConnectorMetric.ConnectorMetricBuilder builder, String connectorName)
            throws ReflectionException, AttributeNotFoundException, MBeanException {
        try {
            findAndPopulateAttributes(server, connector, builder);
        } catch (InstanceNotFoundException _) {
            // The MBean vanished mid-poll (transient JMX window). Re-discover and, at most, retry once.
            // Each outcome logs a single WARN without a stack trace: an InstanceNotFoundException here is a
            // lookup miss during a Tomcat lifecycle event, not a code fault, and this path can repeat on
            // every poll during a sustained window, so a stack trace per poll would only add log noise.
            boolean cacheUpdated = reDiscoverConnectors();
            if (!cacheUpdated) {
                log.warn("MBean for connector '{}' is missing and re-discovery found no connectors; "
                        + "keeping cached connectors and will retry on next poll", connectorName);
                return false;
            }
            TomcatConnector rediscoveredConnector = getTomcatConnector(connectorName);
            if (rediscoveredConnector == null) {
                log.warn("MBean for connector '{}' is missing and the connector was not found after "
                        + "re-discovery; it may have been removed", connectorName);
                return false;
            }
            try {
                findAndPopulateAttributes(server, rediscoveredConnector, builder);
            } catch (InstanceNotFoundException _) {
                log.warn("MBean for connector '{}' is missing and disappeared again immediately after "
                        + "re-discovery; will retry on next poll", connectorName);
                return false;
            }
        }
        return true;
    }

    @Override
    public Set<ConnectorMetric> getActiveConnectorsMetrics() {
        return activeConnectorsNames.stream().map(this::getConnectorMetric)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    @VisibleForTesting
    TomcatConnector getTomcatConnector(String connectorName) {
        TomcatConnector connector;
        connectorsLock.readLock().lock();
        try {
            connector = connectorsRef.get().get(connectorName);
        } finally {
            connectorsLock.readLock().unlock();
        }
        return connector;
    }

    private static void findAndPopulateAttributes(MBeanServerProxy server, TomcatConnector connector,
            ConnectorMetric.ConnectorMetricBuilder builder)
            throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        builder.currentThreadsBusy(getAttributeFromThreadPool(server, connector, "currentThreadsBusy"));
        builder.currentConnections(getAttributeFromThreadPool(server, connector, "connectionCount"));
        builder.processedCount(getAttributeFromRequestProcessor(server, connector, "requestCount"));
    }

    private static long getAttributeFromRequestProcessor(MBeanServerProxy server, TomcatConnector connector,
            String attributeName)
            throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        return toLong(server.getAttribute(connector.requestProcessor(), attributeName));
    }

    private static long getAttributeFromThreadPool(MBeanServerProxy server, TomcatConnector connector,
            String attributeName)
            throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        return toLong(server.getAttribute(connector.threadPool(), attributeName));
    }

    @VisibleForTesting
    boolean reDiscoverConnectors() {
        Map<String, TomcatConnector> connectorsBackup = connectorsRef.get();
        connectorsLock.writeLock().lock();
        try {
            log.info("Re-discovering Tomcat connectors due to MBean unregistration");
            if (connectorsBackup != connectorsRef.get()) {
                log.info("Connectors were already re-discovered by another thread while waiting for write lock");
                // true means the cache is fresh, not that this connector is present;
                // the caller's null check on the re-fetched connector is the backstop.
                return true;
            }
            Map<String, TomcatConnector> rediscoveredConnectors = findConnectors(false);
            if (rediscoveredConnectors.isEmpty() && !connectorsBackup.isEmpty()) {
                log.warn("Tomcat connectors were not found during re-discovery. "
                        + "Keeping previous connector cache with {} connectors",
                        connectorsBackup.size());
                return false;
            }
            connectorsRef.set(rediscoveredConnectors);
            log.info("Re-discovery complete. Found {} connectors", connectorsRef.get().size());
            return true;
        } finally {
            connectorsLock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    MBeanServerProxy findMBeanServer() {
        try {
            List<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(null);
            log.info("Found {} mbean servers", mBeanServers.size());
            MBeanServer server =
                    !mBeanServers.isEmpty() ? mBeanServers.get(0) : ManagementFactory.getPlatformMBeanServer();
            log.info("MBeanServer found. List of domains {}", (Object) server.getDomains());
            return new TomcatMBeanServer(server);
        } catch (Exception e) {
            log.warn("Failed to find MBeanServer", e);
            return NOOP_M_BEAN_SERVER;
        }
    }

    private static String getMainListeningEndpoint() {
        return MAIN_LISTENING_ENDPOINT_TEMPLATE.formatted(TomcatListeningDetector.detectMainListeningEndpoint());
    }

    @VisibleForTesting
    Map<String, TomcatConnector> findConnectors() {
        return findConnectors(true);
    }

    private Map<String, TomcatConnector> findConnectors(boolean logErrorOnFailure) {
        try {
            String domain = getJmxDomain();
            Map<String, ObjectName> threadPoolNames = queryMBeans(domain, "ThreadPool");
            Map<String, ObjectName> requestProcessorNames = queryMBeans(domain, "GlobalRequestProcessor");
            Set<String> names = getNames(threadPoolNames, requestProcessorNames);
            log.info("Found Tomcat connectors {}", names);
            return findConnectors(names, threadPoolNames, requestProcessorNames);
        } catch (Exception e) {
            if (logErrorOnFailure) {
                log.error("Failed to find Tomcat connectors", e);
            } else {
                log.warn("Tomcat connectors were temporarily unavailable during re-discovery", e);
            }
            return Collections.emptyMap();
        }
    }

    private Map<String, ObjectName> queryMBeans(String domain, String type)
            throws MalformedObjectNameException {
        String pattern = domain + ":type=" + type + ",name=*";
        return getMBeanServer().queryMBeans(new ObjectName(pattern), null)
                .stream()
                .map(ObjectInstance::getObjectName)
                .collect(Collectors.toMap(o -> o.getKeyProperty("name"), o -> o));
    }

    private Map<String, TomcatConnector> findConnectors(Set<String> names,
            Map<String, ObjectName> threadPoolNames, Map<String, ObjectName> requestProcessorNames)
            throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        Map<String, TomcatConnector> connectorsFound = new HashMap<>();

        for (String name : names) {
            ObjectName threadPoolName = threadPoolNames.get(name);
            ObjectName requestProcessorName = requestProcessorNames.get(name);
            long maxThreads = toLong(getMBeanServer().getAttribute(threadPoolName, "maxThreads"));
            long maxConnections = toLong(getMBeanServer().getAttribute(threadPoolName, "maxConnections"));
            TomcatConnector connector = TomcatConnector.builder()
                    .name(name)
                    .maxThreads(maxThreads)
                    .maxConnections(maxConnections)
                    .threadPool(threadPoolName)
                    .requestProcessor(requestProcessorName)
                    .build();
            log.info("Adding Tomcat connector to monitor: {}", connector);
            connectorsFound.put(name, connector);
        }

        return connectorsFound;
    }

    private String getJmxDomain() throws MalformedObjectNameException {
        if (hasObjectName("Tomcat:type=Server")) {
            return "Tomcat";
        }
        if (hasObjectName("Catalina:type=Server")) {
            return "Catalina";
        }
        return MBEAN_ANY_DOMAIN;
    }

    private boolean hasObjectName(String name) throws MalformedObjectNameException {
        return getMBeanServer().queryNames(new ObjectName(name), null).size() == 1;
    }

    private static Set<String> getNames(Map<String, ObjectName> threadPoolNames,
            Map<String, ObjectName> requestProcessorNames) {
        Set<String> threadPoolNamesSet = threadPoolNames.keySet();
        Set<String> requestProcessorNamesSet = requestProcessorNames.keySet();

        if (threadPoolNamesSet.isEmpty()) {
            throw new IllegalStateException("No Tomcat thread pools found");
        }

        if (requestProcessorNamesSet.isEmpty()) {
            throw new IllegalStateException("No Tomcat request processors found");
        }

        if (!threadPoolNamesSet.equals(requestProcessorNamesSet)) {
            String message = String.format("Difference between thread pools and request processors lists. " +
                    "Thread pools: %s. Request processors: %s", threadPoolNamesSet, requestProcessorNamesSet);
            throw new IllegalStateException(message);
        }

        return threadPoolNamesSet;
    }

    private static long toLong(Object value) {
        return parseLong(value.toString());
    }
}