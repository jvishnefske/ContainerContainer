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

import com.jfrog.sysconf.SysConfig;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.TestUtils;
import org.jfrog.common.TomcatUtilsTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.management.Attribute;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.util.TomcatListeningDetector.SYS_ARTIFACTORY_ENTRY_POINTS_INTERNAL_PORT;
import static org.artifactory.util.TomcatListeningDetector.SYS_ARTIFACTORY_PORT;
import static org.artifactory.util.TomcatListeningDetector.SYS_MAINTENANCE_ARTIFACTORY_PORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TomcatListeningDetector}.
 *
 * @author Yossi Shaul
 */
public class TomcatListeningDetectorTest extends ArtifactoryHomeBoundJUnitTest {

    private static final int NO_PORT_FOUND = -1;

    @BeforeEach
    public void beforeEach() {
        System.clearProperty(SYS_ARTIFACTORY_PORT);
        System.clearProperty(SYS_MAINTENANCE_ARTIFACTORY_PORT);
        System.clearProperty(SYS_ARTIFACTORY_ENTRY_POINTS_INTERNAL_PORT);
    }

    @Test
    void testGetMaintenancePortDefaultValue() {
        int port = TomcatListeningDetector.getMaintenancePort();
        assertEquals(8091, port);
    }

    @Test
    void testGetMaintenancePortBySystemConfig() {
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);

        when(sysConfig.getInt(eq(SYS_MAINTENANCE_ARTIFACTORY_PORT), anyInt()))
                .thenReturn(2222);

        int port = TomcatListeningDetector.getMaintenancePort();
        assertEquals(2222, port);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }

    @Test
    void testGetMaintenancePortByBadSystemProperty() {
        System.setProperty(SYS_MAINTENANCE_ARTIFACTORY_PORT, "not_int");
        assertEquals(8091, TomcatListeningDetector.getMaintenancePort());
    }

    @Test
    void testGetPortByBadSystemProperty() {
        System.setProperty(SYS_ARTIFACTORY_PORT, "not_int");
        assertEquals(NO_PORT_FOUND, TomcatListeningDetector.detectMainListeningPort());
    }

    @Test
    void detectMainListeningPortWithoutAnyPortDataAvailable() {
        assertEquals(NO_PORT_FOUND, TomcatListeningDetector.detectMainListeningPort());
    }

    @Test
    void detectMainListeningPortBySystemProperty() {
        System.setProperty(SYS_ARTIFACTORY_PORT, "8787");
        assertEquals(8787, TomcatListeningDetector.detectMainListeningPort());
    }

    @Test
    void detectMainListeningPortByFakingTomcatHttpsConnector() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName https = new ObjectName("Tomcat:type=Connector,port=9043");
        try {
            mbs.registerMBean(new TomcatUtilsTest.DummyTomcatConnector(), https);
            assertEquals(9043, TomcatListeningDetector.detectMainListeningPort(), "Wrong tomcat port detected");
        } finally {
            if (mbs.isRegistered(https)) {
                mbs.unregisterMBean(https);
            }
        }
    }

    @Test
    void detectMainListeningPortByFakingTomcatConnector()
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException,
            MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("Tomcat:type=Connector,port=8081");
        try {
            mbs.registerMBean(new TomcatUtilsTest.DummyTomcatConnector(), name);
            assertEquals(8081, TomcatListeningDetector.detectMainListeningPort(), "Wrong tomcat port detected");
        } finally {
            if (mbs.isRegistered(name)) {
                mbs.unregisterMBean(name);
            }
        }
    }

    @Test
    void testDetectMainListeningPortOnlyMaintenanceConnectorFoundThrowsException() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName httpsConnector = new ObjectName("Tomcat:type=Connector,port=8091");
        try {
            mbs.registerMBean(new TomcatUtilsTest.DummyTomcatConnector(), httpsConnector);
            assertThrowsExactly(IllegalStateException.class, TomcatListeningDetector::detectMainListeningPort,
                    "No main connector found");
        } finally {
            if (mbs.isRegistered(httpsConnector)) {
                mbs.unregisterMBean(httpsConnector);
            }
        }
    }

    @Test
    void testDetectMainListeningPortNoMBeans() {
        try (MockedStatic<ManagementFactory> managementFactory = mockStatic(ManagementFactory.class)) {
            MBeanServer mBeanServer = mock(MBeanServer.class);
            when(mBeanServer.queryMBeans(any(), any())).thenReturn(Set.of());
            managementFactory.when(ManagementFactory::getPlatformMBeanServer).thenReturn(mBeanServer);
            assertEquals(NO_PORT_FOUND, TomcatListeningDetector.detectMainListeningPort());
        }
    }

    @Test
    void testDetectMainListeningPortBySystemYaml() {
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        try {
            SysConfig sysConfig = mock(SysConfig.class);
            TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);

            when(sysConfig.get(SYS_ARTIFACTORY_PORT)).thenReturn(Optional.of("9090"));

            assertEquals(9090, TomcatListeningDetector.detectMainListeningPort());
        } finally {
            TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
        }
    }

    @Test
    void testDetectMainListeningEndpointSuccess() {
        int port = 8081;
        String address = "127.0.0.1";
        ObjectName mockConnector = mock(ObjectName.class);
        when(mockConnector.getKeyProperty("address")).thenReturn("\"" + address + "\"");
        when(mockConnector.getKeyProperty("port")).thenReturn(String.valueOf(port));

        try (MockedStatic<TomcatListeningDetector> detectorMock = mockStatic(TomcatListeningDetector.class,
                CALLS_REAL_METHODS)) {
            detectorMock.when(TomcatListeningDetector::detectMainListeningPort).thenReturn(port);
            detectorMock.when(() -> TomcatListeningDetector.getMainConnector(port)).thenReturn(mockConnector);

            String endpoint = TomcatListeningDetector.detectMainListeningEndpoint();
            assertEquals("http-nio-127.0.0.1-8081", endpoint);
        }
    }

    @Test
    void testDetectMainListeningPointNoAddress() {
        int port = 8081;
        ObjectName mockConnector = mock(ObjectName.class);
        when(mockConnector.getKeyProperty("address")).thenReturn(null);
        when(mockConnector.getKeyProperty("port")).thenReturn(String.valueOf(port));

        try (MockedStatic<TomcatListeningDetector> detectorMock = mockStatic(TomcatListeningDetector.class,
                CALLS_REAL_METHODS)) {
            detectorMock.when(TomcatListeningDetector::detectMainListeningPort).thenReturn(port);
            detectorMock.when(() -> TomcatListeningDetector.getMainConnector(port)).thenReturn(mockConnector);

            String endpoint = TomcatListeningDetector.detectMainListeningEndpoint();
            assertEquals("http-nio-127.0.0.1-8081", endpoint);
        }
    }

    @Test
    void testDetectMainListeningEndpointConnectorNotFound() {
        int port = 8081;
        try (MockedStatic<TomcatListeningDetector> detectorMock = mockStatic(TomcatListeningDetector.class,
                CALLS_REAL_METHODS)) {
            detectorMock.when(TomcatListeningDetector::detectMainListeningPort).thenReturn(port);
            detectorMock.when(() -> TomcatListeningDetector.getMainConnector(port)).thenReturn(null);

            IllegalStateException ex =
                    assertThrows(IllegalStateException.class, TomcatListeningDetector::detectMainListeningEndpoint);
            assertTrue(ex.getMessage().contains("main connector not found"));
        }
    }

    @Test
    void detectHttpsListeningPortReturnsEmptyWhenNoConnectorsRegistered() {
        Optional<Integer> port = TomcatListeningDetector.detectHttpsListeningPort();
        assertFalse(port.isPresent(), "Expected empty when no Tomcat connectors are registered");
    }

    @Test
    void detectHttpsListeningPortReturnsPortWhenHttpsConnectorPresent() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("Tomcat:type=Connector,port=8443");
        try {
            mbs.registerMBean(new TomcatUtilsTest.DummyTomcatConnector(), name);
            mbs.setAttribute(name, new Attribute("scheme", "https"));

            Optional<Integer> port = TomcatListeningDetector.detectHttpsListeningPort();

            assertTrue(port.isPresent(), "Expected HTTPS port to be detected");
            assertEquals(8443, port.get());
        } finally {
            if (mbs.isRegistered(name)) {
                mbs.unregisterMBean(name);
            }
        }
    }

    @Test
    void detectHttpsListeningPortDoesNotReturnHttpConnector() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("Tomcat:type=Connector,port=8081");
        try {
            mbs.registerMBean(new TomcatUtilsTest.DummyTomcatConnector(), name);
            // DummyTomcatConnector defaults to scheme="http" — no explicit set needed

            Optional<Integer> port = TomcatListeningDetector.detectHttpsListeningPort();

            assertFalse(port.isPresent(), "HTTP connector must not be returned by detectHttpsListeningPort");
        } finally {
            if (mbs.isRegistered(name)) {
                mbs.unregisterMBean(name);
            }
        }
    }

    @Test
    void detectHttpsListeningPortReturnsMostRelevantWhenBothConnectorsPresent() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName http = new ObjectName("Tomcat:type=Connector,port=8081");
        ObjectName https = new ObjectName("Tomcat:type=Connector,port=8443");
        try {
            mbs.registerMBean(new TomcatUtilsTest.DummyTomcatConnector(), http);
            // http connector defaults to scheme="http"

            mbs.registerMBean(new TomcatUtilsTest.DummyTomcatConnector(), https);
            mbs.setAttribute(https, new Attribute("scheme", "https"));

            Optional<Integer> port = TomcatListeningDetector.detectHttpsListeningPort();

            assertTrue(port.isPresent(), "Expected HTTPS port to be detected");
            assertEquals(8443, port.get());
        } finally {
            if (mbs.isRegistered(http)) {
                mbs.unregisterMBean(http);
            }
            if (mbs.isRegistered(https)) {
                mbs.unregisterMBean(https);
            }
        }
    }

    @Test
    void detectHttpsListeningPortReturnsEmptyWhenMBeanServerQueryFails() {
        try (MockedStatic<ManagementFactory> managementFactory = mockStatic(ManagementFactory.class)) {
            MBeanServer mBeanServer = mock(MBeanServer.class);
            when(mBeanServer.queryNames(any(), any())).thenThrow(new RuntimeException("JMX failure"));
            managementFactory.when(ManagementFactory::getPlatformMBeanServer).thenReturn(mBeanServer);

            Optional<Integer> port = TomcatListeningDetector.detectHttpsListeningPort();

            assertFalse(port.isPresent(), "Expected empty when MBeanServer query fails");
        }
    }
}