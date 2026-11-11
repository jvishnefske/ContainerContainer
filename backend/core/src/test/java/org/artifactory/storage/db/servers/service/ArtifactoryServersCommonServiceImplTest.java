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

package org.artifactory.storage.db.servers.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.codec.digest.DigestUtils;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterManager;
import org.artifactory.lifecycle.storage.db.servers.service.ArtifactoryServersCommonServiceImpl;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.spring.SpringConfigPaths;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.version.VersionProvider;
import org.jfrog.common.logging.logback.servlet.LogbackConfigManager;
import org.jfrog.config.ifc.ConfigurationManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.artifactory.addon.ArtifactoryRunningMode.HA;
import static org.artifactory.state.ArtifactoryServerState.CONVERTING;
import static org.artifactory.state.ArtifactoryServerState.RUNNING;
import static org.artifactory.state.ArtifactoryServerState.STOPPING;
import static org.artifactory.storage.db.servers.model.ArtifactoryServerRole.MEMBER;
import static org.artifactory.storage.db.servers.model.ArtifactoryServerRole.TASK_AFFINITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Shay Bagants
 */
class ArtifactoryServersCommonServiceImplTest {

    private ArtifactoryServersCommonServiceImpl serversCommonService;
    private ArtifactoryServersService serversService;
    private ApplicationEventPublisher applicationEventPublisher;
    private final String art1 = "art-01";
    private final String art2 = "art-02";
    private Cache<String, ArtifactoryServer> serversCache;
    private ArtifactoryServer server;
    private List<ArtifactoryServer> servers;

    @BeforeAll
    public static void beforeAll() {
        ArtifactoryHome.bind(new ArtifactoryHomeStub(ArtifactoryServersCommonServiceImplTest.class.getName()));
    }

    @AfterAll
    public static void afterAll() {
        ArtifactoryHome.unbind();
    }

    @BeforeEach
    public void beforeEach() {
        serversService = mock(ArtifactoryServersServiceImpl.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        serversCommonService = new ArtifactoryServersCommonServiceImpl(applicationEventPublisher, serversService);
        ArtifactoryContextThreadBinder.bind(new DummyArtifactoryContext());
    }

    @AfterEach
    public void afterEach() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCurrentMemberCache(boolean useCache) {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getArtifactoryServer(anyString())).thenReturn(server);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);

        if (useCache) {
            serversCache.put(server.getServerId(), server);
            assertEquals(1, serversCache.size());
        } else {
            assertEquals(0, serversCache.size());
        }

        ArtifactoryServer currentMember = ascsSpy.getCurrentMember();
        assertEquals(1, serversCache.size());
        assertSame(server, currentMember);
        verify(ascsSpy, times(1)).getCurrentMember();
        verify(ascsSpy, times(1)).getArtifactoryServer(anyString());
        verify(serversCacheSpy, times(1)).getIfPresent(anyString());

        if (useCache) {
            verify(serversService, times(0)).getArtifactoryServer(anyString());
            verify(serversCacheSpy, times(0)).put(any(), any());
        } else {
            verify(serversService, times(1)).getArtifactoryServer(anyString());
            verify(serversCacheSpy, times(1)).put(any(), any());
        }
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testCurrentMemberCacheExpired() {
        initSomeDataForServersCache(0);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getArtifactoryServer(anyString())).thenReturn(server);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        serversCache.put(server.getServerId(), server);
        assertEquals(0, serversCache.size());

        ArtifactoryServer currentMember = ascsSpy.getCurrentMember();
        assertEquals(0, serversCache.size());
        assertSame(server, currentMember);
        verify(ascsSpy, times(1)).getCurrentMember();
        verify(ascsSpy, times(1)).getArtifactoryServer(anyString());
        verify(serversService, times(1)).getArtifactoryServer(anyString());
        verify(serversCacheSpy, times(1)).getIfPresent(anyString());
        verify(serversCacheSpy, times(1)).put(any(), any());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAllArtifactoryServersCache(boolean fillCache) {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getAllArtifactoryServers()).thenReturn(servers);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);

        if (fillCache) {
            servers.forEach(s -> serversCache.put(s.getServerId(), s));
            assertEquals(2, serversCache.size());
        } else {
            assertEquals(0, serversCache.size());
        }

        List<ArtifactoryServer> allServers = ascsSpy.getAllArtifactoryServers();
        assertEquals(2, serversCache.size());
        assertEquals(new HashSet<>(servers), new HashSet<>(allServers));
        verify(ascsSpy, times(1)).getAllArtifactoryServers();
        verify(ascsSpy, times(1)).getAllArtifactoryServers(true);
        verify(serversCacheSpy, times(1)).asMap();

        if (fillCache) {
            verify(serversService, times(0)).getAllArtifactoryServers();
            verify(serversCacheSpy, times(0)).put(any(), any());
        } else {
            verify(serversService, times(1)).getAllArtifactoryServers();
            verify(serversCacheSpy, times(2)).put(any(), any());
            AtomicInteger serversCount = (AtomicInteger) ReflectionTestUtils.getField(ascsSpy, "serversCount");
            assertNotNull(serversCount);
            assertEquals(2, serversCount.get());
        }
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testAllArtifactoryServersDoNotUseCache() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getAllArtifactoryServers()).thenReturn(servers);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        ArtifactoryServer cachedServer1 = new ArtifactoryServer(art1, 1_000_000,
                "123.4.5.6", 5701, RUNNING, MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                HA, "COOL1");
        ArtifactoryServer cachedServer2 = new ArtifactoryServer(art2, 1_000_000,
                "123.4.5.7", 5701, RUNNING, MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                HA, "COOL2");
        List<ArtifactoryServer> cachedServers = List.of(cachedServer1, cachedServer2);
        cachedServers.forEach(s -> serversCache.put(s.getServerId(), s));
        assertEquals(2, serversCache.size());

        List<ArtifactoryServer> fromDbServers = ascsSpy.getAllArtifactoryServers(false);
        assertEquals(2, serversCache.size());
        assertSame(servers, fromDbServers);
        assertEquals(new HashSet<>(cachedServers), new HashSet<>(fromDbServers));
        verify(ascsSpy, times(1)).getAllArtifactoryServers(false);
        verify(serversService, times(1)).getAllArtifactoryServers();
        verify(serversCacheSpy, times(2)).put(any(), any());
        verify(serversCacheSpy, times(1)).asMap();
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAllArtifactoryServersCacheEmpty(boolean useCache) {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getAllArtifactoryServers()).thenReturn(servers);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        assertEquals(0, serversCache.size());

        List<ArtifactoryServer> allServers = ascsSpy.getAllArtifactoryServers();
        assertEquals(2, serversCache.size());
        assertSame(servers, allServers);
        verify(ascsSpy, times(1)).getAllArtifactoryServers();
        verify(ascsSpy, times(1)).getAllArtifactoryServers(true);
        verify(serversService, times(1)).getAllArtifactoryServers();
        verify(serversCacheSpy, times(2)).put(any(), any());
        verify(serversCacheSpy, times(1)).asMap();

        AtomicInteger serversCount = (AtomicInteger) ReflectionTestUtils.getField(ascsSpy, "serversCount");
        assertNotNull(serversCount);
        assertEquals(2, serversCount.get());
        serversCache.invalidateAll();

        allServers = ascsSpy.getAllArtifactoryServers(useCache);
        assertEquals(2, serversCache.size());
        assertSame(servers, allServers);
        verify(ascsSpy, times(useCache ? 2 : 1)).getAllArtifactoryServers(useCache);
        verify(serversService, times(2)).getAllArtifactoryServers();
        verify(serversCacheSpy, times(4)).put(any(), any());
        verify(serversCacheSpy, times(2)).asMap();

        serversCount = (AtomicInteger) ReflectionTestUtils.getField(ascsSpy, "serversCount");
        assertNotNull(serversCount);
        assertEquals(2, serversCount.get());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testAllArtifactoryServersCacheExpired() {
        initSomeDataForServersCache(0);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getAllArtifactoryServers()).thenReturn(servers);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        servers.forEach(s -> serversCache.put(s.getServerId(), s));
        assertEquals(0, serversCache.size());

        List<ArtifactoryServer> allServers = ascsSpy.getAllArtifactoryServers();
        assertEquals(0, serversCache.size());
        assertSame(servers, allServers);
        verify(ascsSpy, times(1)).getAllArtifactoryServers();
        verify(ascsSpy, times(1)).getAllArtifactoryServers(true);
        verify(serversService, times(1)).getAllArtifactoryServers();
        verify(serversCacheSpy, times(2)).put(any(), any());
        verify(serversCacheSpy, times(1)).asMap();

        AtomicInteger serversCount = (AtomicInteger) ReflectionTestUtils.getField(ascsSpy, "serversCount");
        assertNotNull(serversCount);
        assertEquals(2, serversCount.get());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetActiveMembers(boolean fillCache) {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getAllArtifactoryServers()).thenReturn(servers);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);

        if (fillCache) {
            servers.forEach(s -> serversCache.put(s.getServerId(), s));
            assertEquals(2, serversCache.size());
        } else {
            assertEquals(0, serversCache.size());
        }

        List<ArtifactoryServer> allServers = ascsSpy.getActiveMembers();
        assertEquals(2, serversCache.size());
        assertEquals(new HashSet<>(servers), new HashSet<>(allServers));
        verify(ascsSpy, times(1)).getActiveMembers();
        verify(ascsSpy, times(1)).getActiveMembers(true);
        verify(ascsSpy, times(1)).getAllArtifactoryServers(true);
        verify(serversCacheSpy, times(1)).asMap();

        if (fillCache) {
            verify(serversService, times(0)).getAllArtifactoryServers();
            verify(serversCacheSpy, times(0)).put(any(), any());
        } else {
            verify(serversService, times(1)).getAllArtifactoryServers();
            verify(serversCacheSpy, times(2)).put(any(), any());
            AtomicInteger serversCount = (AtomicInteger) ReflectionTestUtils.getField(ascsSpy, "serversCount");
            assertNotNull(serversCount);
            assertEquals(2, serversCount.get());
        }
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testGetActiveMembersDoNotUseCache() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        when(serversService.getAllArtifactoryServers()).thenReturn(servers);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        ArtifactoryServer cachedServer1 = new ArtifactoryServer(art1, 1_000_000,
                "123.4.5.6", 5701, RUNNING, MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                HA, "COOL1");
        ArtifactoryServer cachedServer2 = new ArtifactoryServer(art2, 1_000_000,
                "123.4.5.7", 5701, RUNNING, MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                HA, "COOL2");
        List<ArtifactoryServer> cachedServers = List.of(cachedServer1, cachedServer2);
        cachedServers.forEach(s -> serversCache.put(s.getServerId(), s));
        assertEquals(2, serversCache.size());

        List<ArtifactoryServer> fromDbServers = ascsSpy.getActiveMembers(false);
        assertEquals(servers, fromDbServers);
        assertEquals(new HashSet<>(cachedServers), new HashSet<>(fromDbServers));
        assertEquals(2, serversCache.size());
        verify(ascsSpy, times(1)).getActiveMembers(false);
        verify(ascsSpy, times(1)).getAllArtifactoryServers(false);
        verify(serversService, times(1)).getAllArtifactoryServers();
        verify(serversCacheSpy, times(1)).asMap();
        verify(serversCacheSpy, times(2)).put(any(), any());

        AtomicInteger serversCount = (AtomicInteger) ReflectionTestUtils.getField(ascsSpy, "serversCount");
        assertNotNull(serversCount);
        assertEquals(2, serversCount.get());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testUpdateArtifactoryServerRole() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        serversCache.put(server.getServerId(), server);
        assertEquals(1, serversCache.size());

        ascsSpy.updateArtifactoryServerRole(server.getServerId(), TASK_AFFINITY);
        assertEquals(1, serversCache.size());
        verify(ascsSpy, times(1)).updateArtifactoryServerRole(server.getServerId(), TASK_AFFINITY);
        verify(serversService, times(1)).updateArtifactoryServerRole(server.getServerId(), TASK_AFFINITY);
        verify(serversCacheSpy, times(1)).getIfPresent(server.getServerId());
        verify(serversCacheSpy, times(1)).put(server.getServerId(), server);

        ArtifactoryServer artifactoryServer = serversCache.getIfPresent(server.getServerId());
        assertNotNull(artifactoryServer);
        assertEquals(TASK_AFFINITY, artifactoryServer.getServerRole());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testUpdateArtifactoryJoinPort() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        serversCache.put(server.getServerId(), server);
        assertEquals(1, serversCache.size());

        ascsSpy.updateArtifactoryJoinPort(server.getServerId(), 42);
        assertEquals(1, serversCache.size());
        verify(ascsSpy, times(1)).updateArtifactoryJoinPort(server.getServerId(), 42);
        verify(serversService, times(1)).updateArtifactoryJoinPort(server.getServerId(), 42);
        verify(serversCacheSpy, times(1)).getIfPresent(server.getServerId());
        verify(serversCacheSpy, times(1)).put(server.getServerId(), server);

        ArtifactoryServer artifactoryServer = serversCache.getIfPresent(server.getServerId());
        assertNotNull(artifactoryServer);
        assertEquals(42, artifactoryServer.getMembershipPort());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testUpdateArtifactoryServerState() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        serversCache.put(server.getServerId(), server);
        assertEquals(1, serversCache.size());

        ascsSpy.updateArtifactoryServerState(server, STOPPING);
        assertEquals(1, serversCache.size());
        verify(ascsSpy, times(1)).updateArtifactoryServerState(server, STOPPING);
        verify(serversService, times(1)).updateArtifactoryServerState(server.getServerId(), STOPPING);
        verify(serversCacheSpy, times(1)).getIfPresent(server.getServerId());
        verify(serversCacheSpy, times(1)).put(server.getServerId(), server);

        ArtifactoryServer artifactoryServer = serversCache.getIfPresent(server.getServerId());
        assertNotNull(artifactoryServer);
        assertEquals(STOPPING, artifactoryServer.getServerState());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testCreateArtifactoryServer() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        assertEquals(0, serversCache.size());

        ascsSpy.createArtifactoryServer(server);
        assertEquals(1, serversCache.size());
        verify(ascsSpy, times(1)).createArtifactoryServer(server);
        verify(serversService, times(1)).createArtifactoryServer(server);
        verify(serversCacheSpy, times(1)).put(server.getServerId(), server);
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testUpdateArtifactoryServer() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        assertEquals(0, serversCache.size());

        ascsSpy.updateArtifactoryServer(server);
        assertEquals(1, serversCache.size());
        verify(ascsSpy, times(1)).updateArtifactoryServer(server);
        verify(serversService, times(1)).updateArtifactoryServer(server);
        verify(serversCacheSpy, times(1)).put(server.getServerId(), server);
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testRemoveArtifactoryServer() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        serversCache.put(server.getServerId(), server);
        assertEquals(1, serversCache.size());

        ascsSpy.removeServer(server.getServerId());
        assertEquals(0, serversCache.size());
        verify(ascsSpy, times(1)).removeServer(server.getServerId());
        verify(serversService, times(1)).removeServer(server.getServerId());
        verify(serversCacheSpy, times(1)).invalidate(server.getServerId());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    @Test
    void testUpdateArtifactoryServerHeartbeatCache() {
        initSomeDataForServersCache(1_000_000);
        Cache<String, ArtifactoryServer> serversCacheSpy = spy(serversCache);
        ArtifactoryServersCommonService ascsSpy = spy(serversCommonService);
        ReflectionTestUtils.setField(ascsSpy, "serversCache", serversCacheSpy);
        serversCache.put(server.getServerId(), server);
        assertEquals(1, serversCache.size());

        ascsSpy.updateArtifactoryServerHeartbeat(server.getServerId(), 123, "NEW");
        assertEquals(1, serversCache.size());
        verify(ascsSpy, times(1)).updateArtifactoryServerHeartbeat(server.getServerId(), 123, "NEW");
        verify(serversService, times(1)).updateArtifactoryServerHeartbeat(server.getServerId(), 123, "NEW");
        verify(serversCacheSpy, times(1)).getIfPresent(server.getServerId());
        verify(serversCacheSpy, times(1)).put(server.getServerId(), server);

        ArtifactoryServer artifactoryServer = serversCache.getIfPresent(server.getServerId());
        assertNotNull(artifactoryServer);
        assertEquals(123, artifactoryServer.getLastHeartbeat());
        assertEquals("NEW", artifactoryServer.getLicenseKeyHash());
        verifyNoMoreInteractions(ascsSpy, serversService, serversCacheSpy);
    }

    /**
     * Test update licenseHash to hash that is currently not in use by any other server
     */
    @Test
    void testUpdateServerLicenseHash() {
        when(serversService.getAllArtifactoryServers())
                .thenReturn(getAllServers(System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense1" + 3),
                        System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense2" + 3)));
        long lastHeartbeatToUpdate = System.currentTimeMillis();
        String licenseHashToUpdate = DigestUtils.sha1Hex("newLicense" + 3);
        when(serversService.updateArtifactoryServerHeartbeat(art1, lastHeartbeatToUpdate, licenseHashToUpdate))
                .thenReturn(1);
        // First update scenario, should succeed
        boolean updateSucceeded = serversCommonService
                .updateArtifactoryLicenseHash(art1, lastHeartbeatToUpdate, licenseHashToUpdate);
        assertTrue(updateSucceeded);
    }

    /**
     * Test update licenseHash while there is a race condition between this node and another HA node, means that some
     * other server just updated the licenseHash as well right after this node did, so two nodes now holds the same
     * licenseHash in the artifactory_servers table. The expected result is rollback while identifying that more than
     * one node holds this licenseHash
     */
    @Test
    void testUpdateServerLicenseHashWithRollback() {
        String licenseHashToUpdate = DigestUtils.sha1Hex("newLicense" + 3);
        String licenseToRollbackInto = DigestUtils.sha1Hex("otherLicense1" + 3);
        when(serversService.getAllArtifactoryServers())
                .thenReturn(getAllServers(System.currentTimeMillis(), licenseToRollbackInto,
                        System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense2" + 3)));
        // This is the 'race conditions'. Note that the this call (that validates that no other server is using this
        //license) return duplicate license and should rollback
        when(serversService.getAllArtifactoryServers())
                .thenReturn(getAllServers(System.currentTimeMillis(), licenseToRollbackInto,
                        System.currentTimeMillis(), licenseHashToUpdate));
        long lastHeartbeatToUpdate = System.currentTimeMillis();
        when(serversService.updateArtifactoryServerHeartbeat(art1, lastHeartbeatToUpdate, licenseHashToUpdate))
                .thenReturn(1);
        when(serversService.updateArtifactoryServerHeartbeat(art1, lastHeartbeatToUpdate,
                licenseToRollbackInto)).thenReturn(1);

        // First update scenario, should succeed
        boolean updateSucceeded = serversCommonService
                .updateArtifactoryLicenseHash(art1, lastHeartbeatToUpdate, licenseHashToUpdate);
        assertFalse(updateSucceeded);
    }

    /**
     * Similar as testUpdateServerLicenseHashWithRollback, but this time, instead of race condition with another node,
     * it's the same node that the DB indicates that already has the license. In other words, after updating the
     * licenseHash, we validate that no one else is using this license, in this test, we validate that indeed no one
     * else uses it, but if the DB indicates that I'm using it, test that NO ROLLBACK WILL OCCUR
     */
    @Test
    void testUpdateServerLicense2() {
        String licenseHashToUpdate = DigestUtils.sha1Hex("newLicense" + 3);
        when(serversService.getAllArtifactoryServers())
                .thenReturn(getAllServers(System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense1" + 3),
                        System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense2" + 3)));
        // This is the duplicate license check, we return our node as the holder of the license and expect no
        //rollback to occur
        when(serversService.getAllArtifactoryServers())
                .thenReturn(getAllServers(System.currentTimeMillis(), licenseHashToUpdate,
                        System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense2" + 3)));
        long lastHeartbeatToUpdate = System.currentTimeMillis();
        when(serversService.updateArtifactoryServerHeartbeat(art1, lastHeartbeatToUpdate, licenseHashToUpdate))
                .thenReturn(1);
        when(serversService.updateArtifactoryServerHeartbeat(art1, lastHeartbeatToUpdate,
                DigestUtils.sha1Hex("otherLicense2" + 3)))
                .thenReturn(1);

        // First update scenario, should succeed
        boolean updateSucceeded = serversCommonService
                .updateArtifactoryLicenseHash(art1, lastHeartbeatToUpdate, licenseHashToUpdate);
        assertTrue(updateSucceeded);
    }

    /**
     * Try to update the licenseHash while another server already own this hash in the artifactory_servers table. We
     * expect the update to fail
     */
    @Test
    void testUpdateServerLicense3() {
        String licenseHashToUpdate = DigestUtils.sha1Hex("newLicense" + 3);
        when(serversService.getAllArtifactoryServers())
                .thenReturn(getAllServers(System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense1" + 3),
                        System.currentTimeMillis(), licenseHashToUpdate));
        when(serversService.getAllArtifactoryServers())
                .thenReturn(getAllServers(System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense1" + 3),
                        System.currentTimeMillis(), DigestUtils.sha1Hex("otherLicense2" + 3)));

        // First update scenario, should succeed
        boolean updateSucceeded = serversCommonService
                .updateArtifactoryLicenseHash(art1, System.currentTimeMillis(), licenseHashToUpdate);
        assertFalse(updateSucceeded);
    }

    @Test
    void testGetConvertingMembers() {
        ArtifactoryServersService serversService = mock(ArtifactoryServersService.class);
        List<ArtifactoryServer> servers = new LinkedList<>();

        ArtifactoryServer s1 = new ArtifactoryServer("art1", 100L, "123.4.5.6", 5701, CONVERTING,
                MEMBER, System.currentTimeMillis(), "6.1", 2, 3L,
                HA, "somehash");
        servers.add(s1);
        when(serversService.getAllArtifactoryServers()).thenReturn(servers);

        serversCommonService = new ArtifactoryServersCommonServiceImpl(applicationEventPublisher, serversService);

        List<ArtifactoryServer> members = serversCommonService.getConvertingMembers();
        assertEquals(1, members.size());
        assertEquals(CONVERTING, members.get(0).getServerState());

        s1 = new ArtifactoryServer("art2", 100L, "123.4.5.7", 5702, RUNNING,
                MEMBER, System.currentTimeMillis(), "6.2", 2, 3L,
                HA, "somehash1");
        servers.add(s1);

        members = serversCommonService.getConvertingMembers();
        assertEquals(1, members.size());
        assertEquals(CONVERTING, members.get(0).getServerState());
        assertTrue(serversCommonService.isConversionRunning());
    }

    @Test
    void testServerQualifiedForDeletion() {
        ArtifactoryServer validServerByHeartbeat = new ArtifactoryServer("art2", 100L, "123.4.5.7", 5702,
                RUNNING, MEMBER, System.currentTimeMillis(), "6.2", 2, 3L,
                HA, "somehash1");

        assertFalse(ArtifactoryServersCommonService.qualifiesForDeletion.test(validServerByHeartbeat));

        ArtifactoryServer validServerByState = new ArtifactoryServer("art2", 100L, "123.4.5.7", 5702,
                CONVERTING,
                MEMBER, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(100), "7.0.0", 2,
                3L, HA, "somehash1");

        assertFalse(ArtifactoryServersCommonService.qualifiesForDeletion.test(validServerByState));

        ArtifactoryServer invalidServerNotConverting = new ArtifactoryServer("art2", 100L, "123.4.5.7", 5702,
                RUNNING,
                MEMBER, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(190), "7.0.0", 2,
                3L,
                HA, "somehash1");

        assertTrue(ArtifactoryServersCommonService.qualifiesForDeletion.test(invalidServerNotConverting));

        ArtifactoryServer invalidServerConverting = new ArtifactoryServer("art2", 100L, "123.4.5.7", 5702,
                CONVERTING,
                MEMBER, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(125), "7.0.0", 2,
                3L,
                HA, "somehash1");

        assertTrue(ArtifactoryServersCommonService.qualifiesForDeletion.test(invalidServerConverting));
    }

    /**
     * Return list of two servers
     *
     * @param server1ExpectedDate        The first server lastHeartbeatDate to return
     * @param server1ExpectedLicenseHash The first server licenseHash to return
     * @param server2ExpectedDate        The second server lastHeartbeatDate to return
     * @param server2ExpectedLicenseHash The second server licenseHash to return
     */
    private List<ArtifactoryServer> getAllServers(long server1ExpectedDate, String server1ExpectedLicenseHash,
            long server2ExpectedDate, String server2ExpectedLicenseHash) {
        ArtifactoryServer server1 = new ArtifactoryServer(art1, 1000000L,
                "123.4.5.6", 5701, RUNNING, MEMBER,
                server1ExpectedDate, "6.1", 2, 3L,
                HA, server1ExpectedLicenseHash);
        ArtifactoryServer server2 = new ArtifactoryServer(art2, 1000000L,
                "123.4.5.7", 5701, RUNNING, MEMBER,
                server2ExpectedDate, "6.1", 2, 3L,
                HA, server2ExpectedLicenseHash);
        return Arrays.asList(server1, server2);
    }

    private void initSomeDataForServersCache(long duration) {
        serversCache = CacheBuilder.newBuilder()
                .expireAfterWrite(duration, TimeUnit.SECONDS)
                .build();
        server = new ArtifactoryServer(art1, 1_000_000,
                "123.4.5.6", 5701, RUNNING, MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                HA, "COOL1");
        ArtifactoryServer anotherServer = new ArtifactoryServer(art2, 1_000_000,
                "123.4.5.7", 5701, RUNNING, MEMBER,
                System.currentTimeMillis(), "7", 7, 7,
                HA, "COOL2");
        servers = List.of(server, anotherServer);
    }

    private class DummyArtifactoryContext implements ArtifactoryContext {

        @Override
        public void exportTo(ExportSettings settings) {

        }

        @Override
        public void importFrom(ImportSettings settings) {

        }

        @Override
        public CentralConfigService getCentralConfig() {
            return null;
        }

        @Override
        public <T> T beanForType(Class<T> type) {
            return null;
        }

        @Override
        public <T> T beanForType(String name, Class<T> type) {
            return null;
        }

        @Override
        public <T> Map<String, T> beansForType(Class<T> type) {
            return null;
        }

        @Override
        public RepositoryService getRepositoryService() {
            return null;
        }

        @Override
        public RepositoryConfigService getRepositoryConfigService() {
            return null;
        }

        @Override
        public AuthorizationService getAuthorizationService() {
            return null;
        }

        @Override
        public long getUptime() {
            return 0;
        }

        @Override
        public ArtifactoryHome getArtifactoryHome() {
            return null;
        }

        @Override
        public String getContextId() {
            return null;
        }

        @Override
        public SpringConfigPaths getConfigPaths() {
            return null;
        }

        @Override
        public String getServerId() {
            return art1;
        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public boolean isCentralConfigImportInProgress() {
            return false;
        }

        @Override
        public boolean isReposImportInProgress() {
            return false;
        }

        @Override
        public boolean isVanillaInstallation() {
            return false;
        }

        @Override
        public boolean isOffline() {
            return false;
        }

        @Override
        public void setOffline() {

        }

        @Override
        public ConfigurationManager getConfigurationManager() {
            return null;
        }

        @Override
        public ConverterManager getConverterManager() {
            return null;
        }

        @Override
        public VersionProvider getVersionProvider() {
            return null;
        }

        @Override
        public LogbackConfigManager getLogbackConfigManager() {
            return null;
        }

        @Override
        public void destroy() {

        }
    }
}
