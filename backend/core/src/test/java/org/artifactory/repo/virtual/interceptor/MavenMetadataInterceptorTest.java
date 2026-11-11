package org.artifactory.repo.virtual.interceptor;

import org.apache.commons.compress.utils.Lists;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RealRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.PathTranslator;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.request.InternalRequestContext;
import org.artifactory.request.Request;
import org.artifactory.resource.BlockedRepoResource;
import org.artifactory.resource.ResourceStreamHandle;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.common.ResourceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Shahaf Golan
 */
class MavenMetadataInterceptorTest extends ArtifactoryHomeBoundJUnitTest {
    private final String LOCAL_MAVEN_REPO = "maven-local" + UUID.randomUUID();
    private final String REMOTE_MAVEN_REPO = "maven-remote" + UUID.randomUUID();
    private final String VIRTUAL_MAVEN_REPO = "maven-virtual" + UUID.randomUUID();
    private final String LOCAL_METADATA_RESOURCE = "/org/artifactory/metadata/local-maven-metadata.xml";
    private final String REMOTE_METADATA_RESOURCE = "/org/artifactory/metadata/remote-maven-metadata.xml";
    private final String REQUEST_RESOURCE_PATH = "io/grpc/grpc-api/maven-metadata.xml";
    private AutoCloseable autoCloseable;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private PathTranslator pathTranslator;
    @Mock
    private LayoutsCoreAddon layoutsCoreAddon;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(LayoutsCoreAddon.class)).thenReturn(layoutsCoreAddon);
        when(artifactoryContext.beanForType(PathTranslator.class)).thenReturn(pathTranslator);
    }

    @AfterEach
    void cleanUp() throws Exception {
        homeStub.setProperty(ConstantValues.mvnMetadataVersion3Enabled,
                ConstantValues.mvnMetadataVersion3Enabled.getDefValue());
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }


    /**
     * The test makes sure of getting the resource from the local repo, skip on the cache repo but keep checking the
     * remote repository with the same priority resolution as the local priority resolution.
     * The test makes sure of covering an escalation case RTDEV-27777.
     */
    @Test
    void testLocalAndRemoteBothSamePriority() throws RepoRejectException, IOException {
        testMergeAggregatedMetadataFromRepos(true, 1, true, 1);
        testMergeAggregatedMetadataFromRepos(false, 1, false, 1);
    }

    /**
     * The test makes sure of getting the resource from the local or from remote repo depends on priority while
     * skipping  on the cache repo.
     */
    @Test
    void testLocalAndRemoteDifferentPriority() throws RepoRejectException, IOException {
        testMergeAggregatedMetadataFromRepos(true, 1, false, 0);
        testMergeAggregatedMetadataFromRepos(false, 0, true, 1);
    }


    /**
     * The function checks different scenarios of merging the metadata of aggregated repos under virtual.
     * The order of the repositories under the virtual when local repo is with priority true: local,remote-cache and
     * remote.
     *
     * The order of the repositories under the virtual when local repo is with priority false: remote-cache,remote,local
     *
     * The remote-cache repo is with priority resolution false, even if the remote was marked as priority resolution.
     */
    private void testMergeAggregatedMetadataFromRepos(boolean localPriority, int expectedLocalResourceTimes,
            boolean remotePriority, int ExpectedRemoteResourceTimes) throws RepoRejectException, IOException {
        homeStub.setProperty(ConstantValues.mvnMetadataVersion3Enabled, "true");
        List<RealRepo<?, ?>> repos = Lists.newArrayList();
        int indexLocalRepo;
        int indexRemoteRepo;

        // Building the repo list by priority order.
        if (remotePriority && !localPriority) {
            addingRemoteAndCacheRepo(repos, REMOTE_MAVEN_REPO, true, true);
            repos.add(mockRepo(LOCAL_MAVEN_REPO, false, true, LocalRepo.class, false));
            indexRemoteRepo = 1;
            indexLocalRepo = 2;
        } else {
            repos.add(mockRepo(LOCAL_MAVEN_REPO, localPriority, true, LocalRepo.class, false));
            addingRemoteAndCacheRepo(repos, REMOTE_MAVEN_REPO, remotePriority, true);
            indexLocalRepo = 0;
            indexRemoteRepo = 2;
        }

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        MavenMetadataInterceptor mavenMetadataInterceptor = new MavenMetadataInterceptor();
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        mavenMetadataInterceptor.setRepoService(repositoryService);
        InternalRequestContext context = mock(InternalRequestContext.class);
        when(context.getResourcePath()).thenReturn(REQUEST_RESOURCE_PATH);
        doAnswer(invocation -> {
            if (((Repo) invocation.getArgument(1)).getKey().equals(repos.get(indexLocalRepo).getKey())) {
                return createResource(LOCAL_METADATA_RESOURCE);
            }

            if (((Repo) invocation.getArgument(1)).getKey().equals(repos.get(indexRemoteRepo).getKey())) {
                return createResource(REMOTE_METADATA_RESOURCE);
            }
            return null;
        }).when(repositoryService).getResourceStreamHandle(any(), any(), any());
        Request request = spy(Request.class);
        when(request.getRepoPath()).thenReturn(
                RepoPathFactory.create(VIRTUAL_MAVEN_REPO, REQUEST_RESOURCE_PATH));
        when(context.getRequest()).thenReturn(request);
        when(pathTranslator.translateRepoRequestContext(any(), any(), any())).thenReturn(context);
        mavenMetadataInterceptor.interceptGetInfo(virtualRepo, context,
                RepoPathFactory.create(VIRTUAL_MAVEN_REPO, REQUEST_RESOURCE_PATH), repos);
        verify(repos.get(indexLocalRepo), times(expectedLocalResourceTimes)).getInfo(any());
        verify(repos.get(indexRemoteRepo - 1), times(0)).getInfo(any());
        verify(repos.get(indexRemoteRepo), times(ExpectedRemoteResourceTimes)).getInfo(any());
    }

    /**
     * Test that when a resource is blocked (e.g., by Curation), the interceptor immediately returns
     * the blocked resource without processing other repositories.
     */
    @Test
    void testBlockedResourceReturnsImmediately() {
        List<RealRepo<?, ?>> repos = Lists.newArrayList();

        // Create a local repo that will return a blocked resource
        LocalRepo localRepo = mockRepo(LOCAL_MAVEN_REPO, true, true, LocalRepo.class, false);
        repos.add(localRepo);

        // Create a remote repo that should not be accessed when local is blocked
        RemoteRepo remoteRepo = mockRepo(REMOTE_MAVEN_REPO, false, true, RemoteRepo.class, false);
        repos.add(remoteRepo);

        VirtualRepo virtualRepo = mock(VirtualRepo.class);
        MavenMetadataInterceptor mavenMetadataInterceptor = new MavenMetadataInterceptor();
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        mavenMetadataInterceptor.setRepoService(repositoryService);
        InternalRequestContext context = mock(InternalRequestContext.class);
        when(context.getResourcePath()).thenReturn(REQUEST_RESOURCE_PATH);

        // Create a blocked resource for the local repo
        RepoPath localRepoPath = RepoPathFactory.create(LOCAL_MAVEN_REPO, REQUEST_RESOURCE_PATH);
        BlockedRepoResource blockedResource = new BlockedRepoResource(localRepoPath, "Artifact blocked by Curation",
                BlockedRepoResource.BlockedType.CURATION);

        // Override the mock behavior for the local repo to return the blocked resource
        when(localRepo.getInfo(any())).thenReturn(blockedResource);

        Request request = spy(Request.class);
        when(request.getRepoPath()).thenReturn(
                RepoPathFactory.create(VIRTUAL_MAVEN_REPO, REQUEST_RESOURCE_PATH));
        when(context.getRequest()).thenReturn(request);
        when(pathTranslator.translateRepoRequestContext(any(), any(), any())).thenReturn(context);

        // Execute the interceptor
        RepoResource result = mavenMetadataInterceptor.interceptGetInfo(virtualRepo, context,
                RepoPathFactory.create(VIRTUAL_MAVEN_REPO, REQUEST_RESOURCE_PATH), repos);

        // Verify that the remote repo was NOT accessed (since local returned blocked resource)
        verify(remoteRepo, times(0)).getInfo(any());

        // Verify that the result is the blocked resource
        assert result != null;
        assert result.isBlocked();
        assert result instanceof BlockedRepoResource;
        BlockedRepoResource resultBlockedResource = (BlockedRepoResource) result;
        assert resultBlockedResource.getBlockedType() == BlockedRepoResource.BlockedType.CURATION;
    }

    private ResourceStreamHandle createResource(String pathToResource) {
        ResourceStreamHandle resourceStream = Mockito.mock(ResourceStreamHandle.class);
        when(resourceStream.getInputStream()).thenReturn(ResourceUtils.getResource(pathToResource));
        return resourceStream;
    }

    private List<RealRepo<?, ?>> addingRemoteAndCacheRepo(List<RealRepo<?, ?>> repos, String repoKey,
            boolean isPriorityResolution,
            boolean isRemoteFoundResource) {
        LocalRepo remoteCache = mockRepo(repoKey + "-cache", false, false, LocalRepo.class, false);
        when(remoteCache.isCache()).thenReturn(true);
        RemoteRepo remoteRepo = mockRepo(repoKey, isPriorityResolution, isRemoteFoundResource, RemoteRepo.class, false);
        repos.add(remoteCache);
        repos.add(remoteRepo);
        return repos;
    }

    private <T extends RealRepo> T mockRepo(String repoKey, boolean isPriorityResolution, boolean isFoundResource,
            Class<T> repoClass, boolean isCache) {
        T repo = Mockito.mock(repoClass);
        when(repo.isPriorityResolution()).thenReturn(isPriorityResolution);
        RepoResource localResource = Mockito.mock(RepoResource.class);
        when(localResource.getPath()).thenReturn(LOCAL_METADATA_RESOURCE);
        when(repo.getKey()).thenReturn(repoKey);
        when(repo.getInfo(any())).thenReturn(localResource);
        when(repo.isCache()).thenReturn(isCache);
        when(localResource.isFound()).thenReturn(isFoundResource);
        return repo;
    }
}