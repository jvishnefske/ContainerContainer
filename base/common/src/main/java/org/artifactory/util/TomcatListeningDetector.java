/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.util;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.sysconf.SysConfig;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class to detect the listening port of Artifactory.
 * Prioritizes configuration (System var), then {@link SysConfig}, and finally attempts to auto-detect from JMX
 *
 * @author Mor Merhav
 */
@Slf4j
@UtilityClass
public class TomcatListeningDetector {

    public static final String SYS_ARTIFACTORY_PORT = "artifactory.port";
    public static final String SYS_ARTIFACTORY_ENTRY_POINTS_INTERNAL_PORT = "artifactory.entrypoints.internalPort";
    public static final String SYS_MAINTENANCE_ARTIFACTORY_PORT = "artifactory.tomcat.maintenanceConnector.port";
    private static final String HTTP_NIO_CONNECTOR_ENDPOINT_TEMPLATE = "http-nio-%s-%d";
    private static final int ARTIFACTORY_PREFERRED_PORT = 8081;
    private static final int ARTIFACTORY_MAINTENANCE_PREFERRED_PORT = 8091;
    private static final String PROPERTY_KEY_PORT = "port";
    private static final String PROPERTY_KEY_ADDRESS = "address";
    private static final String DEFAULT_ADDRESS = "127.0.0.1";
    private static final String QUERY_MBEANS_ALL = "*:*";
    private static final String QUERY_MBEANS_CONNECTORS = "*:type=Connector,*";
    private static final int NO_LISTENING_PORT = -1;
    private static final String HTTPS_SCHEME = "https";
    private static final String SCHEME_ATTRIBUTE = "scheme";

    /**
     * @return The detected http port or -1 if not found
     */
    public static int detectMainListeningPort() {
        return detectMainPortFromSystemProperty().or(TomcatListeningDetector::detectMainPortFromSysConfig)
                .orElseGet(TomcatListeningDetector::detectMainPortFromTomcatMBean);
    }

    /**
     * Detects the HTTPS connector port by querying Tomcat's JMX Connector MBeans and finding the first
     * one whose {@code scheme} attribute is {@code "https"}.
     *
     * @return the detected HTTPS port, or empty if no HTTPS connector is configured
     */
    public static Optional<Integer> detectHttpsListeningPort() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            Set<ObjectName> connectors = mbs.queryNames(new ObjectName(QUERY_MBEANS_CONNECTORS), null);
            return connectors.stream()
                    .filter(c -> isHttpsConnector(mbs, c))
                    .map(TomcatListeningDetector::getPort)
                    .findFirst();
        } catch (Exception e) {
            log.warn("Failed to detect HTTPS connector port from JMX: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isHttpsConnector(MBeanServer mbs, ObjectName connector) {
        try {
            return HTTPS_SCHEME.equals(mbs.getAttribute(connector, SCHEME_ATTRIBUTE));
        } catch (Exception e) {
            log.debug("Failed to read '{}' attribute for connector {}: {}", SCHEME_ATTRIBUTE, connector,
                    e.getMessage());
            return false;
        }
    }

    /**
     * @return the detected main listening endpoint
     * @throws IllegalStateException if ArtifactoryHome is not initialized or main connector not found
     */
    @SneakyThrows(MalformedObjectNameException.class)
    public static String detectMainListeningEndpoint() {
        int mainListeningPort = detectMainListeningPort();
        ObjectName mainConnector = getMainConnector(mainListeningPort);
        if (mainConnector == null) {
            String errMsg = "Cannot determine main listening endpoint, main connector not found";
            log.error(errMsg);
            throw new IllegalStateException(errMsg);
        }
        String address = mainConnector.getKeyProperty(PROPERTY_KEY_ADDRESS);
        address = address != null ? address.replace("\"", "") : DEFAULT_ADDRESS;
        return HTTP_NIO_CONNECTOR_ENDPOINT_TEMPLATE.formatted(address, mainListeningPort);
    }

    /**
     *  This method is needed even though {@link SysConfig} does draw from System properties, because it assumes they
     *  always have a jf. prefix, and that's how it loads them.
     *  Meaning, 'artifactory.port' and 'artifactory.tomcat.maintenanceConnector.port' would fail to be read.
      * @return Optional containing the port if detected from system property, otherwise empty
     */
    private static Optional<Integer> detectMainPortFromSystemProperty() {
        return Optional.ofNullable(System.getProperty(SYS_ARTIFACTORY_PORT))
                .flatMap(port -> parsePort(port, SYS_ARTIFACTORY_PORT));
    }

    private static Optional<Integer> detectMainPortFromSysConfig() {
        SysConfig sysConfig = ArtifactoryHome.get().getSysConfig();
        return sysConfig.get(SYS_ARTIFACTORY_ENTRY_POINTS_INTERNAL_PORT)
                .flatMap(port -> parsePort(port, SYS_ARTIFACTORY_ENTRY_POINTS_INTERNAL_PORT))
                .or(() -> sysConfig.get(SYS_ARTIFACTORY_PORT)
                .flatMap(port -> parsePort(port, SYS_ARTIFACTORY_PORT)));
    }

    private static Optional<Integer> parsePort(String portStr, String propertyKey) {
        try {
            return Optional.of(Integer.parseInt(portStr));
        } catch (NumberFormatException e) {
            log.warn("Invalid port value: '{}' from key: '{}'", portStr,propertyKey);
            return Optional.empty();
        }
    }

    /**
     * @return Configured maintenance port, or ARTIFACTORY_MAINTENANCE_PREFERRED_PORT as default
     * @throws IllegalStateException if ArtifactoryHome is not initialized
     */
    public static int getMaintenancePort() {
        return ArtifactoryHome.get().getSysConfig()
                .getInt(SYS_MAINTENANCE_ARTIFACTORY_PORT, ARTIFACTORY_MAINTENANCE_PREFERRED_PORT);
    }

    @SneakyThrows
    private static int detectMainPortFromTomcatMBean() {
        ObjectName mainConnector = getMainConnector();
        if (mainConnector == null) {
            return NO_LISTENING_PORT;
        }
        return getPort(mainConnector);
    }

    /**
     * Gets the main Tomcat connector MBean based on {@link #ARTIFACTORY_PREFERRED_PORT} by querying the platform
     * MBean server.
     * @param port the port by which to identify the main connector
     * @return the ObjectName of the main connector, or null if not found
     * @throws MalformedObjectNameException if the ObjectName is malformed
     */
    @VisibleForTesting
    static ObjectName getMainConnector(int port) throws MalformedObjectNameException {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> allMbeans =
                mbs.queryNames(new ObjectName(QUERY_MBEANS_ALL), null);
        if (allMbeans.isEmpty()) {
            log.error("No MBeans found");
            return null;
        }
        Set<ObjectName> connectors = mbs.queryNames(new ObjectName(QUERY_MBEANS_CONNECTORS), null);
        if (connectors.isEmpty()) {
            log.error("No Tomcat connectors found");
            return null;
        }
        log.info("Tomcat connectors: {}", connectors);
        ObjectName mainConnector = findMainConnector(connectors, port);
        log.info("Found Tomcat main connector: {}", mainConnector);
        return mainConnector;
    }

    private static ObjectName getMainConnector() throws MalformedObjectNameException {
        return getMainConnector(ARTIFACTORY_PREFERRED_PORT);
    }

    /**
     * Finds the main connector from the set of connectors based on the given port, or falling back by scanning for
     * the first non-maintenance connector if not found.
     * @param connectors all available connectors
     * @param port the port by which to identify the main connector
     * @return the ObjectName of the main connector
     * @throws IllegalStateException if no main connector is found
     */
    private static ObjectName findMainConnector(Set<ObjectName> connectors, int port) {
        Optional<ObjectName> httpConnector = connectors.stream()
                .filter(c -> getPort(c) == port)
                .findFirst();
        if (httpConnector.isEmpty()) {
            log.warn("HTTP connector with preferred port: {} not found, looking for first connector " +
                            "only by not equals to the maintenance port: {}",
                    port, ARTIFACTORY_MAINTENANCE_PREFERRED_PORT);
            httpConnector = connectors.stream()
                    .filter(c -> getPort(c) != ARTIFACTORY_MAINTENANCE_PREFERRED_PORT)
                    .findFirst();
        }
        return httpConnector.orElseThrow(() -> {
            log.error("No main connector found");
            return new IllegalStateException("No main connector found");
        });
    }

    private static int getPort(ObjectName mainConnector) {
        return Integer.parseInt(mainConnector.getKeyProperty(PROPERTY_KEY_PORT));
    }
}