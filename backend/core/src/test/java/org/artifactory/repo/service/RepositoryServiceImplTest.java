package org.artifactory.repo.service;

import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.ImportSettingsImpl;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.config.RepositoryImportSettingsImpl;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.ArchiveFileContent;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.storage.StorageQuotaInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoResolver;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.model.BaseConfig;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RealRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.ReleaseBundlesRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoConfigChangeReload;
import org.artifactory.repo.RepoConfigChangesReload;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.cleanup.FolderPruningService;
import org.artifactory.repo.db.DbCacheRepo;
import org.artifactory.repo.federated.FederatedRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.local.PathDeletionContext;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.repo.service.flexible.interfaces.FlatMoveCopyService;
import org.artifactory.repo.service.mover.MoveRepoPathService;
import org.artifactory.repo.service.mover.MoverConfig;
import org.artifactory.repo.service.versioning.RepositoriesCache;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.RepositoryRuntimeException;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.monitor.OnlineMonitorService;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.security.PermissionTarget;
import org.artifactory.security.RepoPermissionTarget;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.security.service.VersioningCacheImpl;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.notify.NotifyClient;
import org.jfrog.access.common.ResourceType;
import org.jfrog.storage.binstore.exceptions.BinaryNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_REQUEST_TOO_LONG;
import static org.artifactory.api.request.ArtifactoryResponse.ARTIFACTORY_ID;
import static org.artifactory.common.ConstantValues.externalSystemRepositoriesList;
import static org.artifactory.common.ConstantValues.nodeLastUpdatedThresholdMillis;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.descriptor.repo.RepoType.PipeInfo;
import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.ARTIFACT_NOT_FOUND;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.ITEM_NOT_FOUND;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.REPOSITORY_NOT_FOUND;
import static org.artifactory.repo.service.ArtifactDeleteFailStatus.REQUIRES_DELETE_PERMISSION;
import static org.artifactory.repo.system.SystemFolders.SystemFolder;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Omri Ziv
 */
@Slf4j
class RepositoryServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    protected RepositoryServiceImpl repositoryService;
    @Mock
    protected InternalRepositoryService internalRepositoryService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private RepositoriesCache repositoriesCache;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private LocalCacheRepo repo1Cache;
    @Mock
    private ZapService zapService;
    @Mock
    private LocalRepo<?, ?> repo2Cache;
    @Mock
    private LocalRepo<?, LocalConfig> trash;
    @Mock
    private LocalRepo<?, LocalConfig> supportBundle;
    @Mock
    private FolderPruningService pruneService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private InternalRepositoryConfigService internalRepositoryConfigService;
    @Mock
    protected StorageInterceptors storageInterceptors;
    @Mock
    private PackageSpecification packageSpecification;
    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private MoveRepoPathService moveRepoPathService;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private AccessService accessService;
    @Captor
    private ArgumentCaptor<MoverConfig> moverConfigCaptor;
    @Mock
    private TaskService taskService;
    @Mock
    private InternalArtifactoryContext internalArtifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private FederatedRepoAddon federatedRepoAddon;
    private AutoCloseable mocks;
    private RepoPath repoPath1;
    private RepoPath repoPath2;
    protected FileService fileService;

    public static Stream<Arguments> getRepositoryConfigByKeyParams() {
        return Stream.of(
                Arguments.of(repoConfig(LOCAL.getConfigName(), Generic).managed(true).build()),
                Arguments.of(repoConfig(REMOTE.getConfigName(), Generic).managed(true).build()),
                Arguments.of(repoConfig(VIRTUAL.getConfigName(), Generic).managed(true).build()),
                Arguments.of(repoConfig(RELEASE_BUNDLE.getConfigName(), Generic).managed(false).build()),
                Arguments.of(repoConfig("cache", Generic).managed(false).build()),
                Arguments.of(repoConfig("trash", Generic).managed(false).build())
        );
    }

    @Test
    void assertStorageQuota_infoNull_callsProjectAssert_noError() {
        StorageService storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(repositoryService, "storageService", storageService);

        when(storageService.getStorageQuotaInfo(anyLong())).thenReturn(null);

        BasicStatusHolder status = new BasicStatusHolder();
        String repoKey = "build-info-repo";
        long contentLength = 42L;

        repositoryService.assertStorageQuota(repoKey, status, contentLength);

        assertFalse(status.hasErrors());
        verify(projectsService).assertProjectStorageQuota(eq(repoKey), eq(status), eq(contentLength));
    }

    @Test
    void assertStorageQuota_limitReached_setsError_andCallsProjectAssert() {
        StorageService storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(repositoryService, "storageService", storageService);

        StorageQuotaInfo quotaInfo = mock(StorageQuotaInfo.class);
        when(quotaInfo.isLimitReached()).thenReturn(true);
        when(quotaInfo.isWarningLimitReached()).thenReturn(false);
        when(storageService.getStorageQuotaInfo(anyLong())).thenReturn(quotaInfo);

        BasicStatusHolder status = new BasicStatusHolder();
        String repoKey = "build-info-repo";
        long contentLength = 99L;

        repositoryService.assertStorageQuota(repoKey, status, contentLength);

        assertTrue(status.hasErrors());
        assertEquals(SC_REQUEST_TOO_LONG, status.getStatusCode());
        verify(projectsService).assertProjectStorageQuota(eq(repoKey), eq(status), eq(contentLength));
    }

    @Test
    void assertStorageQuota_warningReached_noError_andCallsProjectAssert() {
        StorageService storageService = mock(StorageService.class);
        ReflectionTestUtils.setField(repositoryService, "storageService", storageService);

        StorageQuotaInfo quotaInfo = mock(StorageQuotaInfo.class);
        when(quotaInfo.isLimitReached()).thenReturn(false);
        when(quotaInfo.isWarningLimitReached()).thenReturn(true);
        when(storageService.getStorageQuotaInfo(anyLong())).thenReturn(quotaInfo);

        BasicStatusHolder status = new BasicStatusHolder();
        String repoKey = "build-info-repo";
        long contentLength = 123L;

        repositoryService.assertStorageQuota(repoKey, status, contentLength);

        assertFalse(status.hasErrors());
        verify(projectsService).assertProjectStorageQuota(eq(repoKey), eq(status), eq(contentLength));
    }

    public static Stream<List<String>> repoKeysProvider() {
        return Stream.of(List.of("local-repo-1", "local-repo-2", USAGE_REPO_KEY),
                List.of());
    }

    @Builder
    private record Args(RepoConfig repoConfig, boolean managed) {
    }

    private static Args.ArgsBuilder repoConfig(String repoType, RepoType packageType) {
        RepoConfig repoConfig = switch (repoType) {
            case "local" -> LocalRepoConfig.create("local", packageType);
            case "remote" -> RemoteRepoConfig.create("remote", packageType);
            case "virtual" -> VirtualRepoConfig.create("virtual", packageType);
            case "release bundle" -> ReleaseBundleRepoConfig.create("releaseBundle", packageType);
            case "cache" -> LocalCacheRepoConfig.create(RemoteRepoConfig.create("remote", packageType));
            case "trash" -> LocalRepoConfig.create(TRASH_KEY, packageType);
            default -> throw new IllegalStateException("Unexpected value: " + repoType);
        };
        return Args.builder().repoConfig(repoConfig);
    }


    @BeforeEach
    void beforeEach() {
        mocks = openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        setStringSystemProperty(externalSystemRepositoriesList, "a,b,c,d-d    , e");
        repositoryService = spy(new RepositoryServiceImpl());
        fileService = spy(FileService.class);
        repositoryService.initExternalSystemRepos();
        setField(repositoryService, "authService", authorizationService);
        setField(repositoryService, "securityService", securityService);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(securityService).doAsSystem(any(Runnable.class));
        setField(repositoryService, "fileService", fileService);
        setField(repositoryService, "pruneService", pruneService);
        setField(repositoryService, "buildFacade", buildFacade);
        setField(repositoryService, "storageInterceptors", storageInterceptors);
        setField(repositoryService, "projectsService", projectsService);
        setField(repositoryService, "propertiesService", propertiesService);
        setField(repositoryService, "zapService", zapService);
        setField(repositoryService, "repositoryConfigService", repositoryConfigService);
        setField(repositoryService, "accessService", accessService);
        setField(repositoryService, "addonsManager", addonsManager);
        when(addonsManager.addonByType(FederatedRepoAddon.class)).thenReturn(federatedRepoAddon);

        Map<String, LocalRepo> localRepoMap = new HashMap<>();
        Map<String, LocalCacheRepo> localCacheRepoMap = new HashMap<>();
        when(repo1Cache.isCache()).thenReturn(true);
        when(trash.getKey()).thenReturn(TRASH_KEY);
        when(supportBundle.getKey()).thenReturn(SUPPORT_BUNDLE_REPO_KEY);
        LocalConfig mocked = mock(LocalConfig.class);
        when(trash.getRepoConfig()).thenReturn(mocked);
        when(supportBundle.getRepoConfig()).thenReturn(mocked);
        localRepoMap.put("repo1-cache", repo1Cache);
        localRepoMap.put("repo2", repo2Cache);
        localCacheRepoMap.put("repo1-cache", repo1Cache);
        repositoriesCache = new RepositoriesCache(new HashMap<>(), new HashMap<>(), new HashMap<>(), localCacheRepoMap,
                new HashMap<>(), localRepoMap, trash, new HashSet<>(), supportBundle, new HashMap<>());
        repoPath1 = new RepoPathImpl("repo1-cache",
                "/mysql/connector-java/5.1.7-SNAPSHOT/connector-java-5.1.7-20190219.131101-4.jar");
        repoPath2 = new RepoPathImpl("repo2",
                "/mysql/connector-java/5.1.7-SNAPSHOT/connector-java-5.1.7-20190219.131101-4.jar");
        VersioningCacheImpl<RepositoriesCache> cache = mock(VersioningCacheImpl.class);
        repositoryService.cache = cache;
        when(cache.get()).thenReturn(repositoriesCache);
        when(repo1Cache.getRepoConfig()).thenReturn(mock(LocalCacheRepoConfig.class));
        repositoryService.setPackageSpecification(packageSpecification);
        when(context.beanForType(InternalRepositoryConfigService.class)).thenReturn(internalRepositoryConfigService);
        when(context.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        when(context.beanForType(MoveRepoPathService.class)).thenReturn(moveRepoPathService);
    }

    @AfterEach
    void afterEach() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void testGetChildrenStream() {
        RepoPath repoPath = RepoPathFactory.create("repo", "a");
        repositoryService.getChildrenStream(repoPath);
        verify(fileService, times(1)).getChildrenStream(repoPath);
    }

    @Test
    void testGetNodesStreamIgnoringRepo() {
        RepoPath repoPath = RepoPathFactory.create("repo", "path");
        repositoryService.getNodesStreamIgnoringRepo(repoPath);
        verify(fileService, times(1)).getNodesStreamIgnoringRepo(repoPath);
    }

    @ParameterizedTest
    @MethodSource("getRepositoryConfigByKeyParams")
    void testGetRepositoryConfigByKey(Args args) {
        InternalRepositoryService repositoryService = spy(new RepositoryServiceImpl());
        RepoConfig repoConfig = args.repoConfig;
        if (args.managed) {
            setField(repositoryService, "repositoryConfigService", repositoryConfigService);
            when(internalRepositoryConfigService.isManaged(repoConfig.getKey())).thenReturn(true);
            when(repositoryConfigService.getRepoConfigByKey(repoConfig.getKey())).thenReturn(repoConfig);
        } else {
            Repo<?, RepoConfig> repo = mock(Repo.class);
            when(repo.getRepoConfig()).thenReturn(repoConfig);
            doReturn(repo).when(repositoryService).repositoryByKey(repoConfig.getKey());
        }
        assertNotNull(repositoryService.getRepoConfigByKey(repoConfig.getKey()));
    }

    @Test
    public void testZapWithDeletePermission() {
        when(authorizationService.canDelete(repoPath1)).thenReturn(true);
        repositoryService.zap(repoPath1);
        verify(zapService, times(1)).zap(eq(repoPath1),
                any(LocalCacheRepo.class), any(LongSupplier.class));
    }

    @Test
    public void testZapWithManagePermission() {
        when(authorizationService.canManage(repoPath1)).thenReturn(true);
        repositoryService.zap(repoPath1);
        verify(zapService, times(1)).zap(eq(repoPath1),
                any(LocalCacheRepo.class), any(LongSupplier.class));
    }

    @Test
    public void testZapNotPermitted() {
        when(authorizationService.canDelete(repoPath1)).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> repositoryService.zap(repoPath1));
    }

    @Test
    public void testZapNotCachedRepo() {
        when(authorizationService.canDelete(repoPath2)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> repositoryService.zap(repoPath2));
    }

    @Test
    public void testZapTrash() {
        RepoPath repoPathTrash = RepoPathUtils.repoRootPath(TRASH_KEY);
        when(authorizationService.canDelete(repoPathTrash)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> repositoryService.zap(repoPathTrash));
    }

    @Test
    void testIsExternalSystemRepo() {
        assertTrue(repositoryService.isExternalSystemRepo("a"));
        assertTrue(repositoryService.isExternalSystemRepo("b"));
        assertTrue(repositoryService.isExternalSystemRepo("c"));
        assertTrue(repositoryService.isExternalSystemRepo("d-d"));
        assertTrue(repositoryService.isExternalSystemRepo("e"));
        assertFalse(repositoryService.isExternalSystemRepo(""));
        assertFalse(repositoryService.isExternalSystemRepo("f"));
    }

    @Test
    void testIsFolderContainsLeadArtifacts() {
        RepoPath repoPath = RepoPathFactory.create("repo", "a");
        repositoryService.isFolderContainsArtifacts(repoPath, "manifest.json");
        verify(fileService, times(1)).isFolderContainsArtifacts(repoPath, "manifest.json");
    }

    @Test
    void testUndeployNotExistItem_NoErrorsOrWarning_Return404() {
        RepoPath repoPath = RepoPathFactory.create("repo-key", "folder");
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getImmutableFsItem(repoPath)).thenReturn(null);
        when(repositoryService.storingRepositoryByKey("repo-key")).thenReturn(localRepo);
        doNothing().when(repositoryService).assertDeleteIncludingRoot(any());
        when(repositoryService.localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey())).thenReturn(localRepo);
        BasicStatusHolder statusHolder = repositoryService.undeployMultiTransaction(repoPath);
        assertFalse(statusHolder.hasErrorsOrWarnings());
        assertEquals(ITEM_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
        assertEquals(ITEM_NOT_FOUND.getReason(repoPath.toPrettyPath()), statusHolder.getStatusMsg());
    }

    @Test
    void testUndeployRepoDoesNotExist_NoErrorsOrWarnings_Return404() {
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-path");
        BasicStatusHolder statusHolder = repositoryService.undeploy(repoPath);
        assertFalse(statusHolder.hasErrorsOrWarnings());
        assertEquals(REPOSITORY_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
        assertEquals(REPOSITORY_NOT_FOUND.getReason(repoPath.getRepoKey()), statusHolder.getStatusMsg());
        statusHolder = repositoryService.undeployMultiTransaction(repoPath);
        assertFalse(statusHolder.hasErrorsOrWarnings());
        assertEquals(REPOSITORY_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
        assertEquals(REPOSITORY_NOT_FOUND.getReason(repoPath.getRepoKey()), statusHolder.getStatusMsg());
        statusHolder = repositoryService.undeploy(repoPath);
        assertFalse(statusHolder.hasErrorsOrWarnings());
        assertEquals(REPOSITORY_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
        assertEquals(REPOSITORY_NOT_FOUND.getReason(repoPath.getRepoKey()), statusHolder.getStatusMsg());
        statusHolder = repositoryService.undeployMultiTransaction(repoPath);
        assertFalse(statusHolder.hasErrorsOrWarnings());
        assertEquals(REPOSITORY_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
        assertEquals(REPOSITORY_NOT_FOUND.getReason(repoPath.getRepoKey()), statusHolder.getStatusMsg());
    }


    @Test
    void onRepoConfigReloadNoRepoFoundForDeletionOrUpdateStopMonitoringAndPromoteCache() {
        // Given
        repoConfigReloadPrep repoConfigReloadPrep = getRepoConfigReloadPrep();

        // When
        repositoryService.onRepoConfigReload(repoConfigReloadPrep.changes());

        // Then
        verify(repoConfigReloadPrep.onlineMonitorService()).stopMonitorForRepo("remote-key-delete");
        verify(repoConfigReloadPrep.onlineMonitorService()).stopMonitorForRepo("remote-key-update");
        verify(repoConfigReloadPrep.cache()).promoteVersion();
    }

    @Test
    void onPropagateRepoConfigReload() {
        // Given
        repoConfigReloadPrep repoConfigReloadPrep = getRepoConfigReloadPrep();

        // When
        repositoryService.onPropagatedRepoConfigReload(repoConfigReloadPrep.changes());

        // Then
        verify(repoConfigReloadPrep.onlineMonitorService()).stopMonitorForRepo("remote-key-delete");
        verify(repoConfigReloadPrep.onlineMonitorService()).stopMonitorForRepo("remote-key-update");
        verify(repoConfigReloadPrep.cache()).promoteVersion();
    }

    @Test
    void testUndeployInTx() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        doThrow(new CancelException("Exception thrown", 0)).when(repo1Cache).undeploy(any(), any());
        repositoryService.undeployInTx(repoPath1, false, statusHolder);
        assertEquals(
                "Undeploy of path: 'repo1-cache/mysql/connector-java/5.1.7-SNAPSHOT/connector-java-5.1.7-20190219" +
                        ".131101-4.jar' was canceled: Exception thrown",
                statusHolder.getStatusMsg());
    }

    @Test
    void testUndeployInTxRepoNotFound() {
        boolean deleted = repositoryService.undeployInTx(RepoPathFactory.create("non-existent-repo"), false,
                new BasicStatusHolder());
        assertTrue(deleted);
    }

    @Test
    void testUndeployInTxFullRepoContentDeletion() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        ArgumentCaptor<DeleteContext> deleteContextCaptor = ArgumentCaptor.forClass(DeleteContext.class);

        boolean deleted = repositoryService.undeployInTx(repoPath1, true, statusHolder, true);

        assertTrue(deleted);
        verify(repo1Cache).undeploy(deleteContextCaptor.capture(), eq(statusHolder));
        DeleteContext deleteContext = deleteContextCaptor.getValue();
        assertTrue(deleteContext.isTriggerMetadataCalculation());
        assertTrue(deleteContext.isFullRepoContentDeletion());
    }

    @Test
    void testUndeployInTxFullRepoContentDeletionForUsageRepoSkipsTrashcan() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath usageRepoPath = RepoPathFactory.create(USAGE_REPO_KEY, "usage.json");
        ArgumentCaptor<DeleteContext> deleteContextCaptor = ArgumentCaptor.forClass(DeleteContext.class);
        doReturn(repo1Cache).when(repositoryService).storingRepositoryByKey(USAGE_REPO_KEY);

        boolean deleted = repositoryService.undeployInTx(usageRepoPath, true, statusHolder, true);

        assertTrue(deleted);
        verify(repo1Cache).undeploy(deleteContextCaptor.capture());
        DeleteContext deleteContext = deleteContextCaptor.getValue();
        assertTrue(deleteContext.isTriggerMetadataCalculation());
        assertTrue(deleteContext.isFullRepoContentDeletion());
        assertTrue(deleteContext.isSkipTrashcan());
        verify(repo1Cache, never()).undeploy(any(DeleteContext.class), any(BasicStatusHolder.class));
    }

    @ParameterizedTest
    @CsvSource({
            "false,0",
            "true,1",
    })
    void undeploySingleItemTransactions(boolean canDelete, int times) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", true);
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        when(storageInterceptors.canDelete(eq(repoPath), any(BasicStatusHolder.class))).thenReturn(canDelete);

        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.undeploySingleItemWithTimeCheckEnabled)).thenReturn(false);
        UndeployContext undeployContext = new UndeployContext(statusHolder, false, 0, false);
        boolean result =
                repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);
        assertEquals(canDelete, result);
        verify(fileService, times(times)).loadChildren(any());
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void undeploySingleItemTransactionsSkipSystemFolders(SystemFolder systemFolder) {
        RepositoryServiceImpl repoService = new RepositoryServiceImpl();
        ReflectionTestUtils.setField(repoService, "fileService", fileService);

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = RepoPathFactory.create("repo", systemFolder.getFolderName() + "/");
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        when(storageInterceptors.canDelete(eq(repoPath), any(BasicStatusHolder.class))).thenReturn(true);
        UndeployContext undeployContext = new UndeployContext(statusHolder, false, 0, true);
        boolean result =
                repoService.undeploySingleItemTransactions(undeployContext, repoPath, false);
        assertTrue(result);
        verify(fileService, never()).loadChildren(any());
    }

    @ParameterizedTest
    @EnumSource(SystemFolder.class)
    void undeploySingleItemTransactionsSkipSystemFolderChildren(SystemFolder systemFolder) {
        RepositoryServiceImpl repoService = new RepositoryServiceImpl();
        ReflectionTestUtils.setField(repoService, "fileService", fileService);

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = RepoPathFactory.create("repo",
                systemFolder.getFolderName() + RepoPath.PATH_SEPARATOR + "path.txt");
        UndeployContext undeployContext = new UndeployContext(statusHolder, false, 0, true);

        boolean result = repoService.undeploySingleItemTransactions(undeployContext, repoPath, false);

        assertTrue(result);
        verify(fileService, never()).loadChildren(any());
    }

    @Test
    void undeploySingleItemTransactionsFullRepoContentDeletion_forwardsDeleteContextAndTxFlag() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path.txt", false);
        ArgumentCaptor<DeleteContext> deleteContextCaptor = ArgumentCaptor.forClass(DeleteContext.class);
        when(storageInterceptors.canDelete(eq(repoPath), any(BasicStatusHolder.class), any(DeleteContext.class)))
                .thenReturn(true);
        when(internalRepositoryService.undeployInTx(repoPath, false, statusHolder, true)).thenReturn(true);
        UndeployContext undeployContext = new UndeployContext(statusHolder, false,
                System.currentTimeMillis(), false, true);

        boolean result = repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);

        assertTrue(result);
        verify(storageInterceptors).canDelete(eq(repoPath), any(BasicStatusHolder.class),
                deleteContextCaptor.capture());
        assertTrue(deleteContextCaptor.getValue().isFullRepoContentDeletion());
        verify(internalRepositoryService).undeployInTx(repoPath, false, statusHolder, true);
        verify(internalRepositoryService, never()).undeployInTx(repoPath, false, statusHolder);
    }

    @Test
    void undeploySingleItemTransactionsFullRepoContentDeletion_mergesCanDeleteWarning() {
        BasicStatusHolder sharedStatusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path.txt", false);
        ArgumentCaptor<DeleteContext> deleteContextCaptor = ArgumentCaptor.forClass(DeleteContext.class);
        doAnswer(invocation -> {
            BasicStatusHolder statusHolderArg = invocation.getArgument(1);
            statusHolderArg.warn("Warning from full delete canDelete", 403, null, log);
            return false;
        }).when(storageInterceptors).canDelete(eq(repoPath), any(BasicStatusHolder.class), any(DeleteContext.class));
        UndeployContext undeployContext = new UndeployContext(sharedStatusHolder, false,
                System.currentTimeMillis(), false, true);

        boolean result = repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);

        assertFalse(result);
        assertTrue(sharedStatusHolder.hasWarnings());
        verify(storageInterceptors).canDelete(eq(repoPath), any(BasicStatusHolder.class),
                deleteContextCaptor.capture());
        assertTrue(deleteContextCaptor.getValue().isFullRepoContentDeletion());
        verify(internalRepositoryService, never()).undeployInTx(any(RepoPath.class), anyBoolean(),
                any(BasicStatusHolder.class), anyBoolean());
    }

    @Test
    void undeploySingleItemTransactionsWithTimeChecksNoneRoot() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", true);
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        when(storageInterceptors.canDelete(any(), any())).thenReturn(true);
        ItemInfo itemInfo = mock(ItemInfo.class);
        List<ItemInfo> children = List.of(itemInfo);
        when(fileService.loadChildren(repoPath)).thenReturn(children);
        Properties properties = mock(Properties.class);
        when(propertiesService.getProperties(itemInfo)).thenReturn(properties);
        RepoPath repoPath1 = new RepoPathImpl("repo1", "path1", true);
        when(children.getFirst().getRepoPath()).thenReturn(repoPath1);
        UndeployContext undeployContext = new UndeployContext(statusHolder, false, System.currentTimeMillis(), false);
        boolean result =
                repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);
        assertTrue(result);
        verify(fileService, times(1)).loadChildren(repoPath);
        verify(propertiesService, times(1)).getProperties(itemInfo);
        verify(storageInterceptors, times(2)).canDelete(any(), any());
        verify(pruneService, times(2)).prune(any());
        verify(internalRepositoryService, times(2)).undeployInTx(any(), anyBoolean(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "false, true, 1, 1",
            "true, false, 0, 0"
    })
    void undeploySingleItemTransactionsFile(boolean shouldProtectPathDeletion, boolean expectedResult,
            int undeployTimes, int pruneTimes) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path.txt", false);
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        when(storageInterceptors.canDelete(any(), any())).thenReturn(true);
        UndeployContext undeployContext =
                new UndeployContext(statusHolder, shouldProtectPathDeletion, System.currentTimeMillis(), false);
        boolean result =
                repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);
        assertEquals(result, expectedResult);
        verify(storageInterceptors, times(1)).canDelete(any(), any());
        verify(internalRepositoryService, times(undeployTimes)).undeployInTx(any(), anyBoolean(), any());
        verify(pruneService, times(pruneTimes)).prune(repoPath.getParent());
        verify(internalRepositoryService, times(pruneTimes)).undeployInTx(any(), anyBoolean(), any());
    }

    @ParameterizedTest
    @MethodSource("params")
    void undeploySingleItemTransactionsWithTimeCheckWithNewArtifact(boolean timeCheckEnabled, boolean containPath,
            int propertiesInvocations, int canDeleteInvocations, int parentPruneInvocations) {
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.undeploySingleItemWithTimeCheckEnabled)).thenReturn(timeCheckEnabled);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", true);
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        when(storageInterceptors.canDelete(any(), any())).thenReturn(true);
        ItemInfo itemInfo = mock(ItemInfo.class);
        ItemInfo itemInfo1 = mock(ItemInfo.class);
        RepoPath deletedChilded = new RepoPathImpl("repo", "path1/hello/child1.txt", false);
        when(itemInfo1.getRepoPath()).thenReturn(deletedChilded);
        List<ItemInfo> children = List.of(itemInfo, itemInfo1);
        when(fileService.loadChildren(repoPath)).thenReturn(children);
        Properties properties = mock(Properties.class);
        when(propertiesService.getProperties(itemInfo)).thenReturn(properties);
        when(propertiesService.getProperties(itemInfo1)).thenReturn(properties);
        RepoPath parent = new RepoPathImpl("repo", "path1/hello", true);
        RepoPath child = new RepoPathImpl("repo", "path1/hello/child.txt", false);
        when(children.getFirst().getRepoPath()).thenReturn(child);
        when(properties.getFirst("trash.time")).thenReturn(String.valueOf(Long.MAX_VALUE)).thenReturn("0");
        UndeployContext undeployContext = new UndeployContext(statusHolder, false, System.currentTimeMillis(), false);
        boolean result =
                repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);
        assertTrue(result);
        assertEquals(containPath, undeployContext.getPathsToProtect().contains(parent));
        verify(fileService, times(1)).loadChildren(repoPath);
        verify(propertiesService, times(propertiesInvocations)).getProperties(itemInfo);
        verify(storageInterceptors, times(canDeleteInvocations)).canDelete(any(), any());
        verify(pruneService, times(parentPruneInvocations)).prune(parent);
        verify(pruneService, times(1)).prune(repoPath.getParent());
        verify(internalRepositoryService, times(0)).undeployInTx(eq(parent), anyBoolean(), any());
        verify(internalRepositoryService, times(1)).undeployInTx(eq(deletedChilded), anyBoolean(), any());
    }

    private static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of(true, true, 1, 2, 0),
                Arguments.of(false, false, 0, 3, 2)
        );
    }

    @ParameterizedTest
    @CsvSource({
            "false,0",
            "true,1",
    })
    void undeploySingleItemTransactionsCantDelete(boolean canDelete, int times) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", true);
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        when(storageInterceptors.canDelete(eq(repoPath), any(BasicStatusHolder.class))).thenReturn(canDelete);
        UndeployContext undeployContext = new UndeployContext(statusHolder, false, System.currentTimeMillis(), false);

        boolean result =
                repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);
        assertEquals(canDelete, result);
        verify(fileService, times(times)).loadChildren(any());
    }

    @Test
    void deleteForbidden_shouldIsolateErrorsFromTemporaryStatusHolder_whenCanDeleteReturnsFalse() {
        // Arrange
        BasicStatusHolder sharedStatusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", false);

        // Mock canDelete to return false and add an error to the statusHolder passed to it
        doAnswer(invocation -> {
            BasicStatusHolder statusHolderArg = invocation.getArgument(1);
            statusHolderArg.error("Error from canDelete", 403, null, log);
            return false;
        }).when(storageInterceptors).canDelete(eq(repoPath), any(BasicStatusHolder.class));

        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        UndeployContext undeployContext = new UndeployContext(sharedStatusHolder, false,
                System.currentTimeMillis(), false);

        // Act
        boolean result = repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);

        // Assert - deletion should be blocked, and error should be merged to shared statusHolder
        assertFalse(result, "Deletion should be blocked when canDelete returns false");
        assertTrue(sharedStatusHolder.hasErrors(), "Error from canDelete should be merged to shared statusHolder");
        assertEquals(403, sharedStatusHolder.getStatusCode());
    }

    @Test
    void deleteForbidden_shouldNotPolluteSharedStatusHolder_whenCanDeleteReturnsTrue() {
        // Arrange
        BasicStatusHolder sharedStatusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", false);

        // Mock canDelete to return true but add an error to the statusHolder passed to it
        // This simulates a scenario where canDelete succeeds but has some internal error
        doAnswer(invocation -> {
            BasicStatusHolder statusHolderArg = invocation.getArgument(1);
            statusHolderArg.error("Internal error in canDelete", 500, null, log);
            return true; // But still allows deletion
        }).when(storageInterceptors).canDelete(eq(repoPath), any(BasicStatusHolder.class));

        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        UndeployContext undeployContext = new UndeployContext(sharedStatusHolder, false,
                System.currentTimeMillis(), false);

        // Act
        boolean result = repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);

        // Assert - deletion should proceed, and error should NOT be merged to shared statusHolder
        assertTrue(result, "Deletion should proceed when canDelete returns true");
        assertFalse(sharedStatusHolder.hasErrorsOrWarnings(),
                "Errors from temporary statusHolder should not pollute shared statusHolder when deletion is allowed");
    }

    @Test
    void deleteForbidden_shouldMergeWarning_whenCanDeleteReturnsFalseWithWarning() {
        // Arrange
        BasicStatusHolder sharedStatusHolder = new BasicStatusHolder();
        RepoPath repoPath = new RepoPathImpl("repo", "path", false);

        // Mock canDelete to return false and add a warning
        doAnswer(invocation -> {
            BasicStatusHolder statusHolderArg = invocation.getArgument(1);
            statusHolderArg.warn("Warning from canDelete", 403, null, log);
            return false;
        }).when(storageInterceptors).canDelete(eq(repoPath), any(BasicStatusHolder.class));

        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);
        UndeployContext undeployContext = new UndeployContext(sharedStatusHolder, false,
                System.currentTimeMillis(), false);

        // Act
        boolean result = repositoryService.undeploySingleItemTransactions(undeployContext, repoPath, false);

        // Assert - deletion should be blocked, and warning should be merged
        assertFalse(result, "Deletion should be blocked when canDelete returns false");
        assertTrue(sharedStatusHolder.hasWarnings(), "Warning from canDelete should be merged to shared statusHolder");
        assertEquals(403, sharedStatusHolder.getStatusCode());
    }

    @Test
    void testTetVirtualFileInfoNoRepo() {
        assertThrows(IllegalArgumentException.class, () -> repositoryService.getVirtualFileInfo(repoPath1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", DEFAULT_PROJECT, "some-project-key"})
    @NullSource
    void testGetLocalFederatedAndCachedRepoConfigsByProject(String projectKey) {
        VersioningCacheImpl<RepositoriesCache> cache = mock(VersioningCacheImpl.class);
        repositoryService.cache = cache;

        LocalRepo localRepo = createMockLocalRepo("local-repo");
        FederatedRepo federatedRepo = createMockFederatedRepo("federated-repo");
        LocalCacheRepo localCacheRepo = createMockLocalCacheRepo("local-cache-repo");

        Map<String, LocalRepo> localRepositoriesMap = createRepoMap(localRepo);
        Map<String, FederatedRepo> federatedCacheRepositoriesMap = createRepoMap(federatedRepo);
        Map<String, LocalCacheRepo> localCacheRepositoriesMap = createRepoMap(localCacheRepo);
        Map<String, VirtualRepo> virtualRepositoriesMap = createRepoMap(mock(VirtualRepo.class));
        Map<String, RemoteRepo> remoteRepositoriesMap = createRepoMap(mock(RemoteRepo.class));
        Map<String, ReleaseBundlesRepo> releaseBundlesRepoMap = createRepoMap(mock(ReleaseBundlesRepo.class));

        RepositoriesCache repositoriesCache = new RepositoriesCache(
                releaseBundlesRepoMap,
                virtualRepositoriesMap,
                federatedCacheRepositoriesMap,
                localCacheRepositoriesMap,
                remoteRepositoriesMap,
                localRepositoriesMap,
                null, null, null,
                null
        );

        when(cache.get()).thenReturn(repositoriesCache);

        doAnswer(invocation -> {
            if ("some-project-key".equals(invocation.getArgument(0))) {
                return Set.of("local-repo");
            } else {
                return Set.of("local-repo", "federated-repo", "local-cache-repo");
            }
        }).when(projectsService).getProjectRepositoriesKeys(any(String.class), anyBoolean());

        List<LocalConfig> localConfigs = repositoryService.getLocalFederatedAndCachedRepoConfigsByProject(projectKey);

        if ("some-project-key".equals(projectKey)) {
            assertEquals(1, localConfigs.size());
        } else {
            assertEquals(3, localConfigs.size());
        }
    }

    @Test
    void testTetVirtualGetInfoWithThreadLocalCache() {
        VersioningCacheImpl<RepositoriesCache> cache = mock(VersioningCacheImpl.class);
        repositoryService.cache = cache;

        Map<String, VirtualRepo> localCacheRepositoriesMap = new HashMap<>();
        RepositoriesCache repositoriesCache = new RepositoriesCache(null, localCacheRepositoriesMap,
                null, null, null, null, null, null, null, null);
        when(cache.get()).thenReturn(repositoriesCache);
        VirtualRepo repo = mock(VirtualRepo.class);
        when(repo.getKey()).thenReturn("repo1-cache");
        localCacheRepositoriesMap.put("repo1-cache", repo);

        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(RepoType.Docker);

        try (MockedStatic<ReposItemsThreadLocalCacheService> theMock =
                     Mockito.mockStatic(ReposItemsThreadLocalCacheService.class)) {
            FileInfo file = mock(FileInfo.class);
            FolderInfo folder = mock(FolderInfo.class);
            ItemInfo item = mock(ItemInfo.class);
            when(file.getRepoKey()).thenReturn("repo1-cache");

            theMock.when(() -> ReposItemsThreadLocalCacheService.wrap(any(), any(), any())).thenReturn(file);
            assertNotNull(repositoryService.getVirtualFileInfo(repoPath1));

            theMock.when(() -> ReposItemsThreadLocalCacheService.wrap(any(), any(), any())).thenReturn(folder);
            assertNotNull(repositoryService.getVirtualFolderInfo(repoPath1));

            theMock.when(() -> ReposItemsThreadLocalCacheService.wrap(any(), any(), any())).thenReturn(item);
            assertNotNull(repositoryService.getVirtualItemInfo(repoPath1));
        }
    }

    @Test
    void testTetVirtualGetInfoWithThreadLocalCacheFullFlow() {
        VersioningCacheImpl<RepositoriesCache> cache = mock(VersioningCacheImpl.class);
        repositoryService.cache = cache;
        when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);

        Map<String, VirtualRepo> localCacheRepositoriesMap = new HashMap<>();
        RepositoriesCache reposCache = new RepositoriesCache(
                null, localCacheRepositoriesMap, null, null,
                null, null, null, null, null,
                null);
        when(cache.get()).thenReturn(reposCache);
        VirtualRepo repo = mock(VirtualRepo.class);
        when(repo.getKey()).thenReturn("repo1-cache");
        localCacheRepositoriesMap.put("repo1-cache", repo);

        LocalConfig localConfig = mock(LocalConfig.class);
        when(localConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repo.getResolvedLocalFederatedAndCachedRepos()).thenReturn(Set.of(repo2Cache));

        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(artifactoryHome);
        when(internalRepositoryService.undeployInTx(any(), anyBoolean(), any())).thenReturn(true);

        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.fscEnabled)).thenReturn(false);
        doReturn(localConfig).when(repositoryService).getRepoConfigByKey("repo1-cache");

        setupRepositoryServiceMock("repo1-cache", false, 1, 0);
        assertThrows(ItemNotFoundRuntimeException.class, () -> repositoryService.getVirtualFileInfo(repoPath1));
        assertThrows(ItemNotFoundRuntimeException.class, () -> repositoryService.getVirtualFolderInfo(repoPath1));
        assertThrows(ItemNotFoundRuntimeException.class, () -> repositoryService.getVirtualItemInfo(repoPath1));
    }


    @Test
    void testGetResourceStreamHandleRepoNotFound() {
        RepoPath repoPath = mock(RepoPath.class);
        LocalRepo localRepo = mock(LocalRepo.class);
        RepositoryServiceImpl repositoryServiceSpy = spy(new RepositoryServiceImpl());
        when(repoPath.getRepoKey()).thenReturn("nonExistentRepo");
        doReturn(localRepo).when(repositoryServiceSpy).localFederatedOrCachedRepositoryByKey("nonExistentRepo");
        doThrow(new BinaryNotFoundException("Binary not found")).when(localRepo).getFileContent(any(RepoPath.class));
        assertThrows(NotFoundException.class, () -> repositoryServiceSpy.getResourceStreamHandle(repoPath));
    }

    @Test
    void testGetGenericArchiveFileContentRepoNotFound() throws IOException {
        RepoPath archivePath = RepoPathFactory.create("nonExistentRepo", "path/to/archive.jar");
        String sourceEntryPath = "org/example/Test.java";
        RepositoryServiceImpl repositoryServiceSpy = spy(new RepositoryServiceImpl());
        doReturn(null).when(repositoryServiceSpy).localFederatedOrCachedRepositoryByKey("nonExistentRepo");

        ArchiveFileContent result = repositoryServiceSpy.getGenericArchiveFileContent(archivePath, sourceEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
        assertTrue(result.getFailureReason().contains("not found"),
                "Failure reason should indicate repository not found");
        assertTrue(result.getFailureReason().contains("nonExistentRepo"),
                "Failure reason should contain repository key");
    }

    @Test
    void testGetArchiveFileContentRepoNotFound() throws IOException {
        RepoPath archivePath = RepoPathFactory.create("nonExistentRepo", "path/to/archive.jar");
        String sourceEntryPath = "org/example/Test.java";
        RepositoryServiceImpl repositoryServiceSpy = spy(new RepositoryServiceImpl());
        doReturn(null).when(repositoryServiceSpy).localFederatedOrCachedRepositoryByKey("nonExistentRepo");

        ArchiveFileContent result = repositoryServiceSpy.getArchiveFileContent(archivePath, sourceEntryPath);

        assertNotNull(result);
        assertNull(result.getContent());
        assertTrue(result.getFailureReason().contains("not found"),
                "Failure reason should indicate repository not found");
        assertTrue(result.getFailureReason().contains("nonExistentRepo"),
                "Failure reason should contain repository key");
    }

    @Test
    void testGetGenericArchiveFileContentRepoExists() throws IOException {
        RepoPath archivePath = RepoPathFactory.create("existingRepo", "path/to/archive.jar");
        String sourceEntryPath = "org/example/Test.java";
        LocalRepo localRepo = mock(LocalRepo.class);
        ArchiveFileContent expectedContent = new ArchiveFileContent("test content", archivePath, sourceEntryPath);
        RepositoryServiceImpl repositoryServiceSpy = spy(new RepositoryServiceImpl());
        doReturn(localRepo).when(repositoryServiceSpy).localFederatedOrCachedRepositoryByKey("existingRepo");

        try (MockedConstruction<ArchiveContentRetriever> mockedConstruction =
                     Mockito.mockConstruction(ArchiveContentRetriever.class,
                             (mock, context) -> {
                                 try {
                                     doReturn(expectedContent).when(mock)
                                             .getGenericArchiveFileContent(localRepo, archivePath, sourceEntryPath);
                                 } catch (IOException e) {
                                     throw new RuntimeException(e);
                                 }
                             })) {

            ArchiveFileContent result = repositoryServiceSpy.getGenericArchiveFileContent(archivePath,
                    sourceEntryPath);

            assertNotNull(result, "Result should not be null");
            assertEquals("test content", result.getContent());
            assertEquals(1, mockedConstruction.constructed().size(),
                    "ArchiveContentRetriever should be instantiated once");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetVirtualReposContainingRepoShouldConsiderPackageType(boolean canAggregateRepoByPackageType) {
        VirtualRepoDescriptor virtualRepo = mock(VirtualRepoDescriptor.class);
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(repositoryService.getVirtualRepoDescriptors()).thenReturn(List.of(virtualRepo));
        when(packageSpecification.isVirtualCanContainRepoWithType(any(), any())).thenReturn(
                canAggregateRepoByPackageType);
        try (MockedConstruction<VirtualRepoResolver> ignored = Mockito.mockConstruction(
                VirtualRepoResolver.class,
                (mock, context) -> {
                    doReturn(true).when(mock).contains(localRepoDescriptor);
                })) {
            List<VirtualRepoDescriptor> result = repositoryService.getVirtualReposContainingRepo(localRepoDescriptor);
            assertEquals(canAggregateRepoByPackageType ? 1 : 0, result.size());
        }
    }

    @Test
    void testUnexpireDbCacheRepoWithCachePeriod() {
        DbCacheRepo cacheRepo = mock(DbCacheRepo.class);
        MutableVfsItem mutableVfsItem = mock(MutableVfsItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.getRetrievalCachePeriodSecs()).thenReturn(TimeUnit.HOURS.toSeconds(2));

        when(cacheRepo.getRemoteRepo()).thenReturn(remoteRepo);
        when(cacheRepo.getKey()).thenReturn("cache-repo");
        when(itemInfo.getLastUpdated()).thenReturn(Long.MAX_VALUE);
        when(mutableVfsItem.getOriginalInfo()).thenReturn(itemInfo);
        when(cacheRepo.getMutableFsItem(any(RepoPath.class))).thenReturn(mutableVfsItem);

        repositoryService.unexpireIfExists(cacheRepo, repoPath1.getPath());
        verify(mutableVfsItem).setUpdated(anyLong());
    }


    @Test
    void testUpdateRequiredNoCachePeriod() {
        DbCacheRepo cacheRepo = mock(DbCacheRepo.class);
        VfsItem<?> vfsItem = mock(VfsItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);

        String repoKey = "cache-repo";
        RepoPath repoPath = RepoPathFactory.create(repoKey);

        when(cacheRepo.getRemoteRepo()).thenReturn(mock(RemoteRepo.class));
        when(cacheRepo.getRemoteRepo().getRetrievalCachePeriodSecs()).thenReturn(0L);
        when(cacheRepo.getKey()).thenReturn(repoKey);
        when(cacheRepo.getImmutableFsItem(repoPath)).thenReturn(vfsItem);
        when(vfsItem.getInfo()).thenReturn(itemInfo);
        long currentTime = System.currentTimeMillis();
        when(itemInfo.getLastUpdated()).thenReturn(currentTime - 30000);
        when(vfsItem.getInfo()).thenReturn(itemInfo);

        assertFalse(repositoryService.isUpdateRequired(cacheRepo, currentTime, repoPath));
        when(itemInfo.getLastUpdated()).thenReturn(currentTime - nodeLastUpdatedThresholdMillis.getLong() - 1);
        assertTrue(repositoryService.isUpdateRequired(cacheRepo, currentTime, repoPath));
    }

    @Test
    void testUnexpireLocalRepoUpdateAlways() {
        LocalRepo localRepo = mock(LocalRepo.class);
        MutableVfsItem mutableVfsItem = mock(MutableVfsItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);

        when(localRepo.isCache()).thenReturn(false);
        when(localRepo.getKey()).thenReturn("local-repo");
        when(itemInfo.getLastUpdated()).thenReturn(Long.MAX_VALUE);
        when(mutableVfsItem.getOriginalInfo()).thenReturn(itemInfo);
        when(localRepo.getMutableFsItem(any(RepoPath.class))).thenReturn(mutableVfsItem);

        repositoryService.unexpireIfExists(localRepo, repoPath1.getPath());
        verify(mutableVfsItem).setUpdated(anyLong());
    }

    @ParameterizedTest
    @MethodSource("deployableRepoProvider")
    void testHasAnyDeployableRepo(List<String> repoKeys, boolean isAdmin, boolean hasDeployPermission,
            boolean hasDeployableBuildInfoRepo, boolean expectedResult) {
        when(repositoryConfigService.getLocalAndFederatedRepoConfigsExcludingInfoRepoKeys()).thenReturn(repoKeys);
        when(securityService.isAdmin()).thenReturn(isAdmin);
        RepoPermissionTarget repoPermissionTarget = mock(RepoPermissionTarget.class);
        when(repoPermissionTarget.getRepoKeys()).thenReturn(
                hasDeployPermission ? new HashSet<>(repoKeys) : Set.of("otherRepo"));
        when(authorizationService.getRepoPermissionTargets(ArtifactoryPermission.DEPLOY)).thenReturn(
                List.of(repoPermissionTarget));
        LocalRepoConfig buildInfoRepo = mock(LocalRepoConfig.class);
        when(buildInfoRepo.getKey()).thenReturn("buildInfoRepoKey");
        when(buildFacade.getBuildInfoRepoForDefaultProject()).thenReturn("buildInfoRepoKey");
        doReturn(buildInfoRepo).when(repositoryService).localOrFederatedRepoConfigByKey("buildInfoRepoKey");
        when(authorizationService.hasBuildPermission("buildInfoRepoKey", ArtifactoryPermission.DEPLOY)).thenReturn(
                hasDeployableBuildInfoRepo);

        boolean result = repositoryService.hasAnyDeployableRepo();

        assertEquals(expectedResult, result);
    }

    @Test
    void testPrepareRepoForRemovalBlackoutRealRepo() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("realRepo", Generic);
        InternalRepositoryConfigService repoConfigService = mock(InternalRepositoryConfigService.class);
        when(repoConfigService.getRepoConfigByKey(localRepoConfig.getKey())).thenReturn(localRepoConfig);
        repositoryService.prepareRepoForRemoval(localRepoConfig.getKey(), repoConfigService);

        assertTrue(localRepoConfig.getRepoTypeConfig().isBlackedOut());
        verify(repoConfigService).updateRepoConfig(localRepoConfig, true);
    }

    @Test
    void testPrepareRepoForRemovalRemoteRepo() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remoteRepo", Generic);
        InternalRepositoryConfigService repoConfigService = mock(InternalRepositoryConfigService.class);
        when(repoConfigService.getRepoConfigByKey(remoteRepoConfig.getKey())).thenReturn(remoteRepoConfig);
        repositoryService.prepareRepoForRemoval(remoteRepoConfig.getKey(), repoConfigService);
        assertTrue(remoteRepoConfig.getRepoTypeConfig().isBlackedOut());
        assertTrue(remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally());
        verify(repoConfigService).updateRepoConfig(remoteRepoConfig, true);
    }

    @Test
    void testPrepareRepoForRemovalVirtualRepo() {
        VirtualRepoConfig virtualRepoConfig = VirtualRepoConfig.create("virtualRepo", Generic);
        InternalRepositoryConfigService repoConfigService = mock(InternalRepositoryConfigService.class);
        when(repoConfigService.getRepoConfigByKey(virtualRepoConfig.getKey())).thenReturn(virtualRepoConfig);
        repositoryService.prepareRepoForRemoval(virtualRepoConfig.getKey(), repoConfigService);
        verify(repoConfigService, never()).updateRepoConfig(any(), anyBoolean());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testVirtualRepoDescriptorByKeyBlank(String key) {
        VirtualRepoDescriptor result = repositoryService.virtualRepoDescriptorByKey(key);
        assertNull(result, "Expected null when the repo key is blank");
    }

    @Test
    void testVirtualRepoConfigByKey() {
        String repoKey = "virtual-repo-1";
        VirtualRepoConfig expectedVirtualRepoConfig = VirtualRepoConfig.create(repoKey, Generic);
        when(repositoryConfigService.getVirtualRepoConfigByKey(repoKey)).thenReturn(expectedVirtualRepoConfig);

        VirtualRepoConfig actualVirtualRepoConfig = repositoryService.virtualRepoConfigByKey(repoKey);

        assertNotNull(actualVirtualRepoConfig);
        assertEquals(expectedVirtualRepoConfig, actualVirtualRepoConfig);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testVirtualRepoConfigByKeyBlank(String key) {
        VirtualRepoConfig result = repositoryService.virtualRepoConfigByKey(key);
        assertNull(result, "Expected null when the repo key is blank");
    }

    @Test
    void testGetLocalFederatedAndRemoteRepoConfigs() {
        List<RepoConfig> expectedRepoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", Generic),
                FederatedRepoConfig.create("federated-repo-1", Generic),
                RemoteRepoConfig.create("remote-repo-1", Generic));
        when(repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED))).thenReturn(
                expectedRepoConfigs);

        List<RealRepoConfig> actualRepoConfigs = repositoryService.getLocalFederatedAndRemoteRepoConfigs();

        assertNotNull(actualRepoConfigs);
        assertEquals(expectedRepoConfigs.size(), actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.containsAll(expectedRepoConfigs));
    }

    @Test
    void testGetLocalFederatedAndRemoteRepoConfigsByProject() {
        List<RepoConfig> repoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", Generic),
                LocalRepoConfig.create("local-repo-2", Generic),
                FederatedRepoConfig.create("federated-repo-1", Generic),
                RemoteRepoConfig.create("remote-repo-1", Generic)
        );

        Set<String> expectedRepoConfigs = Set.of("local-repo-1", "federated-repo-1", "remote-repo-1");
        when(repositoryConfigService.getRepoConfigsByTypes(Set.of(LOCAL, REMOTE, FEDERATED))).thenReturn(repoConfigs);
        when(projectsService.getProjectRepositoriesKeys(any(), anyBoolean())).thenReturn(expectedRepoConfigs);

        List<RealRepoConfig> actualRepoConfigs =
                repositoryService.getLocalFederatedAndRemoteRepoConfigsByProject("some-project-key");

        assertNotNull(actualRepoConfigs);
        assertEquals(expectedRepoConfigs.size(), actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.stream().map(RepoConfig::getKey).toList().containsAll(expectedRepoConfigs));
    }

    @Test
    void testGetLocalRepoConfigsExcludingInfoRepos() {
        List<LocalConfig> expectedRepoConfigs = List.of(
                LocalRepoConfig.create("local-repo-1", Generic),
                LocalRepoConfig.create("local-repo-2", RepoType.Maven)
        );
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(expectedRepoConfigs);

        List<LocalConfig> actualRepoConfigs = repositoryService.getLocalRepoConfigsExcludingInfoRepos();

        assertNotNull(actualRepoConfigs);
        assertEquals(expectedRepoConfigs.size(), actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.containsAll(expectedRepoConfigs));
    }

    @Test
    public void testSearchFilesWithBadChecksumValidInput() {
        ChecksumType type = ChecksumType.md5;
        Set<String> repos = Set.of("repo1", "repo2");
        List<FileInfo> mockFiles = List.of(mock(FileInfo.class), mock(FileInfo.class));
        when(repositoryService.searchFilesWithBadChecksum(any(ChecksumType.class), anySet()))
                .thenReturn(mockFiles);
        List<FileInfo> result = repositoryService.searchFilesWithBadChecksum(type, repos);
        assertEquals(mockFiles, result);
        verify(repositoryService).searchFilesWithBadChecksum(type, repos);
    }

    @Test
    void testLocalFederatedOrCachedRepoConfigByKey() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", Generic);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("federated-repo", Generic);
        LocalCacheRepoConfig localCacheRepoConfig =
                LocalCacheRepoConfig.create(RemoteRepoConfig.create("cached-repo", Generic));
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        when(localCacheRepo.getRepoConfig()).thenReturn(localCacheRepoConfig);

        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey("federated-repo")).thenReturn(
                federatedRepoConfig);
        when(repositoryService.localFederatedOrCachedRepositoryByKey("cached-repo")).thenReturn(localCacheRepo);
        assertEquals(localRepoConfig, repositoryService.localFederatedOrCachedRepoConfigByKey("local-repo"));
        assertEquals(federatedRepoConfig, repositoryService.localFederatedOrCachedRepoConfigByKey("federated-repo"));
        assertEquals(localCacheRepoConfig, repositoryService.localFederatedOrCachedRepoConfigByKey("cached-repo"));
        assertNull(repositoryService.localFederatedOrCachedRepoConfigByKey("non-existing-repo"));
    }

    @Test
    void testLocalOrFederatedRepoConfigByKey() {
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create("local-repo", Generic);
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("federated-repo", Generic);

        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey("local-repo")).thenReturn(localRepoConfig);
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey("federated-repo")).thenReturn(
                federatedRepoConfig);

        assertEquals(localRepoConfig, repositoryService.localOrFederatedRepoConfigByKey("local-repo"));
        assertEquals(federatedRepoConfig, repositoryService.localOrFederatedRepoConfigByKey("federated-repo"));

        assertNull(repositoryService.localOrFederatedRepoConfigByKey("non-existing-repo"));
    }

    @Test
    void testLocalOrFederatedRepoConfigByKeyTrashcan() {
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey(TRASH_KEY)).thenReturn(null);
        assertNotNull(repositoryService.localOrFederatedRepoConfigByKey(TRASH_KEY));
    }

    @Test
    void testLocalOrFederatedRepoConfigBySupportBundle() {
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey(SUPPORT_BUNDLE_REPO_KEY)).thenReturn(null);
        assertNotNull(repositoryService.localOrFederatedRepoConfigByKey(SUPPORT_BUNDLE_REPO_KEY));
    }

    @Test
    void testRemoteRepoConfigByKey() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("remote-repo", Generic);
        when(repositoryConfigService.getRemoteRepoConfigByKey("remote-repo")).thenReturn(remoteRepoConfig);

        assertEquals(remoteRepoConfig, repositoryService.remoteRepoConfigByKey("remote-repo"));

        assertNull(repositoryService.remoteRepoConfigByKey("non-existing-repo"));
    }

    @Test
    void getFederatedRepoConfigByKey_shouldReturnConfig_whenRepoExists() {
        FederatedRepoConfig federatedRepoConfig = FederatedRepoConfig.create("federated-repo", Generic);
        when(repositoryConfigService.getFederatedRepoConfigByKey("federated-repo")).thenReturn(federatedRepoConfig);

        assertEquals(federatedRepoConfig, repositoryService.getFederatedRepoConfigByKey("federated-repo"));
    }

    @Test
    void getFederatedRepoConfigByKey_shouldReturnNull_whenRepoDoesNotExist() {
        when(repositoryConfigService.getFederatedRepoConfigByKey("non-existing-repo")).thenReturn(null);

        assertNull(repositoryService.getFederatedRepoConfigByKey("non-existing-repo"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testFederatedRepoDescriptorByKeyBlank(String key) {
        FederatedRepoDescriptor result = repositoryService.federatedRepoDescriptorByKey(key);
        assertNull(result, "Expected null when the repo key is blank");
    }

    @Test
    void testGetLocalRepoConfigsByProject() {
        VersioningCacheImpl<RepositoriesCache> cache = mock(VersioningCacheImpl.class);
        repositoryService.cache = cache;

        Map<String, LocalRepo> localRepositoriesMap = new HashMap<>();
        RepositoriesCache repositoriesCache = new RepositoriesCache(null, null,
                null, null, null, localRepositoriesMap, null, null, null, null);
        when(cache.get()).thenReturn(repositoriesCache);
        List.of(
                LocalRepoConfig.create("local-repo-1", Generic),
                LocalRepoConfig.create("local-repo-2", RepoType.Maven),
                LocalRepoConfig.create("pipe-info", PipeInfo),
                LocalRepoConfig.create(IN_TRANSIT_REPO_KEY, Generic)
        ).forEach(repoConfig -> {
            LocalRepo localRepo = mock(LocalRepo.class);
            when(localRepo.getRepoConfig()).thenReturn(repoConfig);
            localRepositoriesMap.put(repoConfig.getKey(), localRepo);
        });
        when(projectsService.getProjectRepositoriesKeys(any(), anyBoolean())).thenReturn(
                Set.of("local-repo-1", "pipe-info"));

        List<LocalConfig> actualRepoConfigs =
                repositoryService.getLocalRepoConfigsByProject("some-project-key");

        assertNotNull(actualRepoConfigs);
        assertEquals(1, actualRepoConfigs.size());
        assertTrue(actualRepoConfigs.stream().map(RepoConfig::getKey).collect(Collectors.toSet())
                .contains("local-repo-1"));
    }

    @Test
    void testContainsRealContent_RealRepo_Empty() {
        String repoKey = "realRepo";
        RealRepoConfig realRepoConfig = mock(RealRepoConfig.class);
        when(realRepoConfig.isReal()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(realRepoConfig);
        when(fileService.isEmpty(repoKey)).thenReturn(true);

        boolean containsRealContent = repositoryService.containsRealContent(repoKey);

        assertFalse(containsRealContent);
    }

    @Test
    void testContainsRealContent_RealRepo_NotEmpty() {
        String repoKey = "realRepo";
        RealRepoConfig realRepoConfig = mock(RealRepoConfig.class);
        when(realRepoConfig.isReal()).thenReturn(true);
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(realRepoConfig);
        when(fileService.isEmpty(repoKey)).thenReturn(false);

        boolean containsRealContent = repositoryService.containsRealContent(repoKey);

        assertTrue(containsRealContent);
    }

    @Test
    void testContainsRealContent_VirtualRepo() {
        String repoKey = "virtualRepo";
        when(repositoryService.getRepoConfigByKey(repoKey)).thenReturn(
                VirtualRepoConfig.create("repoKey", Generic));

        boolean containsRealContent = repositoryService.containsRealContent(repoKey);

        assertFalse(containsRealContent);
    }

    @Test
    void testGetMutableItem() {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/item");
        LocalRepo localRepo = mock(LocalRepo.class);
        MutableVfsItem mutableVfsItem = mock(MutableVfsItem.class);

        when(repositoryService.localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey())).thenReturn(localRepo);
        when(localRepo.getMutableFsItem(repoPath)).thenReturn(mutableVfsItem);

        MutableVfsItem result = repositoryService.getMutableItem(repoPath);

        assertNotNull(result);
        assertEquals(mutableVfsItem, result);
        verify(localRepo).getMutableFsItem(repoPath);
    }

    @Test
    void testGetMutableItemCacheRepo() {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/item");
        LocalRepo localRepo = mock(LocalRepo.class);
        MutableVfsItem mutableVfsItem = mock(MutableVfsItem.class);

        when(repositoryService.localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey())).thenReturn(localRepo);
        when(localRepo.isCache()).thenReturn(true);
        when(localRepo.getKey()).thenReturn("cacheRepoKey");
        RepoPath resolvedRepoPath = RepoPathFactory.create("cacheRepoKey", "path/to/item");
        when(localRepo.getMutableFsItem(resolvedRepoPath)).thenReturn(mutableVfsItem);

        MutableVfsItem result = repositoryService.getMutableItem(repoPath);

        assertNotNull(result);
        assertEquals(mutableVfsItem, result);
        verify(localRepo).getMutableFsItem(resolvedRepoPath);
    }

    @Test
    void testGetMutableItemLocalRepoNotFound() {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/item");

        when(repositoryService.localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey())).thenReturn(null);

        assertThrows(ItemNotFoundRuntimeException.class, () -> repositoryService.getMutableItem(repoPath));
    }

    @Test
    void testGetMutableItemMutableFsItemNotFound() {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/item");
        LocalRepo localRepo = mock(LocalRepo.class);

        when(repositoryService.localFederatedOrCachedRepositoryByKey(repoPath.getRepoKey())).thenReturn(localRepo);
        when(localRepo.getMutableFsItem(repoPath)).thenReturn(null);

        assertThrows(ItemNotFoundRuntimeException.class, () -> repositoryService.getMutableItem(repoPath));
    }

    @Test
    void getItemInfo_shouldResolveRepoPath_whenCacheRepoWithDifferentKey() {
        RepoPath repoPath = RepoPathFactory.create("remote-repo", "path/to/item");
        LocalRepo cacheRepoMock = mock(LocalRepo.class);
        VfsItem vfsItemMock = mock(VfsItem.class);
        ItemInfo itemInfoMock = mock(ItemInfo.class);
        doReturn(cacheRepoMock).when(repositoryService).getLocalRepository(repoPath);
        when(cacheRepoMock.isCache()).thenReturn(true);
        when(cacheRepoMock.getKey()).thenReturn("remote-repo-cache");
        RepoPath resolvedRepoPath = RepoPathFactory.create("remote-repo-cache", "path/to/item");
        when(cacheRepoMock.getImmutableFsItem(resolvedRepoPath)).thenReturn(vfsItemMock);
        when(vfsItemMock.getInfo()).thenReturn(itemInfoMock);
        ItemInfo result = repositoryService.getItemInfo(repoPath);
        assertNotNull(result);
        assertEquals(itemInfoMock, result);
        verify(cacheRepoMock).getImmutableFsItem(resolvedRepoPath);
    }

    @Test
    void getItemInfo_shouldNotResolveRepoPath_whenCacheRepoWithSameKey() {
        RepoPath repoPath = RepoPathFactory.create("remote-repo-cache", "path/to/item");
        LocalRepo cacheRepoMock = mock(LocalRepo.class);
        VfsItem vfsItemMock = mock(VfsItem.class);
        ItemInfo itemInfoMock = mock(ItemInfo.class);
        doReturn(cacheRepoMock).when(repositoryService).getLocalRepository(repoPath);
        when(cacheRepoMock.isCache()).thenReturn(true);
        when(cacheRepoMock.getKey()).thenReturn("remote-repo-cache");
        when(cacheRepoMock.getImmutableFsItem(repoPath)).thenReturn(vfsItemMock);
        when(vfsItemMock.getInfo()).thenReturn(itemInfoMock);
        ItemInfo result = repositoryService.getItemInfo(repoPath);
        assertNotNull(result);
        assertEquals(itemInfoMock, result);
        verify(cacheRepoMock).getImmutableFsItem(repoPath);
    }

    @Test
    void getItemInfo_shouldNotResolveRepoPath_whenNotCacheRepo() {
        RepoPath repoPath = RepoPathFactory.create("local-repo", "path/to/item");
        LocalRepo cacheRepoMock = mock(LocalRepo.class);
        VfsItem vfsItemMock = mock(VfsItem.class);
        ItemInfo itemInfoMock = mock(ItemInfo.class);
        doReturn(cacheRepoMock).when(repositoryService).getLocalRepository(repoPath);
        when(cacheRepoMock.isCache()).thenReturn(false);
        when(cacheRepoMock.getImmutableFsItem(repoPath)).thenReturn(vfsItemMock);
        when(vfsItemMock.getInfo()).thenReturn(itemInfoMock);
        ItemInfo result = repositoryService.getItemInfo(repoPath);
        assertNotNull(result);
        assertEquals(itemInfoMock, result);
        verify(cacheRepoMock).getImmutableFsItem(repoPath);
    }

    @Test
    void getItemInfo_shouldThrowException_whenItemNotFound() {
        RepoPath repoPath = RepoPathFactory.create("local-repo", "path/to/item");
        LocalRepo localRepoMock = mock(LocalRepo.class);
        doReturn(localRepoMock).when(repositoryService).getLocalRepository(repoPath);
        when(localRepoMock.isCache()).thenReturn(false);
        when(localRepoMock.getImmutableFsItem(repoPath)).thenReturn(null);
        assertThrows(ItemNotFoundRuntimeException.class, () -> repositoryService.getItemInfo(repoPath));
    }

    private static Stream<Arguments> deployableRepoProvider() {
        return Stream.of(
                Arguments.of(List.of("repo1", "repo2"), true, false, false, true),
                Arguments.of(List.of("repo1", "repo2"), false, true, false, true),
                Arguments.of(List.of("repo1", "repo2"), false, false, false, false),
                Arguments.of(List.of("repo1", "repo2"), false, false, true, true),
                Arguments.of(List.of(PermissionTarget.ANY_REPO), false, true, false, true),
                Arguments.of(List.of(PermissionTarget.ANY_REPO), true, false, false, true),
                Arguments.of(List.of(PermissionTarget.ANY_REPO), true, false, true, true),
                Arguments.of(List.of(PermissionTarget.ANY_LOCAL_REPO), false, true, false, true),
                Arguments.of(List.of(PermissionTarget.ANY_LOCAL_REPO), true, false, false, true),
                Arguments.of(List.of(PermissionTarget.ANY_LOCAL_REPO), true, false, true, true),
                Arguments.of(List.of(), true, false, false, false),
                Arguments.of(List.of(), true, false, true, true),
                Arguments.of(List.of(), false, false, false, false),
                Arguments.of(List.of(), false, false, true, true)
        );
    }

    private void setupRepositoryServiceMock(String repoName, boolean isVirtualRepo, int numberOfLocals
            , int numberOfRemotes) {
        VirtualRepoDescriptor virtualRepo = mock(VirtualRepoDescriptor.class);
        var localRepoDescriptors = Collections.nCopies(numberOfLocals, mock(LocalRepoDescriptor.class));
        var remoteRepoDescriptors = Collections.nCopies(numberOfRemotes, mock(RemoteRepoDescriptor.class));
        List<RepoDescriptor> combined = Stream.concat(localRepoDescriptors.stream(), remoteRepoDescriptors.stream())
                .collect(Collectors.toList());
        when(virtualRepo.getRepositories()).thenReturn(combined);
        if (isVirtualRepo) {
            when(repositoryService.virtualRepoDescriptorByKey(repoName)).thenReturn(virtualRepo);
        } else {
            when(repositoryService.virtualRepoDescriptorByKey(repoName)).thenReturn(null);
        }

        List<RepoDescriptor> repoDescriptors = new ArrayList<>();
        for (int i = 0; i < numberOfLocals; i++) {
            RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
            when(repoDescriptor.getKey()).thenReturn("local-repo-" + i);
            repoDescriptors.add(repoDescriptor);
        }

        for (int i = 0; i < numberOfRemotes; i++) {
            RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
            when(repoDescriptor.getKey()).thenReturn("remote-repo-" + i + "-cache");
            repoDescriptors.add(repoDescriptor);
        }

        when(repositoryService.getVirtualResolvedLocalFederatedAndCacheDescriptors(repoName))
                .thenReturn(repoDescriptors);
    }

    @Nonnull
    private repoConfigReloadPrep getRepoConfigReloadPrep() {
        RepoConfigChangeReload createdFederatedChangeReload =
                new RepoConfigChangeReload(RepoConfigChange.createOf(FederatedRepoConfig.create("fed-key", Generic)));
        RepoConfigChangeReload deleteRemoteChangeReload = new RepoConfigChangeReload(
                RepoConfigChange.deleteOf(RemoteRepoConfig.create("remote-key-delete", Generic)));
        RemoteRepoConfig oldRemoteRepoConfig = RemoteRepoConfig.create("remote-key-update", Generic);
        RemoteRepoConfig newRemoteRepoConfig = RemoteRepoConfig.create("remote-key-update", Generic);
        RepoConfigChangeReload updateRemoteConfigChangeReload =
                new RepoConfigChangeReload(RepoConfigChange.updateOf(oldRemoteRepoConfig, newRemoteRepoConfig));

        Map<RepoDetailsType, List<RepoConfigChangeReload>> changes =
                Map.of(FEDERATED, List.of(createdFederatedChangeReload),
                        REMOTE, List.of(deleteRemoteChangeReload, updateRemoteConfigChangeReload)
                );

        OnlineMonitorService onlineMonitorService = mock(OnlineMonitorService.class);
        repositoryService.setOnlineMonitorService(onlineMonitorService);
        VersioningCacheImpl<RepositoriesCache> cache = mock(VersioningCacheImpl.class);
        when(cache.get()).thenReturn(repositoriesCache);
        repositoryService.cache = cache;
        return new repoConfigReloadPrep(new RepoConfigChangesReload(changes), onlineMonitorService, cache);
    }

    @Test
    void testDeprecatedRemoveRepositoryFlow() {
        String repoKey = "repoKey";
        doNothing().when(repositoryService).deleteRepositoryContent(eq(repoKey), any(BasicStatusHolder.class));
        BasicStatusHolder statusHolder = repositoryService.deprecatedRemoveRepositoryFlow(repoKey);
        assertEquals(SC_OK, statusHolder.getStatusCode());
        assertEquals("Repository '%s' and all its content have been removed successfully.%n".formatted(repoKey),
                statusHolder.getStatusMsg());
    }

    @Test
    void testDeprecatedRemoveRepositoryFlow_ArtifactNotFound() {
        String repoKey = "repoKey";
        StoringRepo<?, ?> storingRepo = mock(StoringRepo.class);
        when(storingRepo.getKey()).thenReturn(repoKey);
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getImmutableFsItem(any(RepoPath.class))).thenReturn(null);

        doReturn(localRepo).when(repositoryService).getLocalRepository(RepoPathFactory.create(repoKey, ""));
        when(storingRepo.isLocal()).thenReturn(true);
        doReturn(storingRepo).when(repositoryService).storingRepositoryByKey(repoKey);
        // Create mock config BEFORE when() to avoid nested when() calls
        RepoConfig mockRepoConfig = createMockRepoConfig(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);
        when(internalRepositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);

        BasicStatusHolder statusHolder = repositoryService.deprecatedRemoveRepositoryFlow(repoKey);

        assertEquals(SC_OK, statusHolder.getStatusCode());
    }

    @Test
    void testDeprecatedRemoveRepositoryFlow_DeleteRepoNotAllowed() {
        String repoKey = "repoKey";
        doAnswer(
                (Answer<Object>) invocation -> {
                    BasicStatusHolder basicStatusHolder = invocation.getArgument(1);
                    basicStatusHolder.error("Delete repo: %s not allowed".formatted(repoKey), SC_BAD_REQUEST, log);
                    return null;
                }).when(storageInterceptors).assertDeleteRepoAllowed(eq(repoKey), any(BasicStatusHolder.class));
        BasicStatusHolder statusHolder = repositoryService.deprecatedRemoveRepositoryFlow(repoKey);
        assertEquals(SC_BAD_REQUEST, statusHolder.getStatusCode());
        assertEquals("Delete repo: %s not allowed".formatted(repoKey), statusHolder.getStatusMsg());
    }

    @Test
    void testDeprecatedRemoveRepositoryFlow_RemoteRepoWithFederation_Success() {
        String repoKey = "remote-repo";
        RemoteRepoConfig remoteConfig = RemoteRepoConfig.create(repoKey, Generic);
        remoteConfig.getRepoTypeConfig().setUrl("https://registry.example.com");
        remoteConfig.getBaseConfig().getFederationConfig().setFederationOnGrid(true);

        RepoConfig mockRepoConfig = createMockRepoConfigWithFederation(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);
        when(internalRepositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);
        doNothing().when(repositoryService).deleteRepositoryContent(eq(repoKey), any(BasicStatusHolder.class));

        BasicStatusHolder statusHolder = repositoryService.deprecatedRemoveRepositoryFlow(repoKey);

        assertEquals(SC_OK, statusHolder.getStatusCode());
        verify(federatedRepoAddon).removeRepoFromFederationMembers(any(RepoConfig.class));
    }

    @Test
    void testDeprecatedRemoveRepositoryFlow_RemoteRepoWithFederation_Failure() {
        String repoKey = "remote-repo";
        RepoConfig mockRepoConfig = createMockRepoConfigWithFederation(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);
        when(internalRepositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);
        doNothing().when(repositoryService).deleteRepositoryContent(eq(repoKey), any(BasicStatusHolder.class));
        doThrow(new FederatedRemoteMembersUpdateException(List.of("url1", "url2"), "Federation sync failed"))
                .when(federatedRepoAddon).removeRepoFromFederationMembers(any(RepoConfig.class));

        BasicStatusHolder statusHolder = repositoryService.deprecatedRemoveRepositoryFlow(repoKey);

        assertTrue(statusHolder.hasErrors());
        verify(federatedRepoAddon).removeRepoFromFederationMembers(any(RepoConfig.class));
    }

    @Test
    void testDeprecatedRemoveRepositoryFlow_RemoteRepoWithoutFederation_SkipsFederationRemoval() {
        String repoKey = "remote-repo";
        RepoConfig mockRepoConfig = createMockRepoConfig(repoKey);
        when(repositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);
        when(internalRepositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(mockRepoConfig);
        doNothing().when(repositoryService).deleteRepositoryContent(eq(repoKey), any(BasicStatusHolder.class));

        BasicStatusHolder statusHolder = repositoryService.deprecatedRemoveRepositoryFlow(repoKey);

        assertEquals(SC_OK, statusHolder.getStatusCode());
        verify(federatedRepoAddon, never()).removeRepoFromFederationMembers(any(RepoConfig.class));
    }

    @Test
    public void testGetImmutableFile() {
        String repoKey = "repoKey";
        RepoPath repoPath = RepoPathFactory.create(repoKey, "path/to/file");
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getImmutableFile(repoPath)).thenReturn(mock(VfsFile.class));
        when(repositoryService.storingRepositoryByKey(repoKey)).thenReturn(localRepo);

        VfsFile<?> immutableFile = repositoryService.getImmutableFile(repoPath);

        assertNotNull(immutableFile, "FileInfo should not be null");
    }

    @Test
    public void testGetImmutableFileNotFound() {
        String repoKey = "repoKey";
        RepoPath repoPath = RepoPathFactory.create(repoKey, "path/to/file");
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getImmutableFile(repoPath)).thenReturn(null);
        when(repositoryService.storingRepositoryByKey(repoKey)).thenReturn(localRepo);

        assertThrows(FileExpectedException.class, () -> repositoryService.getImmutableFile(repoPath));
    }

    @Test
    void testInternalUnexpireIfExists_FileExistsAndNeedsUpdate() {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/file");
        DbCacheRepo localRepo = mock(DbCacheRepo.class, RETURNS_DEEP_STUBS);
        when(localRepo.getKey()).thenReturn(repoPath.getRepoKey());
        VfsItem<?> vfsItem = mock(VfsItem.class, RETURNS_DEEP_STUBS);
        when(localRepo.getImmutableFsItem(repoPath)).thenReturn(vfsItem);
        when(vfsItem.isFile()).thenReturn(true);
        long now = System.currentTimeMillis();
        when(vfsItem.getInfo().getLastUpdated()).thenReturn(now - nodeLastUpdatedThresholdMillis.getLong() - 10000);

        MutableVfsItem<?> mutableVfsItem = mock(MutableVfsItem.class);
        when(localRepo.getMutableFsItem(repoPath)).thenReturn(mutableVfsItem);

        RepoResource repoResource =
                repositoryService.internalUnexpireIfExists(localRepo, repoPath.getPath(), now);

        assertNotNull(repoResource);
        verify(mutableVfsItem).setUpdated(now);
    }

    @Test
    void testInternalUnexpireIfExists_FileDoesNotExist() {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/file");
        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn(repoPath.getRepoKey());
        when(localRepo.getImmutableFsItem(repoPath)).thenReturn(null);

        RepoResource repoResource =
                repositoryService.internalUnexpireIfExists(localRepo, repoPath.getPath(), System.currentTimeMillis());

        assertNull(repoResource);
    }

    @Test
    void testInternalUnexpireIfExists_FileExistsButNoUpdateNeeded() {
        RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/file");
        DbCacheRepo localRepo = mock(DbCacheRepo.class, RETURNS_DEEP_STUBS);
        when(localRepo.getKey()).thenReturn(repoPath.getRepoKey());
        VfsItem<?> vfsItem = mock(VfsItem.class, RETURNS_DEEP_STUBS);
        when(localRepo.getImmutableFsItem(any(RepoPath.class))).thenReturn(vfsItem);
        when(vfsItem.isFile()).thenReturn(true);
        long now = System.currentTimeMillis();
        when(vfsItem.getInfo().getLastUpdated()).thenReturn(now);

        RepoResource repoResource =
                repositoryService.internalUnexpireIfExists(localRepo, repoPath.getPath(), now);

        assertNull(repoResource);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testMoveWithEnabledCacheOperations(boolean copyToCacheAllowed) {
        RepoPath sourcePath = RepoPathFactory.create("repoKey", "path/src/file");
        RepoPath targetPath = RepoPathFactory.create("repoKey", "path/target/file");
        DbCacheRepo localRepo = mock(DbCacheRepo.class, RETURNS_DEEP_STUBS);
        when(localRepo.getKey()).thenReturn(sourcePath.getRepoKey());
        VfsItem<?> vfsItem = mock(VfsItem.class, RETURNS_DEEP_STUBS);
        when(localRepo.getImmutableFsItem(any(RepoPath.class))).thenReturn(vfsItem);
        when(vfsItem.isFile()).thenReturn(true);
        long now = System.currentTimeMillis();
        when(vfsItem.getInfo().getLastUpdated()).thenReturn(now);

        final MoveMultiStatusHolder moveResult = repositoryService.move(sourcePath, targetPath,
                false, true, true, true, copyToCacheAllowed, new PropertiesImpl(), false);
        assertNotNull(moveResult);
        verify(moveRepoPathService, times(1))
                .executeOperation(any(MoveMultiStatusHolder.class), moverConfigCaptor.capture());
        final MoverConfig moverConfig = moverConfigCaptor.getValue();
        assertNotNull(moverConfig);
        assertEquals(copyToCacheAllowed, moverConfig.isToCache());
    }

    @Test
    void testDeleteRepositoryContent_Success() {
        String repoKey = "example-repo";
        RepoPath repoPath = RepoPathFactory.create(repoKey);
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.isLocal()).thenReturn(true);
        when(localRepo.getKey()).thenReturn(repoKey);
        VfsItem<?> vfsItem = mock(VfsItem.class);
        ItemInfo itemInfo = mock(ItemInfo.class);
        when(itemInfo.getRepoPath()).thenReturn(repoPath);
        when(vfsItem.getInfo()).thenReturn(itemInfo);
        when(localRepo.getImmutableFsItem(repoPath)).thenReturn(vfsItem);

        when(repositoryService.storingRepositoryByKey(repoKey)).thenReturn(localRepo);
        when(repositoryService.localFederatedOrCachedRepositoryByKey(repoKey)).thenReturn(localRepo);
        when(storageInterceptors.canDelete(eq(repoPath), any(BasicStatusHolder.class))).thenReturn(true);
        when(authorizationService.canDelete(repoPath)).thenReturn(true);

        repositoryService.deleteRepositoryContent(repoKey, statusHolder);

        assertFalse(statusHolder.hasErrorsOrWarnings(), statusHolder.getStatusMsg());
    }

    @Test
    void testDeleteNonLocalRepositoryContent_Success() {
        String repoKey = "non-local-repo";
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
        when(localRepo.isLocal()).thenReturn(false);

        when(repositoryService.storingRepositoryByKey(repoKey)).thenReturn(localRepo);

        repositoryService.deleteRepositoryContent(repoKey, statusHolder);

        assertFalse(statusHolder.hasErrorsOrWarnings(), statusHolder.getStatusMsg());
    }

    @Test
    void testDeleteRepositoryContent_Failure() {
        String repoKey = "non-existent-repo";
        BasicStatusHolder statusHolder = new BasicStatusHolder();

        repositoryService.deleteRepositoryContent(repoKey, statusHolder);


        assertEquals(SC_NOT_FOUND, statusHolder.getStatusCode());
        assertEquals(REPOSITORY_NOT_FOUND.getReason(repoKey), statusHolder.getStatusMsg());
    }

    /**
     * fake-cache is a remote repo that is called fake-cache. The cache repo has a cache repo named fake-cache-cache.
     * An old bug made it possible to create such repos.
     * This test verifies that we can still get the remote repo config even if it's named with -cache.
     */
    @Test
    void testGetRemoteCacheRepoConfigs() {
        String fakeCacheRepoKey = "fake-cache-cache";
        RemoteRepoConfig fakeCacheRepoConfig = RemoteRepoConfig.create(fakeCacheRepoKey, Generic);
        InternalRepositoryConfigService internalRepositoryConfigService = mock(InternalRepositoryConfigService.class);
        setField(repositoryService, "repositoryConfigService", internalRepositoryConfigService);
        when(internalRepositoryConfigService.isManaged(fakeCacheRepoKey)).thenReturn(true);
        Repo<?, RemoteRepoConfig> cacheRepo = mock(Repo.class);
        when(cacheRepo.getRepoConfig()).thenReturn(fakeCacheRepoConfig);
        when(internalRepositoryConfigService.getRemoteRepoConfigByKey(fakeCacheRepoKey)).thenReturn(
                fakeCacheRepoConfig);
        when(repositoryService.repositoryByKey(fakeCacheRepoKey)).thenReturn(cacheRepo);
        RepoConfig repoConfigByKey = repositoryService.getRepoConfigByKey(fakeCacheRepoKey);
        assertEquals(repoConfigByKey, fakeCacheRepoConfig);
    }

    @Test
    void testGetRepositoryConfigByKey() {
        String repoKey = "repoKey";
        LocalRepoConfig localRepoConfig = LocalRepoConfig.create(repoKey, Generic);
        when(repositoryConfigService.getRepoConfigByKey(repoKey)).thenReturn(localRepoConfig);
        when(internalRepositoryConfigService.isManaged(repoKey)).thenReturn(true);

        RepoConfig result = repositoryService.getRepositoryConfigByKey(repoKey);

        assertNotNull(result);
        assertEquals(localRepoConfig, result);
    }

    @Test
    void testGetCachedRepoConfigs() {
        RemoteRepoConfig remoteRepoConfig = RemoteRepoConfig.create("generic-remote", Generic);
        when(repositoryConfigService.getRepoConfigsByType(REMOTE)).thenReturn(List.of(remoteRepoConfig));

        List<LocalCacheRepoConfig> cachedRepoConfigs = repositoryService.getCachedRepoConfigs();

        assertNotNull(cachedRepoConfigs);
        assertEquals(1, cachedRepoConfigs.size());
        assertEquals(LocalCacheRepoConfig.create(remoteRepoConfig), cachedRepoConfigs.get(0));
    }

    @Test
    void whenIsArtifactoryFailed() throws IOException {
        doThrow(IllegalStateException.class).when(repositoryService).executeRemotePing("repoKey");

        assertFalse(repositoryService.isArtifactory("repoKey"));
    }

    @Test
    void whenIsArtifactorySucceededButNoHeader() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        doReturn(response).when(repositoryService).executeRemotePing("repoKey");

        assertFalse(repositoryService.isArtifactory("repoKey"));
    }

    @Test
    void whenIsArtifactorySucceeded() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getHeaders(eq(ARTIFACTORY_ID))).thenReturn(new Header[]{ mock(Header.class)});
        doReturn(response).when(repositoryService).executeRemotePing("repoKey");

        assertTrue(repositoryService.isArtifactory("repoKey"));
    }

    @Test
    void setHeaderUsesCaseInsensitiveLookup() {
        HttpGet getMethod = new HttpGet("http://localhost");
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("User-Agent", "case-insensitive-agent");

        ReflectionTestUtils.invokeMethod(repositoryService, "setHeader", getMethod, headersMap, HttpHeaders.USER_AGENT);

        assertNotNull(getMethod.getFirstHeader(HttpHeaders.USER_AGENT));
        assertEquals("case-insensitive-agent", getMethod.getFirstHeader(HttpHeaders.USER_AGENT).getValue());
    }

    private record repoConfigReloadPrep(RepoConfigChangesReload changes,
                                        OnlineMonitorService onlineMonitorService,
                                        VersioningCacheImpl<RepositoriesCache> cache) {
    }

    @Test
    void testImportAll(){
        repositoryService = spy(RepositoryServiceImpl.class);
        setField(repositoryService, "repositoryConfigService", repositoryConfigService);
        List<String> repoKeys = List.of("local-repo-1", "local-repo-2");
        ImportSettingsImpl importSettings = mock(ImportSettingsImpl.class);

        when(importSettings.getBaseDir()).thenReturn(new File("/tmp"));
        when(repositoryConfigService.filterReposAllowedForImport(repoKeys)).thenReturn(repoKeys);
        doReturn(repoKeys)
                .when(repositoryService).getAllLocalRepoTypeKeys();
        doNothing().when(repositoryService).importRepositoriesFromSettings(any(RepositoryImportSettingsImpl.class));

        repositoryService.importAll(importSettings);

        verify(repositoryConfigService, times(1)).filterReposAllowedForImport(repoKeys);
    }

    @ParameterizedTest
    @MethodSource("repoKeysProvider")
    void testGetLocalReposToImport(List<String> importRepoKeys) {
        ImportSettingsImpl importSettings = mock(ImportSettingsImpl.class);
        List<String> repoKeysToDelete = List.of("local-repo-1", "local-repo-2");

        when(importSettings.getRepositories()).thenReturn(importRepoKeys);
        when(repositoryConfigService.filterReposAllowedForImport(importRepoKeys)).thenReturn(repoKeysToDelete);

        List<String> localReposToImport =
                repositoryService.getLocalReposToImport(importSettings, repoKeysToDelete);

        if(!importRepoKeys.isEmpty()){
            verify(repositoryConfigService, times(1)).filterReposAllowedForImport(importRepoKeys);
        } else {
            verify(repositoryConfigService, never()).filterReposAllowedForImport(any());
        }
        assertEquals(localReposToImport, repoKeysToDelete);
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid/path", "another/valid/path"})
    void assertNonRootPathDeleteAllowsValidPaths(String path) {
        PathDeletionContext context = mock(PathDeletionContext.class);
        StoringRepo<?, ?> repo = mock(StoringRepo.class);
        BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);

        when(repo.getKey()).thenReturn("repoKey");
        when(context.getPath()).thenReturn(path);

        doNothing().when(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());

        repositoryService.assertNonRootPathDelete(context, repo, path, statusHolder);

        verify(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());
        verify(statusHolder, never()).error(any(), any());
    }

    @Test
    void assertNonRootPathDelete_overwriteIntent_firesOverwriteAllowedChainOnly() {
        PathDeletionContext context = mock(PathDeletionContext.class);
        when(context.isAssertOverwrite()).thenReturn(true);

        StoringRepo<?, ?> repo = mock(StoringRepo.class);
        when(repo.getKey()).thenReturn("repoKey");
        when(repo.shouldProtectPathDeletion(any(PathDeletionContext.class))).thenReturn(false);

        BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
        doNothing().when(repositoryService).fireAssertOverwriteAllowedEvent(any(), any());
        doNothing().when(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());

        repositoryService.assertNonRootPathDelete(context, repo, "valid/path", statusHolder);

        verify(repositoryService).fireAssertOverwriteAllowedEvent(any(), any());
        verify(repositoryService, never()).fireAssertDeletePathAllowedEvent(any(), any());
    }

    @Test
    void assertNonRootPathDelete_deleteIntent_firesDeletePathAllowedChainOnly() {
        PathDeletionContext context = mock(PathDeletionContext.class);
        when(context.isAssertOverwrite()).thenReturn(false);

        StoringRepo<?, ?> repo = mock(StoringRepo.class);
        when(repo.getKey()).thenReturn("repoKey");
        when(repo.shouldProtectPathDeletion(any(PathDeletionContext.class))).thenReturn(false);
        when(repo.itemExists("valid/path")).thenReturn(true);

        BasicStatusHolder statusHolder = mock(BasicStatusHolder.class);
        doNothing().when(repositoryService).fireAssertOverwriteAllowedEvent(any(), any());
        doNothing().when(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());

        repositoryService.assertNonRootPathDelete(context, repo, "valid/path", statusHolder);

        verify(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());
        verify(repositoryService, never()).fireAssertOverwriteAllowedEvent(any(), any());
    }

    @Test
    void moveFlat_targetOverwrite_firesOverwriteAllowedChainOnly() {
        FlatMoveCopyService flatMoveCopyService = mock(FlatMoveCopyService.class);
        when(flatMoveCopyService.moveCopy(any())).thenReturn(new MoveMultiStatusHolder());
        setField(repositoryService, "flatMoveCopyService", flatMoveCopyService);

        RepoPath source = RepoPathFactory.create("sourceRepo", "source/path");
        doNothing().when(repositoryService).fireAssertOverwriteAllowedEvent(any(), any());
        doNothing().when(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());

        repositoryService.moveFlat(source, "targetKey", "target/path",
                false, false, 1000, repoPath -> null, repoPath -> null);

        ArgumentCaptor<RepoPath> overwritePaths = ArgumentCaptor.forClass(RepoPath.class);
        ArgumentCaptor<RepoPath> deletePaths = ArgumentCaptor.forClass(RepoPath.class);
        verify(repositoryService).fireAssertOverwriteAllowedEvent(overwritePaths.capture(), any());
        verify(repositoryService).fireAssertDeletePathAllowedEvent(deletePaths.capture(), any());
        assertEquals("targetKey", overwritePaths.getValue().getRepoKey());
        assertEquals("target/path", overwritePaths.getValue().getPath());
        assertEquals(source, deletePaths.getValue());
    }

    @Test
    void copyFlat_targetOverwrite_firesOverwriteAllowedChainOnly() {
        FlatMoveCopyService flatMoveCopyService = mock(FlatMoveCopyService.class);
        when(flatMoveCopyService.moveCopy(any())).thenReturn(new MoveMultiStatusHolder());
        setField(repositoryService, "flatMoveCopyService", flatMoveCopyService);

        RepoPath source = RepoPathFactory.create("sourceRepo", "source/path");
        doNothing().when(repositoryService).fireAssertOverwriteAllowedEvent(any(), any());
        doNothing().when(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());

        repositoryService.copyFlat(source, "targetKey", "target/path",
                false, false, 1000, repoPath -> null, repoPath -> null);

        ArgumentCaptor<RepoPath> overwritePaths = ArgumentCaptor.forClass(RepoPath.class);
        verify(repositoryService).fireAssertOverwriteAllowedEvent(overwritePaths.capture(), any());
        verify(repositoryService, never()).fireAssertDeletePathAllowedEvent(any(), any());
        assertEquals("targetKey", overwritePaths.getValue().getRepoKey());
        assertEquals("target/path", overwritePaths.getValue().getPath());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assertNonRootPathDeleteHandlesHideUnauthorizedResources(boolean hideUnauthorizedResources) {
        PathDeletionContext pathDeletionContext = mock(PathDeletionContext.class);
        when(pathDeletionContext.getPath()).thenReturn("valid/path");

        StoringRepo<?, ?> repo = mock(StoringRepo.class);
        when(repo.getKey()).thenReturn("repoKey");
        when(repo.shouldProtectPathDeletion(pathDeletionContext)).thenReturn(true);

        CentralConfigService centralConfigService = mock(CentralConfigService.class, RETURNS_DEEP_STUBS);
        when(centralConfigService.getDescriptor().getSecurity().isHideUnauthorizedResources())
                .thenReturn(hideUnauthorizedResources);
        ReflectionTestUtils.setField(repositoryService, "centralConfigService", centralConfigService);

        doNothing().when(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = RepoPathFactory.create("repoKey", "valid/path");

        when(authorizationService.canDelete(repoPath)).thenReturn(false);
        when(securityService.currentUsername()).thenReturn("userName");

        repositoryService.assertNonRootPathDelete(pathDeletionContext, repo, "valid/path", statusHolder);

        if (hideUnauthorizedResources) {
            assertTrue(statusHolder.hasErrors());
            assertEquals(ARTIFACT_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
            assertEquals(ARTIFACT_NOT_FOUND.getReason(repoPath.toPrettyPath()), statusHolder.getStatusMsg());
        } else {
            assertTrue(statusHolder.hasErrors());
            assertEquals(REQUIRES_DELETE_PERMISSION.getStatusCode(), statusHolder.getStatusCode());
            assertEquals(REQUIRES_DELETE_PERMISSION.getReason(repoPath.toPrettyPath(), "userName"),
                    statusHolder.getStatusMsg());
        }
    }

    /**
     * concurrent {@code PUT /api/build} for the same build coordinates triggers an overwrite/republish
     * cleanup delete of the build-info JSON. When one request already removed the artifact, the other request's
     * delete finds it missing. With {@code idempotentDelete=true} this must be treated as a no-op success (the
     * desired end-state, artifact absent, is already met) instead of failing with {@code ARTIFACT_NOT_FOUND}
     * (which previously surfaced as an HTTP 500). With {@code idempotentDelete=false} the not-found error is
     * preserved (normal delete semantics).
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assertNonRootPathDelete_missingArtifact_idempotentDeleteSkipsNotFound(boolean idempotentDelete) {
        StoringRepo<?, ?> repo = mock(StoringRepo.class);
        when(repo.getKey()).thenReturn("repoKey");
        // Skip the delete-permission branch so the test reaches the missing-artifact check.
        when(repo.shouldProtectPathDeletion(any())).thenReturn(false);
        when(repo.itemExists("missing/path")).thenReturn(false);

        doNothing().when(repositoryService).fireAssertDeletePathAllowedEvent(any(), any());

        PathDeletionContext pathDeletionContext = new PathDeletionContext.Builder(repo, "missing/path")
                .assertOverwrite(false)
                .idempotentDelete(idempotentDelete)
                .build();
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        RepoPath repoPath = RepoPathFactory.create("repoKey", "missing/path");

        repositoryService.assertNonRootPathDelete(pathDeletionContext, repo, "missing/path", statusHolder);

        if (idempotentDelete) {
            // The not-found status must not be recorded - the missing artifact is an idempotent no-op.
            assertNotEquals(ARTIFACT_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode(),
                    "Idempotent delete of an already-missing artifact must not record a not-found status");
            assertNull(statusHolder.getStatusMsg(),
                    "Idempotent delete of an already-missing artifact must not record any status message");
        } else {
            // Normal delete semantics are preserved: a missing artifact still reports not-found.
            assertEquals(ARTIFACT_NOT_FOUND.getStatusCode(), statusHolder.getStatusCode());
            assertEquals(ARTIFACT_NOT_FOUND.getReason(repoPath.toPrettyPath()), statusHolder.getStatusMsg());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testDeleteRepoFromAllRelatedRepoAcls(boolean enableAccessClient) {
        String repoKey = "repoKey";
        NotifyClient notificationsClient = mock(NotifyClient.class);
        AccessClient accessClient = mock(AccessClient.class);
        when(accessClient.notifications()).thenReturn(notificationsClient);
        when(accessService.getAccessClient()).thenReturn(accessClient);

        homeStub.setProperty(
                ConstantValues.enableAccessClientDeleteRepoFromPermission.getPropertyName(),
                String.valueOf(enableAccessClient));

        repositoryService.deleteRepoFromAllRelatedRepoAcls(repoKey);

        verify(notificationsClient, times(enableAccessClient ? 1 : 0)).deleteResource(ResourceType.REPO, repoKey);
        verify(authorizationService, times(enableAccessClient ? 0 : 1)).getAllRepoAcls();
    }

    @Test
    void testRepoStatsStarted() {
        homeStub.setProperty(
                ConstantValues.repoStatsEnabled.getPropertyName(),
                Boolean.TRUE.toString());
        try (MockedStatic<InternalContextHelper> context = mockStatic(InternalContextHelper.class)) {
            RepositoryServiceImpl repositoryServiceSpy = spy(RepositoryServiceImpl.class);
            repositoryServiceSpy.setTaskService(taskService);
            context.when(InternalContextHelper::get).thenReturn(mock(InternalArtifactoryContext.class));
            doNothing().when(repositoryServiceSpy).initClient();

            repositoryServiceSpy.init();
            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            verify(taskService, atLeast(1)).startTask(taskCaptor.capture(), eq(false));
            List<Task> capturedTasks = taskCaptor.getAllValues();

            Task reposStatsFlushTask = capturedTasks.stream()
                    .filter(task -> task.getAttribute(Task.JOB_NAME).equals(JobName.REPO_STATS_FLUSH))
                    .findFirst()
                    .orElse(null);
            assertNotNull(reposStatsFlushTask, "The reposStatsFlushTask should not be null");
            assertEquals(JobName.REPO_STATS_FLUSH, reposStatsFlushTask.getAttribute(Task.JOB_NAME));
        }
    }

    @Test
    void testVirtualItemExistsIncludingNested() {
        RepositoryServiceImpl repositoryServiceSpy = spy(RepositoryServiceImpl.class);

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        RepoPath repoPath = RepoPathFactory.create("virtual-repo", "path/to/item");

        doReturn(virtualRepo).when(repositoryServiceSpy).virtualRepositoryByKey("virtual-repo");
        when(virtualRepo.virtualItemExistsIncludingNested(repoPath.getPath())).thenReturn(true);

        boolean result = repositoryServiceSpy.virtualItemExistsIncludingNested(repoPath);
        assertTrue(result);
    }

    @Test
    void testVirtualItemExistsIncludingNestedRepoDoesNotExist() {
        RepoPath repoPath = RepoPathFactory.create("virtual-repo", "path/to/item");

        Assertions.assertThrows(RepositoryRuntimeException.class, () ->
                repositoryService.virtualItemExistsIncludingNested(repoPath));

    }

    private static <T extends Repo<?, ?>> Map<String, T> createRepoMap(T repo) {
        Map<String, T> repoMap = new HashMap<>();
        repoMap.put(repo.getKey(), repo);
        return repoMap;
    }

    private static LocalRepo<?, ?> createMockLocalRepo(String key) {
        LocalRepo<?, LocalConfig> localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn(key);
        when(localRepo.getRepoConfig()).thenReturn(LocalRepoConfig.create(key, Generic));
        return localRepo;
    }

    private static FederatedRepo createMockFederatedRepo(String key) {
        FederatedRepo federatedRepo = mock(FederatedRepo.class);
        when(federatedRepo.getKey()).thenReturn(key);
        when(federatedRepo.getRepoConfig()).thenReturn(FederatedRepoConfig.create(key, Generic));
        return federatedRepo;
    }

    private static LocalCacheRepo createMockLocalCacheRepo(String key) {
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        when(localCacheRepo.getKey()).thenReturn(key);
        when(localCacheRepo.getRepoConfig()).thenReturn(
                LocalCacheRepoConfig.create(RemoteRepoConfig.create(key, Generic)));
        return localCacheRepo;
    }

    @ParameterizedTest
    @MethodSource("deleteOrphanReposTestProvider")
    void testDeleteOrphanRepos(List<RepoConfigChangeReload> repoConfigReloads, int expectedAclCalls,
            String[] expectedRepoKeys) {
        // Given
        doNothing().when(repositoryService).deleteRepoFromAllRelatedRepoAcls(anyString());

        // When
        repositoryService.deleteOrphanRepos(repoConfigReloads);

        // Then
        verify(repositoryService, times(expectedAclCalls)).deleteRepoFromAllRelatedRepoAcls(anyString());

        // Verify specific repo keys if provided
        for (String repoKey : expectedRepoKeys) {
            verify(repositoryService, times(1)).deleteRepoFromAllRelatedRepoAcls(repoKey);
        }
    }

    @Test
    void testDeleteOrphanReposEmptyList() {
        // Given
        List<RepoConfigChangeReload> repoConfigReloads = List.of();

        // When
        repositoryService.deleteOrphanRepos(repoConfigReloads);

        // Then
        verify(repositoryService, never()).deleteRepoFromAllRelatedRepoAcls(anyString());
    }

    @ParameterizedTest
    @MethodSource("repoTypesProvider")
    void testDeleteOrphanReposWithDifferentRepoTypes(RepoDetailsType repoType) {
        // Given
        String repoKey = "test-repo-" + repoType.name().toLowerCase();
        RepoConfigChangeReload repoConfigReload = createRepoConfigChangeReloadWithType(repoKey, repoType);
        List<RepoConfigChangeReload> repoConfigReloads = List.of(repoConfigReload);

        doNothing().when(repositoryService).deleteRepoFromAllRelatedRepoAcls(anyString());

        // When
        repositoryService.deleteOrphanRepos(repoConfigReloads);

        // Then
        verify(repositoryService, times(1)).deleteRepoFromAllRelatedRepoAcls(repoKey);
    }

    /**
     * Provider for deleteOrphanRepos test cases
     */
    private static Stream<Arguments> deleteOrphanReposTestProvider() {
        return Stream.of(
            // Single repo deletion
            Arguments.of(
                List.of(createRepoConfigChangeReload("test-repo", REPO_DELETE)),
                1,
                new String[]{"test-repo"}
            ),
            // Multiple repos deletion
            Arguments.of(
                List.of(
                    createRepoConfigChangeReload("test-repo-1", REPO_DELETE),
                    createRepoConfigChangeReload("test-repo-2", REPO_DELETE),
                    createRepoConfigChangeReload("test-repo-3", REPO_DELETE)
                ),
                3,
                new String[]{"test-repo-1", "test-repo-2", "test-repo-3"}
            ),
            // No repos to delete (UPDATE action)
            Arguments.of(
                List.of(createRepoConfigChangeReload("test-repo", REPO_UPDATE)),
                0,
                new String[]{}
            ),
            // Mixed actions (DELETE, UPDATE, DELETE)
            Arguments.of(
                List.of(
                    createRepoConfigChangeReload("test-repo-1", REPO_DELETE),
                    createRepoConfigChangeReload("test-repo-2", REPO_UPDATE),
                    createRepoConfigChangeReload("test-repo-3", REPO_DELETE)
                ),
                2,
                new String[]{"test-repo-1", "test-repo-3"}
            ),
            // All non-DELETE actions
            Arguments.of(
                List.of(
                    createRepoConfigChangeReload("test-repo-1", REPO_CREATE),
                    createRepoConfigChangeReload("test-repo-2", REPO_UPDATE)
                ),
                0,
                new String[]{}
            )
        );
    }

    /**
     * Provider for different repository types
     */
    private static Stream<Arguments> repoTypesProvider() {
        return Stream.of(
            Arguments.of(LOCAL),
            Arguments.of(REMOTE),
            Arguments.of(VIRTUAL),
            Arguments.of(FEDERATED),
            Arguments.of(RELEASE_BUNDLE)
        );
    }

    /**
     * Helper method to create a RepoConfigChangeReload for testing
     */
    private static RepoConfigChangeReload createRepoConfigChangeReload(String repoKey,
            RepoConfigChange.ConfigAction configAction) {
        RepoConfig repoConfig = createMockRepoConfig(repoKey);
        RepoConfigChange repoConfigChange = new RepoConfigChange(repoKey, configAction, repoConfig, repoConfig);
        return new RepoConfigChangeReload(repoConfigChange);
    }

    /**
     * Helper method to create a RepoConfigChangeReload with specific repo type for testing
     */
    private static RepoConfigChangeReload createRepoConfigChangeReloadWithType(String repoKey,
            RepoDetailsType repoType) {
        RepoConfig repoConfig = createMockRepoConfigWithType(repoKey, repoType);
        RepoConfigChange repoConfigChange = new RepoConfigChange(repoKey, RepoConfigChange.ConfigAction.REPO_DELETE,
                repoConfig, repoConfig);
        return new RepoConfigChangeReload(repoConfigChange);
    }

    /**
     * Helper method to create a mock RepoConfig
     * Uses real BaseConfig and FederationConfig instances to avoid Mockito stubbing issues
     */
    private static RepoConfig createMockRepoConfig(String repoKey) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        // Use real instances - they are simple data objects
        // By default, isFederated() returns false when federationOnGrid=false and members is empty
        BaseConfig baseConfig = new BaseConfig();
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getRepoType()).thenReturn(LOCAL); // Default to LOCAL
        when(repoConfig.getPackageType()).thenReturn(Generic);
        when(repoConfig.getModification()).thenReturn(System.currentTimeMillis());
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        return repoConfig;
    }

    /**
     * Helper method to create a mock RepoConfig with specific repo type
     * Uses real BaseConfig and FederationConfig instances to avoid Mockito stubbing issues
     */
    private static RepoConfig createMockRepoConfigWithType(String repoKey, RepoDetailsType repoType) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        // Use real instances - they are simple data objects
        // By default, isFederated() returns false when federationOnGrid=false and members is empty
        BaseConfig baseConfig = new BaseConfig();
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getRepoType()).thenReturn(repoType);
        when(repoConfig.getPackageType()).thenReturn(Generic);
        when(repoConfig.getModification()).thenReturn(System.currentTimeMillis());
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        return repoConfig;
    }

    /**
     * Helper method to create a mock RepoConfig with federation enabled
     * Uses real BaseConfig with federationOnGrid=true to make isFederated() return true
     */
    private static RepoConfig createMockRepoConfigWithFederation(String repoKey) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        BaseConfig baseConfig = new BaseConfig();
        // Enable federation
        baseConfig.getFederationConfig().setFederationOnGrid(true);
        when(repoConfig.getKey()).thenReturn(repoKey);
        when(repoConfig.getRepoType()).thenReturn(REMOTE);
        when(repoConfig.getPackageType()).thenReturn(Generic);
        when(repoConfig.getModification()).thenReturn(System.currentTimeMillis());
        when(repoConfig.getBaseConfig()).thenReturn(baseConfig);
        return repoConfig;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void initRepo_shouldCallRepoInit_withExistenceValidatedFlag(boolean existenceValidated) {
        // Arrange
        Repo<?, ?> mockRepo = mock(Repo.class);

        // Act
        repositoryService.initRepo(mockRepo, existenceValidated);

        // Assert
        verify(mockRepo).init(existenceValidated);
    }

    @Test
    void deleteOrphanNodes_shouldReturnZero_whenNoOrphanNodes() {
        when(fileService.getNodeRepoPaths("test-repo", 1000)).thenReturn(List.of());

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        int deleted = repositoryService.deleteOrphanNodes("test-repo", statusHolder);

        assertEquals(0, deleted);
        assertFalse(statusHolder.hasErrors());
    }

    @Test
    void deleteOrphanNodes_shouldUndeployEachNode_whenOrphansExist() {
        // Arrange
        RepoPath path1 = RepoPathFactory.create("test-repo", "orphan1.jar");
        RepoPath path2 = RepoPathFactory.create("test-repo", "orphan2.jar");
        when(fileService.getNodeRepoPaths(eq("test-repo"), anyInt()))
                .thenReturn(List.of(path1, path2));

        try (MockedStatic<InternalContextHelper> ctx =
                     mockStatic(InternalContextHelper.class)) {
            ctx.when(InternalContextHelper::get).thenReturn(internalArtifactoryContext);
            when(internalArtifactoryContext.beanForType(InternalRepositoryService.class))
                    .thenReturn(internalRepositoryService);
            when(internalRepositoryService.undeployInTx(
                    any(RepoPath.class), eq(false), any(BasicStatusHolder.class)))
                    .thenReturn(true);

            // Act
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            int deleted = repositoryService.deleteOrphanNodes("test-repo", statusHolder);

            // Assert
            assertEquals(2, deleted);
            verify(internalRepositoryService).undeployInTx(
                    eq(path1), eq(false), any(BasicStatusHolder.class));
            verify(internalRepositoryService).undeployInTx(
                    eq(path2), eq(false), any(BasicStatusHolder.class));
        }
    }

    @Test
    void deleteOrphanNodes_fullRepoContentDeletion_shouldForwardContextFlag() {
        // Arrange
        RepoPath path = RepoPathFactory.create("test-repo", "orphan.jar");
        when(fileService.getNodeRepoPaths(eq("test-repo"), anyInt())).thenReturn(List.of(path));

        try (MockedStatic<InternalContextHelper> ctx =
                     mockStatic(InternalContextHelper.class)) {
            ctx.when(InternalContextHelper::get).thenReturn(internalArtifactoryContext);
            when(internalArtifactoryContext.beanForType(InternalRepositoryService.class))
                    .thenReturn(internalRepositoryService);
            when(internalRepositoryService.undeployInTx(
                    eq(path), eq(false), any(BasicStatusHolder.class), eq(true)))
                    .thenReturn(true);

            // Act
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            int deleted = repositoryService.deleteOrphanNodes("test-repo", statusHolder, true);

            // Assert
            assertEquals(1, deleted);
            verify(internalRepositoryService).undeployInTx(path, false, statusHolder, true);
            verify(internalRepositoryService, never()).undeployInTx(path, false, statusHolder);
        }
    }

    @Test
    void deleteOrphanNodes_shouldContinue_whenSingleUndeployFails() {
        // Arrange
        RepoPath path1 = RepoPathFactory.create("test-repo", "bad.jar");
        RepoPath path2 = RepoPathFactory.create("test-repo", "good.jar");
        when(fileService.getNodeRepoPaths(eq("test-repo"), anyInt()))
                .thenReturn(List.of(path1, path2));

        try (MockedStatic<InternalContextHelper> ctx =
                     mockStatic(InternalContextHelper.class)) {
            ctx.when(InternalContextHelper::get).thenReturn(internalArtifactoryContext);
            when(internalArtifactoryContext.beanForType(InternalRepositoryService.class))
                    .thenReturn(internalRepositoryService);
            when(internalRepositoryService.undeployInTx(
                    eq(path1), eq(false), any(BasicStatusHolder.class)))
                    .thenThrow(new RuntimeException("FK constraint"));
            when(internalRepositoryService.undeployInTx(
                    eq(path2), eq(false), any(BasicStatusHolder.class)))
                    .thenReturn(true);

            // Act
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            int deleted = repositoryService.deleteOrphanNodes("test-repo", statusHolder);

            // Assert - first failed, second succeeded
            assertEquals(1, deleted);
            assertTrue(statusHolder.hasErrors());
        }
    }

    @Test
    void deleteOrphanNodes_shouldCountOnlySuccessful_whenUndeployReturnsFalse() {
        // Arrange
        RepoPath path1 = RepoPathFactory.create("test-repo", "file1.jar");
        when(fileService.getNodeRepoPaths(eq("test-repo"), anyInt()))
                .thenReturn(List.of(path1));

        try (MockedStatic<InternalContextHelper> ctx =
                     mockStatic(InternalContextHelper.class)) {
            ctx.when(InternalContextHelper::get).thenReturn(internalArtifactoryContext);
            when(internalArtifactoryContext.beanForType(InternalRepositoryService.class))
                    .thenReturn(internalRepositoryService);
            when(internalRepositoryService.undeployInTx(
                    any(RepoPath.class), eq(false), any(BasicStatusHolder.class)))
                    .thenReturn(false);

            // Act
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            int deleted = repositoryService.deleteOrphanNodes("test-repo", statusHolder);

            // Assert
            assertEquals(0, deleted);
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "false, Maven, false",
            "false, BuildInfo, false",
            "true, Maven, false",
            "true, BuildInfo, true",
    })
    void shouldExcludeBuildInfo(
            boolean excludeBuilds,
            String repoType,
            boolean shouldExcludeBuildInfo) {
        ExportSettings settings = mock(ExportSettings.class);
        when(settings.isExcludeBuilds()).thenReturn(excludeBuilds);
        RepoDescriptor descriptor = mock(RepoDescriptor.class);
        when(descriptor.getType()).thenReturn(RepoType.valueOf(repoType));
        assertEquals(shouldExcludeBuildInfo, repositoryService.shouldExcludeBuildInfo(settings, descriptor));
    }

    @Test
    void moveFlat_staticPropsOverload_appliesPropsToEveryMovedItem() {
        FlatMoveCopyService flatMoveCopyService = mock(FlatMoveCopyService.class);
        setField(repositoryService, "flatMoveCopyService", flatMoveCopyService);
        when(flatMoveCopyService.moveCopy(any(MoveCopyContext.class)))
                .thenReturn(new MoveMultiStatusHolder());

        RepoPath source = RepoPathFactory.create("source-repo", "folder");
        Properties commonProps = new PropertiesImpl();
        commonProps.putAll("key", new String[] {"value"});
        List<String> removeProps = List.of("trash.time");

        repositoryService.moveFlat(source, "target-repo", "/dest", commonProps, removeProps,
                false, true, 1000);

        ArgumentCaptor<MoveCopyContext> ctx = ArgumentCaptor.forClass(MoveCopyContext.class);
        verify(flatMoveCopyService).moveCopy(ctx.capture());
        Function<RepoPath, Properties> propsFn = ctx.getValue().getAddProps();
        String msg = "Static-Properties overload must apply the same instance to every iterated moved item";
        assertSame(commonProps, propsFn.apply(RepoPathFactory.create("target-repo", "/dest")), msg);
        assertSame(commonProps, propsFn.apply(RepoPathFactory.create("target-repo", "/dest/child")), msg);
        assertSame(commonProps, propsFn.apply(RepoPathFactory.create("target-repo", "/dest/child/leaf")), msg);
    }
}
