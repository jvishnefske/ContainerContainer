package org.artifactory.repo.virtual;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.db.DbStoringRepoMixin;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.ph.PackageVirtualResolutionService;
import org.artifactory.repo.virtual.interceptor.VirtualRepoInterceptor;
import org.artifactory.storage.service.StatsServiceImpl;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.threadlocal.cache.ReposItemsThreadLocalCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Arko Jyoti De
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VirtualRepoTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    InternalRepositoryService internalRepositoryService;

    @Mock
    private ArtifactoryContext artifactoryContext;

    @Mock
    private VirtualRepositoryConfigResolver virtualRepositoryConfigResolver;

    @BeforeEach
    void setUp() {
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(artifactoryContext.getCentralConfig()).thenReturn(centralConfigService);
        lenient().when(artifactoryContext.beanForType(any(Class.class)))
                .thenAnswer(inv -> mock((Class<?>) inv.getArgument(0)));
        when(artifactoryContext.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(mock(AddonsManager.class));
        when(artifactoryContext.beanForType(PackageVirtualResolutionService.class))
                .thenReturn(mock(PackageVirtualResolutionService.class));
        when(artifactoryContext.beanForType(VirtualRepositoryConfigResolver.class))
                .thenReturn(virtualRepositoryConfigResolver);
        when(artifactoryContext.beanForType(PathTranslator.class)).thenReturn(mock(PathTranslator.class));
        when(artifactoryContext.beanForType(StatsServiceImpl.class)).thenReturn(mock(StatsServiceImpl.class));
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testInterceptPreCalculateVirtualMetadata() {
        // Mock dependencies
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.Generic);
        VirtualRepoInterceptor interceptor1 = Mockito.mock(VirtualRepoInterceptor.class);
        VirtualRepoInterceptor interceptor2 = Mockito.mock(VirtualRepoInterceptor.class);
        Map<String, VirtualRepoInterceptor> interceptorsMap = new HashMap<>();
        interceptorsMap.put("a", interceptor1);
        interceptorsMap.put("b", interceptor2);
        // Mock other dependencies as needed
        InternalRequestContext requestContext = Mockito.mock(InternalRequestContext.class);

        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("repo");

        try (MockedStatic<ContextHelper> contextHelperMockedStatic = mockStatic(ContextHelper.class)) {
            ArtifactoryContext testContext = Mockito.mock(ArtifactoryContext.class);
            CentralConfigService testCentralConfig = Mockito.mock(CentralConfigService.class);
            Mockito.when(testContext.getCentralConfig()).thenReturn(testCentralConfig);
            Mockito.lenient().when(testContext.beanForType(Mockito.any(Class.class)))
                    .thenAnswer(inv -> Mockito.mock((Class<?>) inv.getArgument(0)));
            Mockito.when(testContext.beansForType(VirtualRepoInterceptor.class)).thenReturn(interceptorsMap);
            Mockito.when(testContext.beanForType(InternalRepositoryService.class))
                    .thenReturn(Mockito.mock(InternalRepositoryService.class));
            Mockito.when(testContext.beanForType(CentralConfigService.class)).thenReturn(testCentralConfig);
            Mockito.when(testContext.beanForType(AddonsManager.class))
                    .thenReturn(Mockito.mock(AddonsManager.class));
            Mockito.when(testContext.beanForType(VirtualRepositoryConfigResolver.class))
                    .thenReturn(Mockito.mock(VirtualRepositoryConfigResolver.class));
            Mockito.when(testContext.beanForType(PathTranslator.class))
                    .thenReturn(Mockito.mock(PathTranslator.class));
            Mockito.when(testContext.beanForType(PackageVirtualResolutionService.class))
                    .thenReturn(Mockito.mock(PackageVirtualResolutionService.class));
            Mockito.when(testContext.beanForType(StatsServiceImpl.class))
                    .thenReturn(Mockito.mock(StatsServiceImpl.class));
            contextHelperMockedStatic.when(ContextHelper::get).thenReturn(testContext);
            VirtualRepo repo = new VirtualRepo(descriptor,
                    repoConfig, Mockito.mock(InternalRepositoryService.class), null);
            // Invoke the method
            repo.dbStorageMixin = Mockito.mock(DbStoringRepoMixin.class);
            repo.init(false);
            repo.interceptPreCalculateVirtualMetadata(requestContext);

            // Verify that the preCalculateVirtualMetadata method was called on each interceptor
            Mockito.verify(interceptor1).preCalculateVirtualMetadata(repo,
                    requestContext.getResourcePath(), requestContext.isFromAnotherArtifactory());
            Mockito.verify(interceptor2).preCalculateVirtualMetadata(repo,
                    requestContext.getResourcePath(), requestContext.isFromAnotherArtifactory());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetVirtualRepoItemCacheEnabled(boolean fscStorageEnabled) {
        setStringSystemProperty(ConstantValues.fscStorageEnabled, fscStorageEnabled ? "true" : "false");
        try (MockedStatic<ReposItemsThreadLocalCacheService> ReposItemsCacheMock =
                     Mockito.mockStatic(ReposItemsThreadLocalCacheService.class)) {
            ReposItemsCacheMock.when(() -> ReposItemsThreadLocalCacheService.wrap(any(), any())).thenReturn(null);

            VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.Docker);
            VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
            descriptor.setKey("virtual_repo");

            VirtualRepo repo = new VirtualRepo(descriptor, repoConfig, internalRepositoryService, null);
            repo.dbStorageMixin = Mockito.mock(DbStoringRepoMixin.class);
            repo.init(false);

            RepoPath repoPath = Mockito.mock(RepoPath.class);
            repo.getVirtualRepoItem(repoPath);

            if (fscStorageEnabled) {
                ReposItemsCacheMock.verify(() -> ReposItemsThreadLocalCacheService.wrap(any(), any()));
            } else {
                ReposItemsCacheMock.verifyNoInteractions();
            }
        }
        finally {
            setStringSystemProperty(ConstantValues.fscStorageEnabled, "false");
        }
    }

    @Test
    public void testUndeploy() {
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.Docker);
        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("virtual_repo");

        VirtualRepo repo = new VirtualRepo(descriptor, repoConfig, internalRepositoryService, null);
        DbStoringRepoMixin dbStorageMixin = Mockito.mock(DbStoringRepoMixin.class);
        repo.dbStorageMixin = dbStorageMixin;
        DeleteContext deleteContext = new DeleteContext(new RepoPathImpl("repoKey", "path"));
        repo.init(false);

        repo.undeploy(deleteContext);

        verify(dbStorageMixin, times(1)).undeploy(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testVirtualItemExistsIncludingNested(boolean itemExists) {
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.Docker);
        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("virtual_repo");

        VirtualRepo virtualRepo = spy(new VirtualRepo(descriptor, repoConfig, internalRepositoryService, null));
        var localRepo = Mockito.mock(LocalRepo.class);
        var localRepoWithoutItem = Mockito.mock(LocalRepo.class);
        String path = "path/to/item";

        doReturn(Set.of(localRepo, localRepoWithoutItem)).when(virtualRepo).getResolvedLocalAndFederatedRepos();
        when(localRepo.itemExists(path)).thenReturn(itemExists);

        boolean actual = virtualRepo.virtualItemExistsIncludingNested(path);
        Assertions.assertEquals(itemExists, actual);
    }

    @Test
    public void testVirtualItemExistsIncludingNestedWithoutRepo() {
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("repo", RepoType.Docker);
        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("virtual_repo");

        VirtualRepo virtualRepo = spy(new VirtualRepo(descriptor, repoConfig,
                internalRepositoryService, null));
        String path = "path/to/item";

        doReturn(Set.of()).when(virtualRepo).getResolvedLocalAndFederatedRepos();

        boolean actual = virtualRepo.virtualItemExistsIncludingNested(path);
        Assertions.assertFalse(actual);
    }

    /**
     * Verifies that the same repo key can exist in different categories without collision:
     * a legacy local repo named "foo-cache" and a remote's local cache repo (also key "foo-cache")
     * must both be present after updateRepos. Previously a single aggregatedRepoKeys set could
     * drop one of them.
     */
    @Test
    void updateRepos_allowsSameKeyInLocalAndLocalCache_noCollision() {
        VirtualRepoConfig repoConfig = VirtualRepoConfig.create("virtual", RepoType.Generic);
        repoConfig.getRepoTypeConfig().setRepositoryRefs(List.of("foo-cache", "foo"));

        VirtualRepoDescriptor descriptor = new VirtualRepoDescriptor();
        descriptor.setKey("virtual");
        VirtualRepo virtualRepo = new VirtualRepo(descriptor, repoConfig, internalRepositoryService, null);
        virtualRepo.dbStorageMixin = mock(DbStoringRepoMixin.class);

        // Legacy local repo with -cache suffix (key "foo-cache")
        LocalRepo legacyLocalRepo = mock(LocalRepo.class);
        when(legacyLocalRepo.getKey()).thenReturn("foo-cache");
        when(legacyLocalRepo.isReal()).thenReturn(true);

        // Remote repo "foo" with a local cache repo (key "foo-cache")
        LocalCacheRepo cacheRepo = mock(LocalCacheRepo.class);
        when(cacheRepo.getKey()).thenReturn("foo-cache");
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.getKey()).thenReturn("foo");
        when(remoteRepo.isReal()).thenReturn(true);
        when(remoteRepo.isStoreArtifactsLocally()).thenReturn(true);
        when(remoteRepo.getLocalCacheRepo()).thenReturn(cacheRepo);

        Map<String, LocalRepo> localRepos = new HashMap<>();
        localRepos.put("foo-cache", legacyLocalRepo);
        Map<String, org.artifactory.repo.federated.FederatedRepo> federatedRepos = new HashMap<>();
        Map<String, RemoteRepo> remoteRepos = new HashMap<>();
        remoteRepos.put("foo", remoteRepo);
        Map<String, VirtualRepo> newVirtualMap = new HashMap<>();

        virtualRepo.updateRepos(newVirtualMap, localRepos, federatedRepos, remoteRepos);

        List<LocalRepo> locals = virtualRepo.getLocalRepositories();
        List<RemoteRepo> remotes = virtualRepo.getRemoteRepositories();
        List<LocalCacheRepo> caches = virtualRepo.getLocalCaches();

        Assertions.assertEquals(1, locals.size(), "Legacy local repo 'foo-cache' should be present");
        Assertions.assertSame(legacyLocalRepo, locals.get(0));
        Assertions.assertEquals(1, remotes.size(), "Remote 'foo' should be present");
        Assertions.assertSame(remoteRepo, remotes.get(0));
        Assertions.assertEquals(1, caches.size(),
                "Remote's local cache 'foo-cache' should be present (no collision with legacy local)");
        Assertions.assertSame(cacheRepo, caches.get(0));
    }
}
