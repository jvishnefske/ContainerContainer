package org.artifactory.metadata.service;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.fs.FileInfo;
import org.artifactory.leadartifact.LeadArtifactFacade;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.provider.AbstractMetadataProvider;
import org.artifactory.metadata.service.provider.DockerMetadataProvider;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.fs.FileInfoImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.db.event.service.metadata.mapper.MetadataEntityMapper;
import org.artifactory.storage.db.fs.model.MetadataStats;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metadata.client.model.MetadataStatistics;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataEntityFacadeTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private FileService fileService;
    @Mock
    private PropertiesService propService;
    @Mock
    private InternalRepositoryService repoService;
    @Mock
    private MetadataEntityMapper mapper;

    private MetadataEntityFacade metadataEntityFacade;
    private static final String REPO_KEY = "nuget-local";
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() throws PackageException, IOException {
        mocks = MockitoAnnotations.openMocks(this);
        List<AbstractMetadataProvider> abstractMetadataProviders = List.of(new DockerMetadataProvider());
        this.metadataEntityFacade =
                new MetadataEntityFacade(abstractMetadataProviders, fileService, propService, repoService,
                        mapper, new LeadArtifactFacade(repoService, abstractMetadataProviders));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("testCreatePathDeletedEntityProvider")
    void testCreatePathDeletedEntityProvider(String repoKey, String path) {
        RepoPath repoPath = RepoPathFactory.create(repoKey, path);
        MetadataVersionRepo pathDeletedEntity = metadataEntityFacade.createPathDeletedEntity(repoPath);
        assertEquals(repoKey, pathDeletedEntity.getName());
        assertEquals(path, pathDeletedEntity.getLeadFilePath());
    }

    @Test
    void testCreatePathDeletedEntityStripsCacheSuffix() {
        RepoPath repoPath = RepoPathFactory.create("nuget-remote-cache", "a/b/c/pkg.nupkg");
        LocalCacheRepoConfig cacheConfig = mock(LocalCacheRepoConfig.class);
        RemoteRepoConfig remoteConfig = mock(RemoteRepoConfig.class);
        when(remoteConfig.getKey()).thenReturn("nuget-remote");
        when(cacheConfig.getRemoteRepoConfig()).thenReturn(remoteConfig);
        when(repoService.getRepoConfigByKey("nuget-remote-cache")).thenReturn(cacheConfig);

        MetadataVersionRepo pathDeletedEntity = metadataEntityFacade.createPathDeletedEntity(repoPath);

        assertEquals("nuget-remote", pathDeletedEntity.getName());
        assertEquals("a/b/c/pkg.nupkg", pathDeletedEntity.getLeadFilePath());
    }

    @Test
    void testItemStatistic() {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setType(RepoType.Docker);
        when(repoService.localFederatedOrCachedRepoDescriptorByKey("docker-local")).thenReturn(localRepoDescriptor);
        MetadataStatistics metadataStatistics =
                metadataEntityFacade.getMdsItemStatistics(
                        Map.of(RepoPathFactory.create("docker-local", ""),
                                new MetadataStats(1L, 1L, 0L, false),
                                RepoPathFactory.create("docker-local", "a/b/c/"),
                                new MetadataStats(1L, 1L, 0L, false),
                                RepoPathFactory.create("docker-local", "a/b/c/manifest.json"),
                                new MetadataStats(1L, 1L, 0L, false),
                                RepoPathFactory.create("docker-local", "a/b/c/list.manifest.json"),
                                new MetadataStats(1L, 1L, 0L, false),
                                RepoPathFactory.create("docker-local", "a/b/c/d/manifest.json"),
                                new MetadataStats(0L, 1L, 0L, false)));

        assertEquals(1, metadataStatistics.getItems().size());
        assertEquals(1, metadataStatistics.getItems().get(0).getAdditionalDownloads());
        assertEquals(1, metadataStatistics.getItems().get(0).getLastDownloaded());
        assertEquals(0, metadataStatistics.getItems().get(0).getRemoteLastDownloaded());
    }

    @Test
    void getPackageId() {
        ArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        try {
            RepositoryService repositoryService = mock(RepositoryService.class);
            AqlService aqlService = mock(AqlService.class);
            when(context.beanForType(RepositoryService.class)).thenReturn(repositoryService);
            when(context.beanForType(AqlService.class)).thenReturn(aqlService);
            LocalRepoConfig localRepoConfig = mock(LocalRepoConfig.class);
            when(localRepoConfig.isCache()).thenReturn(false);
            when(repositoryService.getRepoConfigByKey("repoKey")).thenReturn(localRepoConfig);
            AqlEagerResult<AqlItem> aqlResult = mock(AqlEagerResult.class);
            when(aqlResult.getResults()).thenReturn(Collections.emptyList());
            when(aqlService.executeQueryEager(any(AqlApiItem.class))).thenReturn(aqlResult);

            LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
            when(localRepoDescriptor.getType()).thenReturn(RepoType.Docker);
            when(repoService.localFederatedOrCachedRepoDescriptorByKey("repoKey")).thenReturn(localRepoDescriptor);
            RepoPath repoPath = RepoPathFactory.create("repoKey", "path/to/artifact");
            Properties properties = new PropertiesImpl();
            properties.put("docker.repoName", "repoKey");
            FileInfo fileInfo = new FileInfoImpl(repoPath);
            VfsItem<?> vfsItem = mock(VfsItem.class);
            when(vfsItem.getInfo()).thenReturn(fileInfo);
            when(vfsItem.getProperties()).thenReturn(properties);
            LocalRepo<?, ?> localRepo = mock(LocalRepo.class);
            when(repoService.getLocalRepository(any(RepoPath.class))).thenReturn(localRepo);
            when(localRepo.getImmutableFsItem(any(RepoPath.class))).thenReturn(vfsItem);

            Optional<String> pkgid = metadataEntityFacade.getPackageId("repoKey/path/to/artifact");
            assertTrue(pkgid.isPresent());
            assertEquals("docker://repoKey", pkgid.get());
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    @Test
    void getPackageId_NullRepoDescriptor_ReturnsEmptyOptional() {
        Optional<String> pkgid = metadataEntityFacade.getPackageId("repoKey/path/to/artifact");
        assertTrue(pkgid.isEmpty());
    }

    static Stream<Arguments> testCreatePathDeletedEntityProvider() {
        return Stream.of(
                Arguments.of(REPO_KEY, ""),
                Arguments.of(REPO_KEY, "a/b/c/"),
                Arguments.of(REPO_KEY, "a/b/c/pkg.nupkg")
        );
    }
}