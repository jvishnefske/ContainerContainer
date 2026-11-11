package org.artifactory.storage.service;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.hqc.enums.HeavyQueryCacheType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.sh.acl.ArtifactoryBinaryService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.RepoStorageSummary;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.binstore.BinariesInfo;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.jfrog.storage.hqc.service.HeavyQueryCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.testng.Assert;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author annakv
 */
class StorageSummaryServiceImplTest extends ArtifactoryHomeBoundTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    RepositoryService repositoryService;
    @Mock
    ProjectsService projectsService;
    @Mock
    ArtifactoryBinaryService binaryStore;
    @Mock
    FileService fileService;
    @Mock
    HeavyQueryCacheService heavyQueryCacheService;
    private StorageSummaryService service;
    private AutoCloseable autoCloseable;
    @Mock
    InternalArtifactoryContext context;

    @BeforeEach
    public void init() {
        bindArtifactoryHome();
        autoCloseable = openMocks(this);
        service = new StorageSummaryServiceImpl(repositoryService, projectsService, binaryStore,
                fileService, addonsManager, heavyQueryCacheService);
        when(context.beanForType(StorageSummaryService.class)).thenReturn(service);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void cleanup() throws Exception {
        autoCloseable.close();
        ArtifactoryContextThreadBinder.unbind();
        unbindArtifactoryHome();
    }

    @Test
    void testGetSummaryInfo() {
        SupportAddon supportAddon = mock(SupportAddon.class);
        when(addonsManager.addonByType(SupportAddon.class)).thenReturn(supportAddon);
        Set<RepoStorageSummary> summaries = Set.of(new RepoStorageSummary("repoKey", 2, 5, 100));
        when(fileService.getRepositoriesStorageSummary()).thenReturn(summaries);
        when(binaryStore.getBinariesInfo()).thenReturn(new BinariesInfo());
        when(heavyQueryCacheService.get(any(), eq(StorageSummaryInfo.class), eq(""),
                eq(HeavyQueryCacheType.STORAGE_INFO), any(), eq(true))).thenAnswer(invocation -> {
                    Callable<StorageSummaryInfo> callable = invocation.getArgument(0);
                    return callable.call();
                });
        StorageSummaryService service = new StorageSummaryServiceImpl(repositoryService, projectsService, binaryStore,
                fileService, addonsManager, heavyQueryCacheService);
        StorageSummaryInfo info = service.updateAndGet();
        assertNotNull(info);
        assertNotNull(info.getBinariesInfo());
        assertEquals(100, Objects.requireNonNull(info.getRepoStorageSummary("repoKey")).getUsedSpace());
        verify(repositoryService, times(1)).getLocalFederatedAndCachedRepoConfigs();
        verify(repositoryService, times(1)).getVirtualRepoConfigs();
        verify(fileService, times(1)).getRepositoriesStorageSummary();
        verify(binaryStore, times(1)).getBinariesInfo();
        verify(heavyQueryCacheService, times(1)).get(any(), eq(StorageSummaryInfo.class), eq(""),
                eq(HeavyQueryCacheType.STORAGE_INFO), any(), eq(true));
    }

    @Test
    void testGetSummaryInfoFromDb() {
        homeStub.setProperty(ConstantValues.storageSummaryHqcEnable, "false");
        SupportAddon supportAddon = mock(SupportAddon.class);
        when(addonsManager.addonByType(SupportAddon.class)).thenReturn(supportAddon);
        Set<RepoStorageSummary> summaries = Set.of(new RepoStorageSummary("repoKey", 2, 5, 100));
        when(fileService.getRepositoriesStorageSummary()).thenReturn(summaries);
        when(binaryStore.getBinariesInfo()).thenReturn(new BinariesInfo());
        StorageSummaryService service = new StorageSummaryServiceImpl(repositoryService, projectsService, binaryStore,
                fileService, addonsManager, heavyQueryCacheService);
        StorageSummaryInfo info = service.updateAndGet();
        assertNotNull(info);
        assertNotNull(info.getBinariesInfo());
        assertEquals(100, Objects.requireNonNull(info.getRepoStorageSummary("repoKey")).getUsedSpace());
        verify(repositoryService, times(1)).getLocalFederatedAndCachedRepoConfigs();
        verify(repositoryService, times(1)).getVirtualRepoConfigs();
        verify(fileService, times(1)).getRepositoriesStorageSummary();
        verify(binaryStore, times(1)).getBinariesInfo();
    }

    @Test
    void testGetStorageSummaryInfoHqc() {
        SupportAddon supportAddon = mock(SupportAddon.class);
        when(addonsManager.addonByType(SupportAddon.class)).thenReturn(supportAddon);
        Set<RepoStorageSummary> summaries = Set.of(new RepoStorageSummary("repoKey", 2, 5, 100));
        when(fileService.getRepositoriesStorageSummary()).thenReturn(summaries);
        when(binaryStore.getBinariesInfo()).thenReturn(new BinariesInfo());
        StorageSummaryInfo info = service.getStorageSummaryInfoHqc();
        assertNotNull(info);
    }

    private static Object[][] repoDescriptorToRepoTypeDataProvider() {
        LocalConfig mocked = mock(LocalConfig.class);
        when(mocked.getKey()).thenReturn("test-repo");
        when(mocked.getPackageType()).thenReturn(RepoType.Generic);
        return new Object[][] {
                {FederatedRepoConfig.create("test-repo",
                        RepoType.Generic), RepoStorageSummaryInfo.RepositoryType.FEDERATED},
                {LocalRepoConfig.create("test-repo", RepoType.Generic),
                        RepoStorageSummaryInfo.RepositoryType.LOCAL},
                {LocalCacheRepoConfig.create(RemoteRepoConfig.create("test-repo", RepoType.Generic)),
                        RepoStorageSummaryInfo.RepositoryType.CACHE},
                {mocked, RepoStorageSummaryInfo.RepositoryType.NA}
        };
    }

    @ParameterizedTest
    @MethodSource("repoDescriptorToRepoTypeDataProvider")
    void testRepositoryDescriptorToRepositoryType(LocalConfig config,
            RepoStorageSummaryInfo.RepositoryType expectedRepoType) {
        SupportAddon supportAddon = mock(SupportAddon.class);
        when(addonsManager.addonByType(SupportAddon.class)).thenReturn(supportAddon);
        when(repositoryService.getLocalFederatedAndCachedRepoConfigs()).thenReturn(List.of(config));
        String repoKey = config instanceof LocalCacheRepoConfig ? "test-repo-cache" : "test-repo";
        Set<RepoStorageSummary> summaries = Set.of(new RepoStorageSummary(repoKey, 2, 5, 100));
        when(fileService.getRepositoriesStorageSummary()).thenReturn(summaries);
        when(heavyQueryCacheService.get(any(), eq(StorageSummaryInfo.class), eq(""),
                eq(HeavyQueryCacheType.STORAGE_INFO), any(), eq(false))).thenAnswer(invocation -> {
                    Callable<StorageSummaryInfo> callable = invocation.getArgument(0);
                    return callable.call();
                });
        StorageSummaryInfo info = service.get();

        var testRepoInfo = info.getRepoStorageSummary(repoKey);
        assertNotNull(testRepoInfo);
        Assert.assertEquals(testRepoInfo.getRepoType(), expectedRepoType);
    }

    @Test
    void testGetSummaryGenericException() {
        SupportAddon supportAddon = mock(SupportAddon.class);
        when(addonsManager.addonByType(SupportAddon.class)).thenReturn(supportAddon);
        doThrow(new HeavyQueryCacheException("")).when(heavyQueryCacheService).get(any(), any(), any(), any(),
                any(), anyBoolean());
        service.updateAndGet();
        verify(repositoryService, times(1)).getLocalFederatedAndCachedRepoConfigs();
    }

    @Test
    void testGetCache() {
        StorageSummaryInfo expectedInfo = new StorageSummaryInfo();
        service.replaceLocalCache(expectedInfo);
        StorageSummaryInfo info = service.getLocalCache();
        assertEquals(expectedInfo, info);
    }

    @Test
    void testGetCacheNull() {
        assertThrows(CacheUnAvailableException.class, () -> service.getLocalCache());
    }

}
