package org.artifactory.sh.cleanup;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.sh.cleanup.full.FullGcCollector;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.BinariesGcDao;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.common.BinaryElementImpl;
import org.jfrog.storage.binstore.exceptions.BinaryDbException;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.Logger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_GC_ITERATION;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * @author TamirH
 */
class GCServiceImplTest extends ArtifactoryHomeBoundJUnitTest {
    TrashService trashService;
    GarbageCollectionDatastoreService garbageCollectionDatastoreService;
    GCServiceImpl gcService;
    ConfigsService configsService;
    StorageService storageService;
    private FullGcCollector fullGcCollector;
    private ApplicationEventPublisher eventPublisher;
    private ArtifactoryBinaryService binaryService;

    @BeforeEach
    public void init() {
        this.binaryService = mock(ArtifactoryBinaryService.class);
        trashService = mock(TrashService.class);
        when(trashService.isTrashcanEnabled()).thenReturn(true);
        garbageCollectionDatastoreService = mock(GarbageCollectionDatastoreService.class);
        configsService = mock(ConfigsService.class);
        final BinariesGcDao binariesGcDao = mock(BinariesGcDao.class);
        this.eventPublisher = mock(ApplicationEventPublisher.class);
        final RepositoryService repoService = mock(RepositoryService.class);
        final SecurityService securityService = mock(SecurityService.class);
        final DbService dbService = mock(DbService.class);
        this.storageService = mock(StorageService.class);
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);
        gcService = spy(new GCServiceImpl(binaryService, trashService,
                garbageCollectionDatastoreService, configsService, binariesGcDao,
                eventPublisher, repoService, securityService, dbService, storageService));
        gcService.postConstruct();
        this.fullGcCollector = spy(gcService.getFullGcCollector());
        gcService.setFullGcCollector(fullGcCollector);

        defaultConfigServiceInit();
    }

    @Test
    void testGcFlow() {
        doReturn(new GarbageCollectorInfo("Strategy", 10)).when(gcService).runTrashAndBinariesGarbageCollect();
        doReturn(new GarbageCollectorInfo("Strategy", 10)).when(fullGcCollector).start();
        IntStream.range(0, 30).forEach(i -> gcService.startGarbageCollect());
        verify(gcService, times(30)).runTrashAndBinariesGarbageCollect();
        verify(fullGcCollector, times(1)).start();
        //31 times because the FullGC is invoking a minor internally, so we save twice in one iteration
        verify(garbageCollectionDatastoreService, times(31)).saveLastGCRunInfo(any());
    }

    @Test
    void testGcWithoutResult() {
        IntStream.range(0, 30).forEach(i -> gcService.startGarbageCollect());
        verify(gcService, times(30)).runTrashAndBinariesGarbageCollect();
        verify(fullGcCollector, times(1)).start();
        verify(garbageCollectionDatastoreService, times(31)).saveLastGCRunInfo(any());
    }

    @Test
    void testGcCount() {
        IntStream.range(0, 50).forEach(i -> gcService.startGarbageCollect());
        verify(gcService, times(50)).runTrashAndBinariesGarbageCollect();
        verify(fullGcCollector, times(2)).start();
        verify(garbageCollectionDatastoreService, times(52)).saveLastGCRunInfo(any());
    }

    private void defaultConfigServiceInit() {
        final AtomicInteger counter = new AtomicInteger(0);
        when(configsService.getConfig(ARTIFACTORY_GC_ITERATION)).thenAnswer((i) -> String.valueOf(counter));
        when(configsService.addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), anyString(), anyLong())).thenAnswer(i -> {
            counter.set(Integer.parseInt(i.getArgument(1)));
            return true;
        });
    }

    @Test
    void testGcCountWhileCannotFetchConfig() {
        when(configsService.getConfig(ARTIFACTORY_GC_ITERATION)).thenReturn("1").thenReturn("2")
                .thenThrow(new StorageException("Something went wrong"));
        IntStream.range(0, 50).forEach(i -> gcService.startGarbageCollect());
        verify(gcService, times(50)).runTrashAndBinariesGarbageCollect();
        verify(fullGcCollector, times(2)).start();
        verify(garbageCollectionDatastoreService, times(52)).saveLastGCRunInfo(any());
        verify(configsService, times(2)).addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), anyString(), anyLong());
    }

    @Test
    void testGcNullIteration() {
        when(configsService.getConfig(ARTIFACTORY_GC_ITERATION)).thenReturn(null);
        gcService.startGarbageCollect();
        verify(gcService, times(1)).runTrashAndBinariesGarbageCollect();
        verify(fullGcCollector, never()).start();
        verify(configsService, times(1)).addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), eq("1"), anyLong());
    }

    @Test
    public void testGcIterationFallBack() throws BinaryDbException {
        when(configsService.getConfig(ARTIFACTORY_GC_ITERATION))
                //gc increment doesn't exist in Configs
                .thenReturn(null).thenReturn("1")
                //couldn't retrieve gc increment from DB
                .thenThrow(RuntimeException.class)
                .thenReturn(String.valueOf(ConstantValues.gcSkipFullGcBetweenMinorIterations.getInt() - 1))
                .thenReturn("0");
        when(trashService.isTrashcanEnabled()).thenReturn(true);
        AtomicInteger gcIteration = (AtomicInteger) ReflectionTestUtils.getField(gcService, "fallbackGcIteration");
        assert gcIteration != null;
        gcService.startGarbageCollect();
        verify(configsService).addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), eq("1"), anyLong());
        assertEquals(gcIteration.get(), 1);
        gcService.startGarbageCollect();
        verify(configsService).addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), eq("2"), anyLong());
        assertEquals(gcIteration.get(), 2);
        //expect an exception happened, gcIteration was used instead of configs and configs not updated
        Mockito.clearInvocations(configsService);
        gcService.startGarbageCollect();
        assertEquals(gcIteration.get(), 3);
        verify(configsService, never()).addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), anyString(), anyLong());
        gcService.startGarbageCollect();
        verify(configsService).addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), eq("0"), anyLong());
        assertEquals(gcIteration.get(), 0);
        gcService.startGarbageCollect();
        verify(configsService).addOrUpdateConfig(eq(ARTIFACTORY_GC_ITERATION), eq("1"), anyLong());
        assertEquals(gcIteration.get(), 1);
    }

    @Test
    void testForceFull() {
        final GarbageCollectorInfo garbageCollectorInfo = gcService.forceFull();
        verify(fullGcCollector, times(1)).start();
        verify(garbageCollectionDatastoreService, times(1))
                .saveLastGCRunInfo(garbageCollectorInfo);
    }

    @Test
    void testForceFullNoMetricsSaved() {
        final GarbageCollectorInfo garbageCollectorInfo = gcService.forceFull();
        verify(fullGcCollector, times(1)).start();
        verify(garbageCollectionDatastoreService, times(0))
                .saveLastGCRunInfo(null);
    }

    @Test
    void testForceFullNoTrash() {
        when(trashService.isTrashcanEnabled()).thenReturn(false);
        final GarbageCollectorInfo garbageCollectorInfo = gcService.forceFull();
        verify(fullGcCollector, times(1)).start();
        verify(garbageCollectionDatastoreService, times(1))
                .saveLastGCRunInfo(garbageCollectorInfo);
    }

    @Test
    void testDeleteBlobInfosNullCheck() {
        gcService.deleteBlobInfos(null);
        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @Test
    void testDeleteBlobInfos() {
        gcService.deleteBlobInfos(List.of("sha2"));
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    void testElementBinaryCleanerFailedDeleteArchiveRecord() {
        final BinaryElementImpl binaryElement = new BinaryElementImpl("sha1", "sha2", "md5", 5);
        final GarbageCollectorInfo garbageCollectorInfo = new GarbageCollectorInfo("Full", 10);
        when(binaryService.deleteBinary(anyString())).thenReturn(false);
        assertFalse(gcService.executeBinaryCleaner(binaryElement, garbageCollectorInfo));
    }

    @Test
    void testElementBinaryCleaner() {
        final String sha1 = "sha1";
        final BinaryElementImpl binaryElement = new BinaryElementImpl(sha1, "sha2", "md5", 5);
        final GarbageCollectorInfo garbageCollectorInfo = new GarbageCollectorInfo("Full", 10);
        when(binaryService.deleteBinary(sha1)).thenReturn(true);
        gcService.executeBinaryCleaner(binaryElement, garbageCollectorInfo);
        verify(binaryService, times(1)).deleteBinary(sha1);
        Assertions.assertEquals(1, garbageCollectorInfo.getBinariesCleaned().get());
        Assertions.assertEquals(1, garbageCollectorInfo.getChecksumsCleaned().get());
        Assertions.assertEquals(5, garbageCollectorInfo.getTotalSizeCleaned().get());
    }

    @Test
    void testElementBinaryCleanerFailedCleanBinary() {
        final String sha1 = "sha1";
        final BinaryElementImpl binaryElement = new BinaryElementImpl(sha1, "sha2", "md5", 5);
        final GarbageCollectorInfo garbageCollectorInfo = new GarbageCollectorInfo("Full", 10);
        when(binaryService.deleteBinary(sha1)).thenReturn(false);
        gcService.executeBinaryCleaner(binaryElement, garbageCollectorInfo);
        verify(binaryService, times(1)).deleteBinary(sha1);
        Assertions.assertEquals(0, garbageCollectorInfo.getBinariesCleaned().get());
        Assertions.assertEquals(0, garbageCollectorInfo.getChecksumsCleaned().get());
        Assertions.assertEquals(0, garbageCollectorInfo.getTotalSizeCleaned().get());
    }

    @Test
    void testGcCandidateBinaryCleanerFailedCleanBinary() {
        final String sha1 = "shagc1";
        final GCCandidate gcCandidate = new GCCandidate(new RepoPathImpl("test", ""), sha1, "sha2", "md5", 5);
        final GarbageCollectorInfo garbageCollectorInfo = new GarbageCollectorInfo("Full", 10);
        when(binaryService.deleteBinary(sha1)).thenReturn(false);
        gcService.executeBinaryCleaner(gcCandidate, garbageCollectorInfo);
        verify(binaryService, times(1)).deleteBinary(sha1);
        Assertions.assertEquals(0, garbageCollectorInfo.getBinariesCleaned().get());
        Assertions.assertEquals(0, garbageCollectorInfo.getChecksumsCleaned().get());
        Assertions.assertEquals(0, garbageCollectorInfo.getTotalSizeCleaned().get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRunShardingBalancerTask(boolean withError) {
        if (withError) {
            doAnswer(invocation -> {
                BasicStatusHolder statusHolder = invocation.getArgument(0);
                statusHolder.error("Sharding balancer error", new RuntimeException("Test exception"),
                        mock(Logger.class));
                return null;
            }).when(storageService).callManualShardingBalancer(any(BasicStatusHolder.class));
        }

        gcService.runShardingBalancerTask();

        verify(storageService, times(1)).callManualShardingBalancer(any(BasicStatusHolder.class));
    }

    @Test
    void testRunShardingBalancerTaskDisabled() {
        homeStub.setProperty(ConstantValues.gcCheckBalanceAfterFullGc, "false");

        final BinariesGcDao binariesGcDao = mock(BinariesGcDao.class);
        final RepositoryService repoService = mock(RepositoryService.class);
        final SecurityService securityService = mock(SecurityService.class);
        final DbService dbService = mock(DbService.class);
        when(dbService.getDatabaseType()).thenReturn(org.jfrog.storage.DbType.POSTGRESQL);
        GCServiceImpl gcServiceWithDisabled = new GCServiceImpl(binaryService, trashService,
                garbageCollectionDatastoreService, configsService, binariesGcDao,
                eventPublisher, repoService, securityService, dbService, storageService);

        gcServiceWithDisabled.runShardingBalancerTask();

        verify(storageService, never()).callManualShardingBalancer(any(BasicStatusHolder.class));
    }

}