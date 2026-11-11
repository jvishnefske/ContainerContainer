package org.artifactory.metadata.service.provider;

import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.artifactory.metadata.service.provider.DockerMetadataProvider.MANIFEST_TYPE_PROP;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class DockerMetadataProviderTest {

    AutoCloseable closeable;
    DockerMetadataProvider dockerMetadataProvider;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        dockerMetadataProvider = spy(new DockerMetadataProvider());
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
    }

    @Test
    void getTypeShouldReturnOci() {
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst(MANIFEST_TYPE_PROP)).thenReturn("application/vnd.oci.image.");
        Assertions.assertEquals(RepoType.OCI.getType(), dockerMetadataProvider.getType(properties));
    }

    @Test
    void getTypeShouldReturnDocker() {
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst(MANIFEST_TYPE_PROP)).thenReturn(null);
        Assertions.assertEquals(RepoType.Docker.getType(), dockerMetadataProvider.getType(properties));
    }

    @Test
    void getRepoType() {
        Assertions.assertEquals(RepoType.Docker, dockerMetadataProvider.getRepoType());
    }

    @Test
    void isLeadArtifact() {
        Assertions.assertTrue(dockerMetadataProvider.isLeadArtifact(RepoPathFactory.create("repo", "manifest.json")));
        Assertions.assertTrue(
                dockerMetadataProvider.isLeadArtifact(RepoPathFactory.create("repo", "list.manifest.json")));
        Assertions.assertTrue(
                dockerMetadataProvider.isLeadArtifact(RepoPathFactory.create("repo", "stam/stam/list.manifest.json")));
        Assertions.assertTrue(
                dockerMetadataProvider.isLeadArtifact(RepoPathFactory.create("repo", "bla/stam/manifest.json")));
        Assertions.assertFalse(
                dockerMetadataProvider.isLeadArtifact(RepoPathFactory.create("repo", "bla/stam/stam.manifest.json")));
        Assertions.assertFalse(
                dockerMetadataProvider.isLeadArtifact(RepoPathFactory.create("repo", "bla/stam/bla.json")));
    }

    @Test
    void buildVersionFilesQuery() {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        AqlApiItem aqlApiItem = new AqlApiItem(true);
        Assertions.assertTrue(aqlApiItem.isDistinct());
        Optional<AqlApiItem> optionalAqlApiItem = Optional.of(aqlApiItem);
        doReturn(optionalAqlApiItem).when(dockerMetadataProvider).getLeadArtifactSiblingsQuery(repoPath);
        Optional<AqlApiItem> res = dockerMetadataProvider.buildVersionFilesQuery(repoPath);
        Assertions.assertTrue(res.isPresent());
        Assertions.assertEquals(optionalAqlApiItem, res);
        Assertions.assertFalse(res.get().isDistinct());
    }

    @Test
    void getPkgidSuffixReturnEmpty() {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        Properties properties = Mockito.mock(Properties.class);
        Assertions.assertEquals("",
                dockerMetadataProvider.typeSpecificMetadataTemplate.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void getPkgidSuffix() {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst("docker.repoName")).thenReturn("repo");
        Assertions.assertEquals("repo",
                dockerMetadataProvider.typeSpecificMetadataTemplate.getPkgidSuffix(repoPath, properties));
    }

    @Test
    void getVersionReturnEmpty() {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        Properties properties = Mockito.mock(Properties.class);
        Assertions.assertEquals("",
                dockerMetadataProvider.typeSpecificMetadataTemplate.getVersion(repoPath, properties));
    }

    @Test
    void getVersionWithSha256() {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst("docker.manifest")).thenReturn("sha256:stam");
        Assertions.assertEquals("sha256__stam",
                dockerMetadataProvider.typeSpecificMetadataTemplate.getVersion(repoPath, properties));
    }

    @Test
    void getVersion() {
        RepoPath repoPath = Mockito.mock(RepoPath.class);
        Properties properties = Mockito.mock(Properties.class);
        when(properties.getFirst("docker.manifest")).thenReturn("latest");
        Assertions.assertEquals("latest",
                dockerMetadataProvider.typeSpecificMetadataTemplate.getVersion(repoPath, properties));
    }
}