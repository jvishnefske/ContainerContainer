package org.artifactory.ph;

import com.jfrog.ph.acl.model.PackageArtifact;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metadata.client.MetadataClient;
import org.jfrog.metadata.client.PackageClient;
import org.jfrog.metadata.client.model.MetadataPackageImpl;
import org.jfrog.metadata.client.model.MetadataVersion;
import org.jfrog.metadata.client.model.MetadataVersionRepo;
import org.jfrog.metadata.client.model.MetadataVersionTagEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageMetadataClientImplTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock private ArtifactoryApplicationContext context;
    @Mock private PackageClient packageClient;
    @Mock private MetadataClient metadataClient;
    @Mock private MetadataEventService metadataEventService;
    @Mock private InternalRepositoryService repositoryService;
    private PackageMetadataClientImpl packageMetadataClient;

    private static final String REPO_KEY = "repoKey";
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(context.beanForType(MetadataEventService.class)).thenReturn(metadataEventService);
        when(metadataEventService.getMetadataClient()).thenReturn(metadataClient);
        when(metadataClient.packages()).thenReturn(packageClient);
        packageMetadataClient = new PackageMetadataClientImpl(repositoryService);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @SuppressWarnings("rawtypes")
    @Test
    void testSearch() {
        RepoConfig repo = mock(RepoConfig.class);
        RepoType repoType = mock(RepoType.class);
        MetadataPackageImpl metadataPackage = createMetadataPackage();

        when(repo.getPackageType()).thenReturn(repoType);
        when(repoType.name()).thenReturn("nuget");
        when(repositoryService.getRepositoryConfigByKey(REPO_KEY)).thenReturn(repo);
        when(packageClient.search(any(), any(), any())).thenReturn(List.of(metadataPackage));

        List<PackageArtifact> search = packageMetadataClient.search(REPO_KEY, "", "20");
        assertEquals(1, search.size());
        assertEquals("repoKey", search.get(0).getRepoKey());
        assertEquals("/repoKey/pkg-name.nupkg", search.get(0).getPath());
        assertEquals("pkg-name", search.get(0).getName());
        assertEquals(1L, search.get(0).getDownloadCount());
        assertEquals("nuget-tag" ,search.get(0).getProperties().get("nuget.tags").iterator().next());
        assertEquals("1.0.0-alpha" ,search.get(0).getProperties().get("nuget.version").iterator().next());
        assertEquals("pkg-name" ,search.get(0).getProperties().get("nuget.id").iterator().next());
        assertEquals("package description" ,search.get(0).getProperties().get("nuget.description").iterator().next());
        assertEquals("package description" ,search.get(0).getProperties().get("nuget.summary").iterator().next());
    }

    private MetadataPackageImpl createMetadataPackage() {
        MetadataPackageImpl pkg = mock(MetadataPackageImpl.class);
        MetadataVersion version = mock(MetadataVersion.class);
        MetadataVersionRepo repo = mock(MetadataVersionRepo.class);
        MetadataVersionTagEntity tag = mock(MetadataVersionTagEntity.class);

        when(pkg.getVersions()).thenReturn(List.of(version));
        when(pkg.getDescription()).thenReturn("package description");
        when(version.getName()).thenReturn("1.0.0-alpha");
        when(version.getPkgid()).thenReturn("pkg-name");

        when(version.getTags()).thenReturn(Set.of(tag));
        when(tag.getValue()).thenReturn("nuget-tag");

        when(version.getRepos()).thenReturn(List.of(repo));
        when(repo.getDownloadCount()).thenReturn(1L);
        when(repo.getLeadFilePath()).thenReturn("/repoKey/pkg-name.nupkg");
        when(repo.getName()).thenReturn(REPO_KEY);

        return pkg;
    }
}