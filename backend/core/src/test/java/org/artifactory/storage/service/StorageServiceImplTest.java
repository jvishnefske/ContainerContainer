package org.artifactory.storage.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.aol.AolService;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.gc.GcConfigDescriptor;
import org.artifactory.descriptor.quota.QuotaConfigDescriptor;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.FileStoreStorageSummary;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.StorageUsage;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.model.DbMutableFile;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.storage.jobs.SyncStorageUsageJob;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryProvidersInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryTreeElement;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author barh
 */
public class StorageServiceImplTest extends ArtifactoryHomeBoundTest {

    private final static String PROJECTX = "projectx";
    private final static String PROJECTY = "projecty";
    public static final String PROJ_REPO1 = "source-repo";
    public static final String PROJ_REPO2 = "target-repo";

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private DbService dbService;
    @Mock
    private ArtifactoryBinaryService binaryStore;
    @Mock
    private TaskService taskService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private StorageSummaryService storageSummaryService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private ConfigsService configsService;
    @Mock
    private TrashService trashService;
    @Mock
    private AolService aolService;
    @Mock
    InternalArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeEach
    public void init() {
        bindArtifactoryHome();
        mocks = MockitoAnnotations.openMocks(this);
        MBeanRegistrationServiceImpl mbeanService = mock(MBeanRegistrationServiceImpl.class);
        when(context.beanForType(MBeanRegistrationServiceImpl.class)).thenReturn(mbeanService);
        when(context.beanForType(SecurityService.class)).thenReturn(mock(SecurityService.class));
        when(context.getTaskService()).thenReturn(taskService);
        when(context.beanForType(AolService.class)).thenReturn(aolService);
        setStringSystemProperty(ConstantValues.gcTrashBinariesCleanupEnabled, "false");
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        unbindArtifactoryHome();
        mocks.close();
    }

    @Test
    public void testHideSensitiveInfo() {
        StorageServiceImpl storageHalfMock = spy(StorageServiceImpl.class);
        ArtifactoryBinaryService internalBinaryService = mock(ArtifactoryBinaryService.class);
        storageHalfMock.setInternalBinaryService(internalBinaryService);
        BinaryProvidersInfo<Map<String, String>> binaryProvidersInfo = createProviderInfo();
        when(internalBinaryService.getBinaryProvidersInfo()).thenReturn(binaryProvidersInfo);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        storageHalfMock.setCentralConfigService(centralConfigService);

        BinaryProvidersInfo<Map<String, String>> binaryProviderInfo = storageHalfMock.getBinaryProviderInfo();
        Map<String, String> rootData = binaryProviderInfo.rootTreeElement.getData();
        assertEquals(rootData.size(), 1);
        assertTrue(rootData.containsKey("other"));
        assertFalse(rootData.containsKey("identity"));
        assertFalse(rootData.containsKey("credential"));
        Map<String, String> secondData = binaryProviderInfo.rootTreeElement.getNextBinaryTreeElement().getData();
        assertEquals(secondData.size(), 2);
        assertTrue(secondData.containsKey("path"));
        assertTrue(secondData.containsKey("https"));
        assertFalse(secondData.containsKey("accountKey"));
        assertFalse(secondData.containsKey("accountName"));
    }

    private BinaryProvidersInfo<Map<String, String>> createProviderInfo() {
        BinaryTreeElement<Map<String, String>> rootElement = new BinaryTreeElement<>();
        HashMap<String, String> data = Maps.newHashMap();
        data.put("credential", "c");
        data.put("identity", "d");
        data.put("other", "d");
        rootElement.setData(data);

        BinaryTreeElement<Map<String, String>> secondElement = new BinaryTreeElement<>();
        HashMap<String, String> secondData = Maps.newHashMap();
        secondData.put("accountKey", "k");
        secondData.put("accountName", "n");
        secondData.put("path", "b");
        secondData.put("https", "e");
        secondElement.setData(secondData);

        rootElement.setNextBinaryTreeElement(secondElement);
        return new BinaryProvidersInfo<>("cache", rootElement);
    }

    private static BinaryProvidersInfo<Map<String, String>> createCacheProviderInfo() {
        return createTemplateProviderInfo("cache", "cache-fs", List.of("sharding"));
    }

    private static BinaryProvidersInfo<Map<String, String>> createFilesystemProviderInfo() {
        return createTemplateProviderInfo("cache-fs", "cache-fs", List.of("eventual", "retry", "file-system"));
    }

    private static BinaryProvidersInfo<Map<String, String>> createS3ProviderInfo() {
        return createTemplateProviderInfo("s3-storage-v3", "cache-fs", List.of("eventual", "retry", "s3-storage-v3"));
    }

    private static BinaryProvidersInfo<Map<String, String>> createShardingClusterProviderInfo() {
        return createTemplateProviderInfo("cluster-file-system", "cache-fs", List.of("sharding-cluster"));
    }

    private static Stream<Arguments> provideBinaryProvidersInfoForQuotaTest() {
        return Stream.of(
                Arguments.of(createCacheProviderInfo(), 2, 1),
                Arguments.of(createFilesystemProviderInfo(), 4, 3),
                Arguments.of(createS3ProviderInfo(), 4, -1),
                Arguments.of(createShardingClusterProviderInfo(), 2, 1)
        );
    }

    private static BinaryProvidersInfo<Map<String, String>> createTemplateProviderInfo(String template, String root,
            List<String> providersType) {
        BinaryTreeElement<Map<String, String>> rootElement = new BinaryTreeElement<>();
        HashMap<String, String> data = Maps.newHashMap();
        data.put("type", root);
        rootElement.setData(data);

        BinaryTreeElement<Map<String, String>> rootElementCopy = rootElement;


        for (String providerType : providersType) {
            BinaryTreeElement<Map<String, String>> nextElement = new BinaryTreeElement<>();
            HashMap<String, String> nextData = Maps.newHashMap();
            nextData.put("type", providerType);
            nextElement.setData(nextData);

            rootElement.setNextBinaryTreeElement(nextElement);
            rootElement = rootElement.getNextBinaryTreeElement();
        }

        return new BinaryProvidersInfo<>(template, rootElementCopy);
    }

    @Test
    void testGetCachedStorageSizeResolveFromDB() {
        //prepare
        StorageServiceImpl storageHalfMock = initStorageService();
        BinariesInfo binariesInfo = mock(BinariesInfo.class);
        when(storageSummaryService.getLocalCache()).thenThrow(CacheUnAvailableException.class);
        doReturn(binariesInfo).when(binaryStore).getBinariesInfo();
        //act
        storageHalfMock.getCachedStorageSize();

        //verify
        verify(binaryStore, times(1)).getBinariesInfo();
    }

    @Test
    void testGetCachedStorageSizeResolveFromCache() {
        //prepare
        StorageServiceImpl storageHalfMock = initStorageService();
        when(storageSummaryService.getLocalCache()).thenReturn(createStorageSummary());
        //act
        storageHalfMock.getCachedStorageSize();

        verify(binaryStore, times(0)).getBinariesInfo();
    }

    @Test
    void testCalculateStorageSummary() throws InterruptedException {
        StorageServiceImpl storageService = initStorageService();
        setStorageSummaryCacheEnable(true, storageService);
        setMocks();
        StorageSummaryInfo info = createStorageSummary();
        when(storageSummaryService.updateAndGet()).thenReturn(info);
        storageService.calculateStorageSummary();
        assertEquals(Objects.requireNonNull(info.getRepoStorageSummary("repoKey")).getUsedSpace(),
                3);
        verify(storageSummaryService, times(1)).updateAndGet();
    }

    @Test
    void testCalculateStorageSummaryAsync() throws InterruptedException {
        StorageServiceImpl storageService = initStorageService();
        setStorageSummaryCacheEnable(true, storageService);
        setMocks();
        StorageSummaryInfo info = createStorageSummary();
        when(storageSummaryService.updateAndGet()).thenReturn(info);
        storageService.calculateStorageSummaryAsync();
        assertEquals(Objects.requireNonNull(info.getRepoStorageSummary("repoKey")).getUsedSpace(),
                3);
        verify(storageSummaryService, times(1)).updateAndGet();
    }

    @Test
    void testCalculateStorageSummaryOld() throws InterruptedException {
        StorageServiceImpl storageService = initStorageService();
        setStorageSummaryCacheEnable(false, storageService);
        setMocks();
        StorageSummaryInfo info = createStorageSummary();
        when(storageSummaryService.updateAndGet()).thenReturn(info);
        storageService.calculateStorageSummary();
        assertEquals(Objects.requireNonNull(info.getRepoStorageSummary("repoKey")).getUsedSpace(),
                3);
        verify(storageSummaryService, times(1)).updateAndGet();
    }

    @Test
    void testCalculateStorageSummaryOnStartup() throws InterruptedException {
        StorageServiceImpl storageService = initStorageService();
        setStorageSummaryCacheEnable(true, storageService);
        setMocks();
        StorageSummaryInfo info = createStorageSummary();
        when(storageSummaryService.get()).thenReturn(info);
        storageService.calculateStorageSummaryAsyncOnStartup();
        assertEquals(Objects.requireNonNull(info.getRepoStorageSummary("repoKey")).getUsedSpace(),
                3);
        verify(storageSummaryService, times(1)).get();
    }

    @Test
    void testGetFileStoreStorageSummary() {
        when(binaryStore.getBinaryProvidersInfo()).thenReturn(
                new BinaryProvidersInfo<Map<String, String>>("cache", mock(BinaryTreeElement.class)));
        StorageServiceImpl storageService = initStorageService();
        FileStoreStorageSummary fileStoreStorageSummary = storageService.getFileStoreStorageSummary();
        assertNotNull(fileStoreStorageSummary);
    }

    private StorageSummaryInfo createStorageSummary() {
        return new StorageSummaryInfo(Map.of("repoKey", new RepoStorageSummaryInfo("repoKey",
                RepoStorageSummaryInfo.RepositoryType.LOCAL, 1, 2, 3, "type", "projectKey")),
                new BinariesInfo(),
                Map.of(), Map.of());
    }

    private void setStorageSummaryCacheEnable(boolean enabled, StorageServiceImpl storageService) {
        Field field = ReflectionUtils.findField(StorageServiceImpl.class, "storageSummaryCacheEnable");
        field.setAccessible(true);
        ReflectionUtils.setField(field, storageService, enabled);
    }

    private void setMocks() throws InterruptedException {
        ConflictsGuard<Object> conflictsGuard = mock(ConflictsGuard.class);
        ConflictGuard conflictGuard = mock(ConflictGuard.class);
        when(conflictGuard.tryToLock(anyLong(), any())).thenReturn(true);
        when(conflictsGuard.getLock(any())).thenReturn(conflictGuard);
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(haCommonAddon.getConflictsGuard(anyString())).thenReturn(conflictsGuard);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        HaAddon haAddon = mock(HaAddon.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(true);
    }

    @Test
    void testOnContextCreated() {
        StorageServiceImpl storageService = initStorageService();
        homeStub.setProperty(ConstantValues.metricsEnabled, "true");
        homeStub.setProperty(ConstantValues.updateStorageStatusJobEnabled, "true");
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryApplicationContext artifactoryApplicationContext = mock(ArtifactoryApplicationContext.class);
            when(artifactoryApplicationContext.beanForType(StorageService.class))
                    .thenReturn(mock(StorageService.class));
            contextHelper.when(ContextHelper::get).thenReturn(artifactoryApplicationContext);
            storageService.onContextCreated();
            verify(taskService, times(3)).startTask(any(), anyBoolean());
        }
    }

    @Test
    void testGetProjectStorageSum() {
        StorageServiceImpl storageService = initStorageService();
        assertEquals(1000L, storageService.getProjectStorageUsage(PROJECTX));
        assertEquals(0L, storageService.getProjectStorageUsage(ProjectsService.DEFAULT_PROJECT));
    }

    @Test
    void testSyncStorageUsageDiffsIfNeeded() {
        // Setup
        StorageServiceImpl storageService = initStorageService();
        when(repositoryService.getAllRepoKeys()).thenReturn(ImmutableSet.of(PROJ_REPO1));
        when(projectsService.getAllProjects()).thenReturn(List.of(PROJECTX));
        when(projectsService.getProjectKeyOfRepository(PROJ_REPO1)).thenReturn(PROJECTX);
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.getAllArtifactoryServers()).thenReturn(new ArrayList<>());
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);

        // Set the project limit for 2000 bytes
        when(projectsService.getProjectStorageQuotaBytes(PROJECTX)).thenReturn(2000L);
        // Call syncStorageUsageDiffsIfNeeded and expect nothing to happened - tests the flush threshold
        storageService.syncStorageUsageDiffsIfNeeded();

        //Deploy a small file into a project related repository
        storageService.afterCreate(prepareDbMutableFileMock(1), null, null);
        // Call syncStorageUsageDiffsIfNeeded and expect nothing to happened
        storageService.syncStorageUsageDiffsIfNeeded();

        // Deploy a bigger file into the project related repository
        storageService.afterCreate(prepareDbMutableFileMock(500), null, null);
        storageService.syncStorageUsageDiffsIfNeeded();
        when(context.beanForType(TrashService.class)).thenReturn(trashService);
        when(trashService.isTrashcanEnabled()).thenReturn(false);
        // Delete the file from before so the total diff will be only 1 byte
        DeleteContext deleteContext = new DeleteContext(new RepoPathImpl(PROJ_REPO1, "test.file"));
        storageService.afterDelete(prepareDbMutableFileMock(500), null, deleteContext);
        // Call syncStorageUsageDiffsIfNeeded for 15 times in total (including the previous retries) and expect
        // saveAndReloadStorageUsageDiffsWithRetries will get called at the last try
        for (int i = 0; i < 11; i++) {
            storageService.syncStorageUsageDiffsIfNeeded();
        }
        storageService.syncStorageUsageDiffsIfNeeded();
    }

    @Test
    void testUpdateStorageSummaryWithStorageUsage() {
        StorageServiceImpl storageService = initStorageService();
        StorageUsage storageUsage = new StorageUsage();
        storageUsage.getProjectsStorageSum().put("project", 2000L);
        storageUsage.getRepoStorageSum().put("proj-repo", 1000L);
        storageUsage.getRepoStorageSum().put("proj-remote-cache", 1000L);

        Map<String, String> repoToProjMap = new HashMap<>();
        repoToProjMap.put("proj-repo", "project");
        repoToProjMap.put("proj-remote", "project");
        when(projectsService.getRepoKeyToProjectMap(anySet(), anyBoolean())).thenReturn(repoToProjMap);

        when(repositoryService.getAllRepoKeys())
                .thenReturn(ImmutableSet.of("proj-repo", "proj-remote-cache", "proj-remote"));
        StorageSummaryInfo info = populateStorageSummaryCache();
        when(storageSummaryService.getLocalCache()).thenReturn(info);
        storageService.updateStorageSummaryWithStorageUsage(storageUsage);

        assertEquals(info.getProjectsTotalSizes().getOrDefault("project", 0L).longValue(), 2000L);
        assertEquals(info.getRepoStorageSummary("proj-repo").getUsedSpace(), 1000L);
        assertEquals("project", info.getRepoStorageSummary("proj-remote-cache").getProjectKey());
    }

    @Test
    void testUpdateStorageSummeryWhenUsedSpaceChanged() {
        final StorageServiceImpl storageService = initStorageService();
        final StorageUsage storageUsage = new StorageUsage();
        final String repoName = "proj-repo";
        storageUsage.getProjectsStorageSum().put("project", 1000L);
        storageUsage.getRepoStorageSum().put(repoName, 1000L);

        final Map<String, String> repoToProjMap = new HashMap<>();
        repoToProjMap.put(repoName, "project");
        when(projectsService.getRepoKeyToProjectMap(anySet(), anyBoolean())).thenReturn(repoToProjMap);

        when(repositoryService.getAllRepoKeys())
                .thenReturn(ImmutableSet.of(repoName));


        final Map<String, RepoStorageSummaryInfo> repoSumMap = new HashMap<>();
        RepoStorageSummaryInfo first =
                new RepoStorageSummaryInfo(repoName, RepoStorageSummaryInfo.RepositoryType.NA, 1, 1, 500L, "local",
                        "project");
        repoSumMap.put(repoName, first);
        StorageSummaryInfo info = new StorageSummaryInfo(repoSumMap, null, Map.of("project", 500L), new HashMap<>());
        when(storageSummaryService.getLocalCache()).thenReturn(info);
        storageService.updateStorageSummaryWithStorageUsage(storageUsage);

        //make sure storage summary not a old value from storage usage
        assertEquals(500L, info.getRepoStorageSummaries().get("proj-repo").getUsedSpace());
    }

    @ParameterizedTest
    @MethodSource("provideBinaryProvidersInfoForQuotaTest")
    void testGetStorageInfoWithQuotaWarningAndLimitWithTemplate(BinaryProvidersInfo<Map<String, String>> providerInfo,
            int chainSize, int quotaIndex) {
        StorageServiceImpl storageService = initStorageService();
        when(binaryStore.getBinaryProvidersInfo()).thenReturn(providerInfo);
        BinaryTreeElement<Map<String, String>> binaryTreeElement =
                storageService.getBinaryProviderInfo().rootTreeElement;

        for (int index = 0; index < chainSize; index++) {
            if (index == quotaIndex) {
                assertEquals(binaryTreeElement.getData().get("quotaErrorLimit"), "90");
                assertEquals(binaryTreeElement.getData().get("quotaWarningLimit"), "70");
            } else {
                assertNull(binaryTreeElement.getData().get("quotaErrorLimit"));
                assertNull(binaryTreeElement.getData().get("quotaWarningLimit"));
            }

            binaryTreeElement = binaryTreeElement.getNextBinaryTreeElement();
        }
    }

    @Test
    @DisplayName("replicates a scenario where all repos are present, but one switched a project")
    void testMergeStorageDiffWithStorageUsageWithAllReposPresent() {
        String otherProjRepo = "other-proj-repo";
        StorageServiceImpl storageService = initStorageService();
        when(projectsService.getProjectKeyOfRepository(PROJ_REPO1)).thenReturn(PROJECTX);
        when(projectsService.getProjectKeyOfRepository(otherProjRepo)).thenReturn(PROJECTX);
        Map<String, String> repoToProjMap = new HashMap<>();
        repoToProjMap.put(PROJ_REPO1, PROJECTX);
        repoToProjMap.put(otherProjRepo, PROJECTX);
        when(projectsService.getRepoKeyToProjectMap(anySet(), anyBoolean())).thenReturn(repoToProjMap);
        when(repositoryService.getAllRepoKeys()).thenReturn(ImmutableSet.of(otherProjRepo, PROJ_REPO1));

        StorageUsage oldStorageUsage = new StorageUsage();
        oldStorageUsage.setRevision(1);
        oldStorageUsage.getProjectsStorageSum().put(PROJECTX, 1000L);
        oldStorageUsage.getRepoStorageSum().put(otherProjRepo, 1000L);

        // Deploy some file so we gonna have some changes to merge
        storageService.afterCreate(prepareDbMutableFileMock(500), null, null);

        // Call mergeStorageDiffWithStorageUsage
        StorageUsage mergedStorageUsage = storageService.mergeStorageDiffWithStorageUsage(oldStorageUsage);

        // Verify the merged StorageUsage contains both local changes and oldStorageUsage
        assertEquals(mergedStorageUsage.getRepoStorageSum().get(PROJ_REPO1).longValue(), 500);
        assertEquals(mergedStorageUsage.getRepoStorageSum().get(otherProjRepo).longValue(), 1000);
        assertEquals(mergedStorageUsage.getProjectsStorageSum().get(PROJECTX).longValue(), 1500);
        assertEquals(mergedStorageUsage.getRevision(), 2);
    }

    @Test
    @DisplayName("replicates a scenario where one repo was removed, but a file was added to a previous project" +
            " size")
    void testMergeStorageDiffWithStorageUsageWithOneRepoRemoved() {
        // repo below will be deleted
        String otherProjRepo = "other-proj-repo";
        StorageServiceImpl storageService = initStorageService();
        // repository service indicates that only one repo exists now
        when(repositoryService.getAllRepoKeys()).thenReturn(ImmutableSet.of(PROJ_REPO1));
        when(projectsService.getProjectKeyOfRepository(PROJ_REPO1)).thenReturn(PROJECTX);
        // below simulates response from Access
        Map<String, String> repoToProjMap = new HashMap<>();
        repoToProjMap.put(PROJ_REPO1, PROJECTX);
        when(projectsService.getRepoKeyToProjectMap(anySet(), anyBoolean())).thenReturn(repoToProjMap);

        StorageUsage oldStorageUsage = new StorageUsage();
        oldStorageUsage.setRevision(1);
        oldStorageUsage.getProjectsStorageSum().put(PROJECTX, 1000L);
        oldStorageUsage.getRepoStorageSum().put(otherProjRepo, 1000L);

        // Deploy some file so we gonna have some changes to merge
        storageService.afterCreate(prepareDbMutableFileMock(500), null, null);

        // Call mergeStorageDiffWithStorageUsage
        StorageUsage mergedStorageUsage = storageService.mergeStorageDiffWithStorageUsage(oldStorageUsage);

        // Verify the merged StorageUsage contains both local changes and oldStorageUsage
        assertEquals(mergedStorageUsage.getRepoStorageSum().get(PROJ_REPO1).longValue(), 500);
        assertNull(mergedStorageUsage.getRepoStorageSum().get(otherProjRepo));
        assertEquals(mergedStorageUsage.getProjectsStorageSum().get(PROJECTX).longValue(), 500);
        assertEquals(mergedStorageUsage.getRevision(), 2);
    }

    @Test
    void testMergeStorageDiffWithStorageUsageWithRepoReassignedOnlyAndWithoutDiffs() {
        StorageUsage oldStorageUsage = new StorageUsage();
        oldStorageUsage.setRevision(1);
        oldStorageUsage.getProjectsStorageSum().put(PROJECTX, 1000L);
        oldStorageUsage.getProjectsStorageSum().put(PROJECTY, 1000L);
        oldStorageUsage.getRepoStorageSum().put(PROJ_REPO1, 1000L);
        oldStorageUsage.getRepoStorageSum().put(PROJ_REPO2, 1000L);

        StorageServiceImpl storageService = initStorageService();
        when(projectsService.getAllProjects()).thenReturn(ImmutableList.of(PROJECTX, PROJECTY));
        when(repositoryService.getAllRepoKeys()).thenReturn(ImmutableSet.of(PROJ_REPO2, PROJ_REPO1));
        Map<String, String> repoToProjMap = new HashMap<>();
        repoToProjMap.put(PROJ_REPO1, PROJECTX);
        repoToProjMap.put(PROJ_REPO2, PROJECTX);
        when(projectsService.getRepoKeyToProjectMap(anySet(), anyBoolean())).thenReturn(repoToProjMap);

        // Call mergeStorageDiffWithStorageUsage
        StorageUsage mergedStorageUsage = storageService.mergeStorageDiffWithStorageUsage(oldStorageUsage);

        // Verify the merged StorageUsage contains both local changes and oldStorageUsage
        assertEquals(mergedStorageUsage.getProjectsStorageSum().get(PROJECTX).longValue(), 2000);
        assertEquals(mergedStorageUsage.getProjectsStorageSum().get(PROJECTY).longValue(), 0);
        assertEquals(mergedStorageUsage.getRepoStorageSum().get(PROJ_REPO1).longValue(), 1000);
        assertEquals(mergedStorageUsage.getRepoStorageSum().get(PROJ_REPO2).longValue(), 1000);
        assertEquals(mergedStorageUsage.getRevision(), 2);
    }

    @Test
    void testSyncStorageUsageWithStorageSummary() {
        StorageServiceImpl storageService = initStorageService();
        when(repositoryService.getAllRepoKeys()).thenReturn(ImmutableSet.of("example-repo-local", "prjx-generic",
                "artifactory-build-info", "auto-trashcan", "jfrog-support-bundle"));
        when(configsService.getConfig(ArtifactoryHome.STORAGE_USAGE_FILE)).thenReturn("{\"revision\":1603365765723," +
                "\"repoStorageSum\":{\"example-repo-local\":5703478,\"prjx-generic\":43741906," +
                "\"artifactory-build-info\":0,\"auto-trashcan\":0,\"jfrog-support-bundle\":0}," +
                "\"projectsStorageSum\":{\"default\":5703478,\"projectx\":43741906}}");
        StorageSummaryInfo info = populateStorageSummaryCache();
        when(storageSummaryService.getLocalCache()).thenReturn(info);
        // Sync
        storageService.syncStorageUsageWithStorageSummary();

        // Verify the local cache has been updated correctly
        assertEquals(info.getProjectsTotalSizes().get(PROJECTX).longValue(), 43741906);
        assertEquals(info.getRepoStorageSummary("prjx-generic").getUsedSpace(), 43741906);
        assertEquals(info.getProjectsTotalSizes().get(ProjectsService.DEFAULT_PROJECT).longValue(), 5703478);
        assertEquals(info.getRepoStorageSummary("example-repo-local").getUsedSpace(), 5703478);
        assertEquals(info.getRepoStorageSummary("auto-trashcan").getUsedSpace(), 0);
    }

    @Test
    void testMergeDiffChanges() {
        StorageServiceImpl storageService = initStorageService();
        Map<String, AtomicLong> realDiffMap = new ConcurrentHashMap<>();
        Map<String, AtomicLong> backupDiffMap = new HashMap<>();
        // Create some data to merge
        realDiffMap.put("repo", new AtomicLong(10000L));
        backupDiffMap.put("repo", new AtomicLong(99L));
        backupDiffMap.put("repo2", new AtomicLong(100L));
        // Act
        storageService.mergeDiffChanges(backupDiffMap, realDiffMap);
        // Validate the merged data contains both source and target maps
        assertEquals(realDiffMap.get("repo").get(), 10099L);
        assertEquals(realDiffMap.get("repo2").get(), 100L);
    }

    @Test
    void testGetAllProjectsNotifiedOnStorageQuota() {
        StorageServiceImpl storageService = initStorageService();
        when(configsService.getConfig(ArtifactoryHome.STORAGE_USAGE_FILE)).thenReturn(null);

        // storage usage info must not be empty
        assertThrows(IllegalStateException.class, storageService::getAllProjectsNotifiedOnStorageQuota);

        Map<String, Long> notifiedProjects = Map.of("p1", 0L, "p2", 1L);
        StorageUsage storageUsage = new StorageUsage();
        storageUsage.setProjectsLastNotifiedOnStorageQuota(notifiedProjects);
        String storageUsageString = JsonUtils.getInstance().writeValueAsString(storageUsage);
        when(configsService.getConfig(ArtifactoryHome.STORAGE_USAGE_FILE)).thenReturn(storageUsageString);

        Map<String, Long> notifiedProjectsResult = storageService.getAllProjectsNotifiedOnStorageQuota();
        assertEquals(notifiedProjectsResult.size(), 2);
    }

    @Test
    void testUpdateProjectStorageQuotaNotificationTimestamp() {
        Set<String> projects = Set.of("p1");
        StorageServiceImpl storageService = initStorageService();
        StorageUsage storageUsage = new StorageUsage();
        when(configsService.getConfig(ArtifactoryHome.STORAGE_USAGE_FILE))
                .thenReturn(JsonUtils.getInstance().writeValueAsString(storageUsage));
        when(configsService
                .updateConfigIfLastModificationMatch(eq(ArtifactoryHome.STORAGE_USAGE_FILE), anyString(), anyLong(),
                        anyLong()))
                .thenReturn(false)
                .thenReturn(true);

        storageService.updateProjectStorageQuotaNotificationTimestamp(projects);

        verify(configsService, times(2)).updateConfigIfLastModificationMatch(eq(ArtifactoryHome.STORAGE_USAGE_FILE),
                argThat(storageUsageString -> {
                    StorageUsage storageUsageArgument =
                            JsonUtils.getInstance().readValue(storageUsageString, StorageUsage.class);

                    long timeDelta =
                            DateTime.now().minus(storageUsageArgument.getProjectsLastNotifiedOnStorageQuota().get("p1"))
                                    .getMillis();
                    boolean projectLastNotifiedIsCloseToNow = timeDelta < 5000; // within 5 seconds range
                    return storageUsageArgument.getProjectsLastNotifiedOnStorageQuota().size() == 1 &&
                            projectLastNotifiedIsCloseToNow;
                }), eq(1L), eq(2L));
    }

    /**
     * Reproduces RTDEV-88337 end-to-end at the persistence level: the quota notification job records a per-project
     * cooldown timestamp, then the storage summary recalculation job runs. Both jobs read/write the same
     * storage.usage.json config through {@link #wireInMemoryConfigsStore()} (with real compare-and-set semantics),
     * so the cooldown timestamp must survive the recalculation. Without the fix in
     * {@code StorageServiceImpl.saveAccurateStorageUsageState} this test fails because the map is wiped to {}.
     */
    @Test
    void testQuotaCooldownSurvivesStorageSummaryRecalculation() throws InterruptedException {
        StorageServiceImpl storageService = initStorageService();
        setStorageSummaryCacheEnable(false, storageService);
        setMocks();
        when(storageSummaryService.updateAndGet()).thenReturn(createStorageSummary());
        // Short-circuit the in-memory cache refresh that runs after a successful save
        when(storageSummaryService.getLocalCache()).thenThrow(CacheUnAvailableException.class);
        wireInMemoryConfigsStore();

        // Step 1 - quota notification job: emails project "p1" and records its cooldown timestamp
        storageService.updateProjectStorageQuotaNotificationTimestamp(Set.of("p1"));
        StorageUsage afterNotification = readStoredStorageUsage();
        Long recordedTimestamp = afterNotification.getProjectsLastNotifiedOnStorageQuota().get("p1");
        assertTrue(recordedTimestamp != null,
                "Cooldown timestamp should be recorded right after a notification is sent");
        assertEquals(2L, afterNotification.getRevision()); // bumped from the seeded revision 1

        // Step 2 - storage summary job: full recalculation (this is what used to wipe the cooldown map)
        storageService.calculateStorageSummary();

        // Step 3 - evidence: the recalculation DID run and re-persisted a brand-new state (revision 2 -> 3),
        // yet the cooldown map is intact and the timestamp value is byte-for-byte the same (not reset, not changed).
        StorageUsage afterRecalculation = readStoredStorageUsage();
        assertEquals(3L, afterRecalculation.getRevision()); // proves the job wrote a new StorageUsage, not skipped
        assertEquals(Map.of("p1", recordedTimestamp), afterRecalculation.getProjectsLastNotifiedOnStorageQuota(),
                "Cooldown map must survive the storage summary recalculation unchanged (RTDEV-88337)");
    }

    private StorageUsage readStoredStorageUsage() {
        String storedJson = configsService.getConfig(ArtifactoryHome.STORAGE_USAGE_FILE);
        return JsonUtils.getInstance().readValue(storedJson, StorageUsage.class);
    }

    /**
     * Makes the mocked {@link ConfigsService} behave like a real single-row store for storage.usage.json,
     * including the optimistic compare-and-set used by {@code updateConfigIfLastModificationMatch}, so that the
     * two jobs in {@link #testQuotaCooldownSurvivesStorageSummaryRecalculation()} operate on shared state.
     */
    private void wireInMemoryConfigsStore() {
        String key = ArtifactoryHome.STORAGE_USAGE_FILE;
        Map<String, String> dataStore = new HashMap<>();
        Map<String, Long> revisionStore = new HashMap<>();
        // Seed an initial StorageUsage (revision 1, empty cooldown map), as if quota was just enabled
        dataStore.put(key, JsonUtils.getInstance().writeValueAsString(new StorageUsage()));
        revisionStore.put(key, 1L);

        when(configsService.getConfig(key)).thenAnswer(inv -> dataStore.get(key));
        when(configsService.hasConfig(key)).thenAnswer(inv -> dataStore.containsKey(key));
        doAnswer(inv -> {
            dataStore.put(inv.getArgument(0), inv.getArgument(1));
            revisionStore.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(configsService).addConfig(eq(key), anyString(), anyLong());
        when(configsService.updateConfigIfLastModificationMatch(eq(key), anyString(), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    long expectedRevision = inv.getArgument(2);
                    long newRevision = inv.getArgument(3);
                    if (revisionStore.getOrDefault(key, -1L) == expectedRevision) {
                        dataStore.put(key, inv.getArgument(1));
                        revisionStore.put(key, newRevision);
                        return true;
                    }
                    return false;
                });
    }

    @Test
    void testActivateStorageUsageSyncJobIfNeeded() {
        StorageServiceImpl storageService = initStorageService();

        storageService.onLicenseLoaded();
        verify(taskService).startTask(
                argThat((task) -> task.getType().equals(SyncStorageUsageJob.class)), eq(false));
    }

    @Test
    void testCallManualShardingBalancer() {
        when(binaryStore.isNeedOptimization()).thenReturn(true);
        StorageServiceImpl storageService = initStorageService();
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        storageService.callManualShardingBalancer(statusHolder);
        assertFalse(statusHolder.isError());
    }

    @Test
    void testCallManualGarbageCollect() {
        StorageServiceImpl storageService = initStorageService();
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        storageService.callManualGarbageCollect(statusHolder);
        assertFalse(statusHolder.isError());
    }

    @ParameterizedTest
    @MethodSource("testGcSchedulerHandlerCron")
    void testGcSchedulerHandlerCron(String serviceId, boolean isAol, String interval, String expectedCronExpr) {
        when((aolService.isAol())).thenReturn(isAol);
        try (MockedStatic<TaskUtils> taskUtilsMock = mockStatic(TaskUtils.class)) {
            Task task = mock(Task.class);
            taskUtilsMock.when(() -> TaskUtils.createCronTask(any(), anyString(), anyString())).thenReturn(task);

            GcConfigDescriptor gcConfigDescriptor = mock(GcConfigDescriptor.class);
            when(gcConfigDescriptor.getCronExp()).thenReturn(expectedCronExpr);

            setStringSystemProperty(ConstantValues.gcIntervalSecs, interval);

            StorageServiceImpl.GcSchedulerHandler x = new StorageServiceImpl.GcSchedulerHandler(null, null, serviceId);
            x.activate(gcConfigDescriptor, false, null);

            ArgumentCaptor<String> localOnlyArgumentCaptor = ArgumentCaptor.forClass(String.class);
            taskUtilsMock.verify(() ->
                    TaskUtils.createCronTask(any(), localOnlyArgumentCaptor.capture(), anyString()));
            assertNotNull(localOnlyArgumentCaptor.getValue());
            assertEquals(expectedCronExpr, localOnlyArgumentCaptor.getValue());
        }
    }

    private static Stream<Arguments> testGcSchedulerHandlerCron() {
        String defaultCronEx = "0 0 22 ? * *";
        String serviceIDBasedCronEx = "0 28 16 ? * *";
        String halfHour = String.valueOf(TimeUnit.MINUTES.toSeconds(30L));  // 1800
        String oneHour = String.valueOf(TimeUnit.HOURS.toSeconds(1L));      // 3600
        String halfDay = String.valueOf(TimeUnit.HOURS.toSeconds(12L));     // 43200
        String oneDay = String.valueOf(TimeUnit.HOURS.toSeconds(24L));      // 86400
        String dayAndHalf = String.valueOf(TimeUnit.HOURS.toSeconds(36L));  // 129600
        return Stream.of(
                Arguments.of(null, false, oneDay, defaultCronEx),
                Arguments.of("ABED", false, oneDay, serviceIDBasedCronEx),
                Arguments.of(null, true, oneDay, defaultCronEx), // default because of null serviceID
                Arguments.of("ABED", true, halfHour, serviceIDBasedCronEx), // out of 1-23 range -> random time
                Arguments.of("ABED", true, oneHour, "0 28 0/1 ? * *"), // fire every hour stating at xx:28
                Arguments.of("ABED", true, halfDay, "0 28 4/12 ? * *"), // fire twice a day stating at 04:28
                Arguments.of("ABED", true, oneDay, serviceIDBasedCronEx), // fire once a day stating at 04:28
                Arguments.of("ABED", true, "86401", serviceIDBasedCronEx), // out of 1-23 range -> random time
                Arguments.of("ABED", true, dayAndHalf, serviceIDBasedCronEx) // out of 1-23 range -> random time
        );
    }

    private StorageServiceImpl initStorageService() {
        StorageServiceImpl storageService = spy(new StorageServiceImpl());
        storageService.setAddonsManager(addonsManager);
        storageService.setBinaryStore(binaryStore);
        storageService.setCentralConfigService(centralConfigService);
        storageService.setDbService(dbService);
        storageService.setTaskService(taskService);
        storageService.setRepositoryService(repositoryService);
        storageService.setProjectsService(projectsService);
        storageService.setConfigsService(configsService);
        storageService.setStorageSummaryService(storageSummaryService);
        when(dbService.getDatabaseType()).thenReturn(DbType.MYSQL);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.getQuotaConfig()).thenReturn(createQuotaConfigDescriptor());
        doNothing().when(storageService).rescheduleGC(centralConfigDescriptor, null);
        doReturn(true).when(storageService).isProjectsStorageQuotaEnabled();
        storageService.init();
        when(storageSummaryService.getLocalCache()).thenReturn(populateStorageSummaryCache());
        storageService.clearStorageDiffs();
        return storageService;
    }

    private QuotaConfigDescriptor createQuotaConfigDescriptor() {
        QuotaConfigDescriptor quotaConfig = new QuotaConfigDescriptor();
        quotaConfig.setEnabled(true);
        quotaConfig.setDiskSpaceWarningPercentage(70);
        quotaConfig.setDiskSpaceLimitPercentage(90);
        return quotaConfig;
    }

    private StorageSummaryInfo populateStorageSummaryCache() {
        StorageSummaryInfo summaryInfo =
                new StorageSummaryInfo(new HashMap<>(), null, new HashMap<>(), new HashMap<>());
        summaryInfo.getProjectsTotalSizes().putIfAbsent(PROJECTX, 1000L);
        return summaryInfo;
    }

    public static DbMutableFile prepareDbMutableFileMock(long size) {
        RepoPathImpl repoPath = new RepoPathImpl(PROJ_REPO1, "test.file");
        FileInfoImpl fileInfo = new FileInfoImpl(repoPath);
        fileInfo.setSize(size);
        return new DbMutableFile(mock(StoringRepo.class), 52437842367L, fileInfo);
    }
}