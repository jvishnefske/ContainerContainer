package org.artifactory.lock.util;

import com.google.common.collect.Lists;
import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author Mor Merhav
 */
public class ReleaseLockStrategyUtilsTest {

    @Mock
    private ArtifactoryServersCommonService serversCommonService;
    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setUp() {
        mockitoSession = mockitoSession().
                strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testFindActiveServers() {
        ArtifactoryServerInfo serverInfoMockActive1 = mock(ArtifactoryServerInfo.class);
        ArtifactoryServerInfo serverInfoMockActive2 = mock(ArtifactoryServerInfo.class);
        ArtifactoryServerInfo serverInfoMockActive3 = mock(ArtifactoryServerInfo.class);
        ArtifactoryServerInfo serverInfoMockInactive1 = mock(ArtifactoryServerInfo.class);
        ArtifactoryServerInfo serverInfoMockInactive2 = mock(ArtifactoryServerInfo.class);
        ArtifactoryServerInfo serverInfoMockInactive3 = mock(ArtifactoryServerInfo.class);

        long expirationTime = 1000;

        when(serverInfoMockActive1.isActive(eq(expirationTime))).thenReturn(true);
        when(serverInfoMockActive2.isActive(eq(expirationTime))).thenReturn(true);
        when(serverInfoMockActive3.isActive(eq(expirationTime))).thenReturn(true);

        when(serverInfoMockActive1.getServerId()).thenReturn("active1");
        when(serverInfoMockActive2.getServerId()).thenReturn("active2");
        when(serverInfoMockActive3.getServerId()).thenReturn("active3");

        when(serverInfoMockInactive1.isActive(eq(expirationTime))).thenReturn(false);
        when(serverInfoMockInactive2.isActive(eq(expirationTime))).thenReturn(false);
        when(serverInfoMockInactive3.isActive(eq(expirationTime))).thenReturn(false);

        List<ArtifactoryServerInfo> serverInfoList = Lists
                .newArrayList(serverInfoMockActive1, serverInfoMockActive2, serverInfoMockActive3,
                        serverInfoMockInactive1, serverInfoMockInactive2, serverInfoMockInactive3);

        when(serversCommonService.getAllArtifactoryServersInfo()).thenReturn(serverInfoList);

        Set<String> activeServers = ReleaseLockStrategyUtils.findActiveServers(serversCommonService, expirationTime);

        assertNotNull(activeServers);
        assertEquals(3, activeServers.size());
        Iterator<String> iterator = activeServers.iterator();
        assertEquals(serverInfoMockActive1.getServerId(), iterator.next());
        assertEquals(serverInfoMockActive2.getServerId(), iterator.next());
        assertEquals(serverInfoMockActive3.getServerId(), iterator.next());

    }
}