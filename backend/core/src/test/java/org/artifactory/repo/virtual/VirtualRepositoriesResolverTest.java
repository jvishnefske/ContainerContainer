package org.artifactory.repo.virtual;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RealRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class VirtualRepositoriesResolverTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String LOCAL_PRIORITY_REPO = "local-priority";
    private static final String LOCAL_REPO = "local";
    private static final String REMOTE_PRIORITY_REPO = "remote-priority";
    private static final String REMOTE_PRIORITY_REPO_CACHE = "remote-priority-cache";
    private static final String REMOTE_REPO = "remote";
    private static final String REMOTE_REPO_CACHE = "remote-cache";
    private static final String VIRTUAL_REPO = "virtual";

    @Mock
    InternalRepositoryService repositoryService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    VirtualRepo virtualRepo;
    @Mock
    ArtifactoryContext artifactoryContext;
    @Mock
    PathTranslator pathTranslator;
    @Mock
    VirtualRepositoryConfigResolver virtualRepositoryConfigResolver;
    @Mock
    CentralConfigService centralConfigService;

    VirtualRepositoriesResolver virtualRepositoriesResolver;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        VirtualRepoDescriptor descriptor = mock(VirtualRepoDescriptor.class);
        List<RepoDescriptor> repos =
                List.of(createLocalDescriptor(LOCAL_REPO, false), createRemoteDescriptor(REMOTE_REPO, false),
                        createLocalDescriptor(LOCAL_PRIORITY_REPO, true),
                        createRemoteDescriptor(REMOTE_PRIORITY_REPO, true));
        when(descriptor.getRepositories()).thenReturn(repos);
        when(descriptor.getKey()).thenReturn(VIRTUAL_REPO);
        when(virtualRepo.getKey()).thenReturn(VIRTUAL_REPO);
        when(virtualRepo.getDescriptor()).thenReturn(descriptor);
        when(virtualRepo.accepts(any())).thenReturn(true);
        when(repositoryService.virtualRepositoryByKey(VIRTUAL_REPO)).thenReturn(virtualRepo);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(LayoutsCoreAddon.class)).thenReturn(mock(LayoutsCoreAddon.class));
        when(artifactoryContext.beanForType(PathTranslator.class)).thenReturn(pathTranslator);
        when(pathTranslator.translateArtifactPath(any(), any(), any())).thenReturn("");
        when(artifactoryContext.beanForType(InternalRepositoryService.class)).thenReturn(repositoryService);
        when(artifactoryContext.beanForType(VirtualRepositoryConfigResolver.class))
                .thenReturn(virtualRepositoryConfigResolver);
        when(artifactoryContext.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        stubVirtualRepositoryConfigResolverForAssembleSearch();
        virtualRepositoriesResolver = spy(new VirtualRepositoriesResolver(virtualRepo));
    }

    private void stubVirtualRepositoryConfigResolverForAssembleSearch() {
        LinkedHashSet<RepoConfig> orderedConfigs = new LinkedHashSet<>();
        orderedConfigs.add(createLocalConfigMock(LOCAL_PRIORITY_REPO, true));
        orderedConfigs.add(createLocalConfigMock(LOCAL_REPO, false));
        orderedConfigs.add(createRemoteRepoConfigMock(REMOTE_PRIORITY_REPO, true));
        orderedConfigs.add(createRemoteRepoConfigMock(REMOTE_REPO, false));
        when(virtualRepositoryConfigResolver.getRepoConfigs(
                eq(VIRTUAL_REPO), any(LinkedHashSet.class),
                any(org.artifactory.descriptor.repo.VirtualResolverFilter.class), eq(Integer.MAX_VALUE)))
                .thenReturn(orderedConfigs);
    }

    private RepoConfig createLocalConfigMock(String key, boolean priorityResolution) {
        LocalConfig config = mock(LocalConfig.class);
        when(config.getKey()).thenReturn(key);
        LocalRepositoryRepoTypeConfig repoTypeConfig = mock(LocalRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.isPriorityResolution()).thenReturn(priorityResolution);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        return config;
    }

    private RepoConfig createRemoteRepoConfigMock(String key, boolean priorityResolution) {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.getKey()).thenReturn(key);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.isPriorityResolution()).thenReturn(priorityResolution);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        return config;
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void assembleSearchRepositoriesList() {
        RepoPath repoPath = RepoPathFactory.create(VIRTUAL_REPO, "");
        List<RealRepo<?,?>> res = virtualRepositoriesResolver.assembleSearchRepositoriesList(repoPath, false);
        assertEquals(6, res.size());
        assertEquals(LOCAL_PRIORITY_REPO, res.get(0).getKey());
        assertEquals(REMOTE_PRIORITY_REPO_CACHE, res.get(1).getKey());
        assertEquals(REMOTE_PRIORITY_REPO, res.get(2).getKey());
        assertEquals(LOCAL_REPO, res.get(3).getKey());
        assertEquals(REMOTE_REPO_CACHE, res.get(4).getKey());
        assertEquals(REMOTE_REPO, res.get(5).getKey());
    }

    private RepoDescriptor createRemoteDescriptor(String repoKey, boolean priorityResolution) {
        HttpRepoDescriptor httpRepoDescriptor = new HttpRepoDescriptor();
        updateRepoDescriptor(httpRepoDescriptor, repoKey, priorityResolution);
        RemoteRepo remoteRepo = mock(RemoteRepo.class);
        when(remoteRepo.getKey()).thenReturn(repoKey);
        LocalCacheRepo localCacheRepo = mock(LocalCacheRepo.class);
        when(localCacheRepo.getRemoteRepo()).thenReturn(remoteRepo);
        when(localCacheRepo.getKey()).thenReturn(repoKey + "-cache");
        when(remoteRepo.getLocalCacheRepo()).thenReturn(localCacheRepo);
        when(repositoryService.remoteRepositoryByKey(repoKey)).thenReturn(remoteRepo);
        when(repositoryService.localFederatedOrCachedRepositoryByKey(repoKey)).thenReturn(localCacheRepo);

        return httpRepoDescriptor;
    }

    private RepoDescriptor createLocalDescriptor(String repoKey, boolean priorityResolution) {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        updateRepoDescriptor(localRepoDescriptor, repoKey, priorityResolution);
        LocalRepo localRepo = mock(LocalRepo.class);
        when(localRepo.getKey()).thenReturn(repoKey);
        when(repositoryService.localOrFederatedRepositoryByKey(repoKey)).thenReturn(localRepo);
        return localRepoDescriptor;
    }

    private void updateRepoDescriptor(RealRepoDescriptor repoDescriptor, String repoKey, boolean priorityResolution) {
        repoDescriptor.setKey(repoKey);
        repoDescriptor.setPriorityResolution(priorityResolution);
    }
}