package org.artifactory.lock.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.artifactory.concurrent.NonStandardLockCategory;
import org.artifactory.storage.db.locks.service.ReleaseLocksDbLockService;
import org.artifactory.storage.db.servers.model.ArtifactoryServerInfo;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author Mor Merhav
 */
public class NonStandardReleaseLockStrategyTest extends ArtifactoryHomeBoundTest {

    @Mock
    private ReleaseLocksDbLockService dbLocksService;
    @Mock
    private ArtifactoryServersCommonService serversCommonService;

    private NonStandardReleaseLockStrategy nonStandardReleaseLockStrategy;

    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .initMocks(this)
                .startMocking();

        nonStandardReleaseLockStrategy =
                new NonStandardReleaseLockStrategy(Set.of(dbLocksService), serversCommonService);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testReleaseOrphaned() {
        List<ArtifactoryServerInfo> activeServers = getArtifactoryServerInfoList(2, true);
        List<ArtifactoryServerInfo> inactiveServers = getArtifactoryServerInfoList(3, false);
        List<ArtifactoryServerInfo> allServers = Lists.newArrayList(activeServers);
        allServers.addAll(inactiveServers);

        when(serversCommonService.getAllArtifactoryServersInfo()).thenReturn(allServers);

        Set<String> expectedActiveServerIds = activeServers.stream()
                .map(ArtifactoryServerInfo::getServerId)
                .collect(Collectors.toSet());
        Set<String> expectedCategories = getNonStandardCategories();

        nonStandardReleaseLockStrategy.releaseOrphaned();

        ArgumentCaptor<Set<String>> activeServerIdsArgumentCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<String> categoryArgumentCaptor = ArgumentCaptor.forClass(String.class);
        verify(dbLocksService, times(expectedCategories.size()))
                .releaseOrphanLocksByCategoryExcludingServers(activeServerIdsArgumentCaptor.capture(),
                        categoryArgumentCaptor.capture());
        List<Set<String>> actualActiveServerIdsCapture = activeServerIdsArgumentCaptor.getAllValues();
        Set<String> actualCategories = Sets.newHashSet(categoryArgumentCaptor.getAllValues());

        Set<String> actualActiveServerIds = actualActiveServerIdsCapture.stream().flatMap(Set::stream)
                .collect(Collectors.toSet());

        assertEquals(actualActiveServerIds, expectedActiveServerIds);
        assertEquals(actualCategories, expectedCategories);

    }

    @Test
    public void testReleaseExpired() {
        nonStandardReleaseLockStrategy.releaseExpired();

        NonStandardLockCategory[] nonStandardLockCategories = NonStandardLockCategory.values();
        for (NonStandardLockCategory nonStandardLockCategory : nonStandardLockCategories) {
            if (nonStandardLockCategory.isExpirable()) {
                verify(dbLocksService).releaseExpiredLocksByCategory(eq(nonStandardLockCategory.getName()),
                        eq(nonStandardLockCategory.getLockLeaseTimeMinutes()));
            } else {
                verify(dbLocksService, times(0))
                        .releaseExpiredLocksByCategory(eq(nonStandardLockCategory.getName()), anyInt());
            }
        }
    }
}