package org.artifactory.lock.service;

import com.google.common.collect.Lists;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.locks.service.ReleaseLocksDbLockService;
import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.lock.service.ReleaseLockStrategiesTestUtils.getArtifactoryServerInfoList;
import static org.artifactory.lock.service.ReleaseLockStrategiesTestUtils.getNonStandardCategories;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class StandardReleaseLockStrategyTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ReleaseLocksDbLockService dbLocksService;
    @Mock
    private ArtifactoryServersCommonService serversCommonService;

    private StandardReleaseLockStrategy standardReleaseLockStrategy;

    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .startMocking();

        standardReleaseLockStrategy = new StandardReleaseLockStrategy(Set.of(dbLocksService), serversCommonService);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testReleaseOrphaned() {
        Set<String> nonStandardCategories = getNonStandardCategories();
        List<ArtifactoryServerInfo> activeServers = getArtifactoryServerInfoList(2, true);
        List<ArtifactoryServerInfo> inactiveServers = getArtifactoryServerInfoList(2, false);
        List<ArtifactoryServerInfo> servers = Lists.newArrayList();
        servers.addAll(activeServers);
        servers.addAll(inactiveServers);

        when(serversCommonService.getAllArtifactoryServersInfo()).thenReturn(servers);

        Set<String> excludedServerIds = activeServers.stream().map(ArtifactoryServerInfo::getServerId)
                .collect(Collectors.toSet());

        standardReleaseLockStrategy.releaseOrphaned();

        verify(dbLocksService)
                .releaseOrphanLocksExcludingServersAndCategories(excludedServerIds, nonStandardCategories);

    }

    @Test
    public void testReleaseExpired() {
        Set<String> nonStandardLockCategories = getNonStandardCategories();

        standardReleaseLockStrategy.releaseExpired();

        verify(dbLocksService).releaseExpiredLocksExcludingCategories(nonStandardLockCategories,
                ConstantValues.lockLeaseTime.getLong());
    }
}