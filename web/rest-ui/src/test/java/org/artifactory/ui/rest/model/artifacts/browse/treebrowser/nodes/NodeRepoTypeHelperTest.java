package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.artifactory.addon.AddonType;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.md.Properties;
import org.artifactory.metadata.service.provider.DockerMetadataProvider;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.fs.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class NodeRepoTypeHelperTest {
    AutoCloseable autoCloseable;
    NodeRepoTypeHelper nodeRepoTypeHelper;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private RepositoryService repoService;
    @Mock
    private PropertiesService propertiesService;
    @Mock
    private PackageSpecification packageSpecification;

    public static Stream<Arguments> isOciManifestFolderData() {
        String docker = RepoType.Docker.getType();
        String oci = RepoType.OCI.getType();
        String helmOci = RepoType.HelmOCI.getType();

        String ociIndexType = "application/vnd.oci.image.index.v1+json";
        String ociManifestType = "application/vnd.oci.image.manifest.v1+json";
        String dockerIndexType = "application/vnd.docker.distribution.manifest.list.v2+json";
        String dockerManifestType = "application/vnd.docker.distribution.manifest.v2+json";
        String helmArtifactType = "application/vnd.cncf.helm.config.v1+json";

        return Stream.of(
            Arguments.of("manifest.json", dockerManifestType, "", docker),
            Arguments.of("manifest.json", ociIndexType, "", docker),
            Arguments.of("manifest.json", ociManifestType, "", oci),
            Arguments.of("manifest.json", ociManifestType, null, oci),
            Arguments.of("manifest.json", ociManifestType, helmArtifactType, helmOci),
            Arguments.of("list.manifest.json", dockerIndexType, "", docker),
            Arguments.of("list.manifest.json", ociManifestType, "", docker),
            Arguments.of("list.manifest.json", ociIndexType, "", oci),
            Arguments.of("list.manifest.json", ociIndexType, null, oci),
            Arguments.of("list.manifest.json", ociIndexType, helmArtifactType, helmOci)
        );
    }

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        nodeRepoTypeHelper = spy(new NodeRepoTypeHelper(addonsManager, repoService, propertiesService,
                packageSpecification));
    }

    @AfterEach
    void cleanup() throws Exception {
        autoCloseable.close();
    }

    @Test
    void isOciManifestFolderCatchException() {
        RepoPath repoPath = RepoPathFactory.create("repo", "alpine");
        when(propertiesService.getProperties(any(RepoPath.class))).thenThrow(new RuntimeException("ignore"));
        assertEquals(nodeRepoTypeHelper.getOCIFolderType(repoPath).getType(), RepoType.Docker.getType());
        verify(propertiesService, times(1)).getProperties(any(RepoPath.class));
    }

    @ParameterizedTest
    @MethodSource("isOciManifestFolderData")
    void isOciManifestFolder(String manifestName, String mediaType, String artifactType, String expectedResult) {
        Properties properties = mock(Properties.class);
        when(properties.getFirst(DockerMetadataProvider.MANIFEST_TYPE_PROP)).thenReturn(mediaType);
        when(properties.getFirst(DockerMetadataProvider.MANIFEST_ARTIFACT_TYPE_PROP)).thenReturn(artifactType);
        RepoPath repoPath = RepoPathFactory.create("repo", "alpine");
        doAnswer(invocationOnMock -> {
            RepoPath rp = invocationOnMock.getArgument(0);
            if (rp.getName().equals(manifestName)) {
                return properties;
            }
            return new PropertiesImpl();
        }).when(propertiesService).getProperties(any(RepoPath.class));
        assertEquals(expectedResult, nodeRepoTypeHelper.getOCIFolderType(repoPath).getType());
        verify(propertiesService, times(manifestName.equals("list.manifest.json") ? 2 : 1)).getProperties(
                any(RepoPath.class));
    }

    @Test
    void isDockerManifestFolder() {
        RepoPath repoPath = RepoPathFactory.create("repo", "alpine");
        LocalRepoConfig localDockerRepoConfig = LocalRepoConfig.create("repo", RepoType.Docker);
        DockerAddon dockerAddon = mock(DockerAddon.class);
        when(repoService.localFederatedOrCachedRepoConfigByKey("repo")).thenReturn(localDockerRepoConfig);
        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(addonsManager.addonByType(DockerAddon.class)).thenReturn(dockerAddon);
        when(dockerAddon.isDockerManifestFolder(repoPath)).thenReturn(true);
        assertTrue(nodeRepoTypeHelper.isDockerManifestFolder(repoPath));
    }

    @Test
    void testIsManifestFolderWhileDockerAddonDisabled() {
        RepoPath repoPath = RepoPathFactory.create("repo", "cool-file");
        DockerAddon dockerAddon = mock(DockerAddon.class);
        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(false);
        when(addonsManager.addonByType(DockerAddon.class)).thenReturn(dockerAddon);
        assertFalse(nodeRepoTypeHelper.isDockerManifestFolder(repoPath));
    }

    @Test
    void testGetDockerManifestIconType_dockerNotSupported() {
        RepoPath repoPath = RepoPathFactory.create("repo", "library/nginx");
        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(false);
        when(addonsManager.isAddonSupported(AddonType.OCI)).thenReturn(false);
        when(addonsManager.isAddonSupported(AddonType.HELMOCI)).thenReturn(false);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDockerManifestIconType_noManifestsFound() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(new HashMap<>());

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDockerManifestIconType_helmOCI() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/helm-chart");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.HelmOCI);

        Properties manifestProps = new PropertiesImpl();
        manifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP, "application/vnd.oci.image.manifest.v1+json");
        manifestProps.put("oci.artifact.type", "application/vnd.cncf.helm.config.v1+json");

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", manifestProps);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.HelmOCI.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_docker() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        Properties manifestProps = new PropertiesImpl();
        manifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP,
                "application/vnd.docker.distribution.manifest.v2+json");

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", manifestProps);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.Docker.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_oci() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/oci-image");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.OCI);

        Properties manifestProps = new PropertiesImpl();
        manifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP, "application/vnd.oci.image.manifest.v1+json");

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", manifestProps);

        when(addonsManager.isAddonSupported(AddonType.OCI)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.OCI.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_exception() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenThrow(new RuntimeException("Database error"));

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDockerManifestIconType_listManifest() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        Properties listManifestProps = new PropertiesImpl();
        listManifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP, "application/vnd.oci.image.index.v1+json");

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("list.manifest.json", listManifestProps);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.OCI.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_emptyManifestProperties() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", new PropertiesImpl());
        manifestMap.put("list.manifest.json", new PropertiesImpl());

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.Docker.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_nonOciManifestType() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        Properties manifestProps = new PropertiesImpl();
        manifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP,
                "application/vnd.docker.distribution.manifest.v1+json");

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", manifestProps);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.Docker.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_onlyListManifestHasProperties() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        Properties listManifestProps = new PropertiesImpl();
        listManifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP,
                "application/vnd.docker.distribution.manifest.list.v2+json");

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", new PropertiesImpl());  // Empty
        manifestMap.put("list.manifest.json", listManifestProps);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.Docker.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_bothManifestsNotInMap() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        Map<String, Properties> manifestMap = new HashMap<>();
        // Both files not in map - map has some other file
        manifestMap.put("config.json", new PropertiesImpl());

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.Docker.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_helmOciByArtifactType() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "charts/app");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);
        // Repo is Docker, not HelmOCI

        Properties manifestProps = new PropertiesImpl();
        manifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP, "application/vnd.oci.image.manifest.v1+json");
        manifestProps.put("oci.artifact.type", "application/vnd.cncf.helm.config.v1+json");

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", manifestProps);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.HelmOCI.getType(), result.get());
    }

    @Test
    void testGetDockerManifestIconType_ociWithoutHelmArtifactType() {
        RepoPath repoPath = RepoPathFactory.create("docker-local", "library/nginx");
        LocalRepoConfig config = LocalRepoConfig.create("docker-local", RepoType.Docker);

        Properties manifestProps = new PropertiesImpl();
        manifestProps.put(DockerMetadataProvider.MANIFEST_TYPE_PROP, "application/vnd.oci.image.manifest.v1+json");
        manifestProps.put("oci.artifact.type", "application/vnd.example.config.v1+json");  // Not helm

        Map<String, Properties> manifestMap = new HashMap<>();
        manifestMap.put("manifest.json", manifestProps);

        when(addonsManager.isAddonSupported(AddonType.DOCKER)).thenReturn(true);
        when(repoService.localFederatedOrCachedRepoConfigByKey("docker-local")).thenReturn(config);
        when(propertiesService.getPropertiesForFiles(any(RepoPath.class), any()))
                .thenReturn(manifestMap);

        Optional<String> result = nodeRepoTypeHelper.getDockerManifestIconType(repoPath);

        assertTrue(result.isPresent());
        assertEquals(RepoType.OCI.getType(), result.get());
    }

    @Test
    void testIsNixReferenceFolder_whenRepoSupportsNix_andPackageSpecificationReturnsTrue_returnsTrue() {
        RepoPath repoPath = RepoPathFactory.create("nix-local", "binary-cache/somehash");
        LocalRepoConfig config = LocalRepoConfig.create(repoPath.getRepoKey(), RepoType.Nix);

        when(repoService.localFederatedOrCachedRepoConfigByKey(repoPath.getRepoKey())).thenReturn(config);
        when(packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath())).thenReturn(true);

        assertTrue(nodeRepoTypeHelper.isNixReferenceFolder(repoPath));
        verify(packageSpecification, times(1))
                .isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
    }

    @Test
    void isNixReferenceFolder_whenRepoSupportsNix_andPackageSpecificationReturnsFalse_returnsFalse() {
        RepoPath repoPath = RepoPathFactory.create("nix-local", "some/path/to/package");
        LocalRepoConfig config = LocalRepoConfig.create(repoPath.getRepoKey(), RepoType.Nix);

        when(repoService.localFederatedOrCachedRepoConfigByKey(repoPath.getRepoKey())).thenReturn(config);
        when(packageSpecification.isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath())).thenReturn(false);

        assertFalse(nodeRepoTypeHelper.isNixReferenceFolder(repoPath));
        verify(packageSpecification, times(1))
                .isPackageArtifact(repoPath.getRepoKey(), repoPath.getPath());
    }

    @Test
    void isNixReferenceFolder_whenRepoDoesNotSupportNix_returnsFalse() {
        RepoPath repoPath = RepoPathFactory.create("generic-local", "some/path/to/package");
        LocalRepoConfig config = LocalRepoConfig.create(repoPath.getRepoKey(), RepoType.Generic);

        when(repoService.localFederatedOrCachedRepoConfigByKey(repoPath.getRepoKey())).thenReturn(config);

        assertFalse(nodeRepoTypeHelper.isNixReferenceFolder(repoPath));
        verify(packageSpecification, times(0)).isPackageArtifact(any(), any());
    }

}
