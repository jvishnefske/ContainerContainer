package org.artifactory.ui.rest.service.admin.configuration.repositories.mapper;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.LocalRepositoryRepoTypeConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.model.VirtualRepositoryRepoTypeConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.ProjectRepo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigResponse;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.ProjectRepoHelper;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourceStatus;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepoConfigMapperTest {

    private CentralConfigDescriptor centralConfigDescriptor;
    private ProjectsService projectsService;
    private FederatedRepoService federatedRepoService;
    private PackageSpecification packageSpecification;
    private ProjectRepoHelper projectRepoHelper;
    private RepoConfigMapper repoConfigMapper;
    private ArtifactoryHome artifactoryHome;
    private ArtifactorySystemProperties artifactorySystemProperties;

    @BeforeEach
    void setUp() {
        centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        projectsService = mock(ProjectsService.class);
        federatedRepoService = mock(FederatedRepoService.class);
        packageSpecification = mock(PackageSpecification.class);
        projectRepoHelper = mock(ProjectRepoHelper.class);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        repoConfigMapper = new RepoConfigMapper(projectsService, packageSpecification,
                federatedRepoService, centralConfigService, projectRepoHelper);

        artifactoryHome = mock(ArtifactoryHome.class);
        artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
    }

    @Test
    void hasReplication_returnsTrueForLocalRepoWithEnabledReplication() {
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getKey()).thenReturn("local-repo");
        LocalReplicationDescriptor enabledReplication = mock(LocalReplicationDescriptor.class);
        when(enabledReplication.isEnabled()).thenReturn(true);
        when(centralConfigDescriptor.getMultiLocalReplications("local-repo"))
                .thenReturn(List.of(enabledReplication));

        boolean result = repoConfigMapper.hasReplication(repoConfig);

        assertTrue(result);
    }

    @Test
    void hasReplication_returnsFalseForLocalRepoWithNoEnabledReplication() {
        RepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getKey()).thenReturn("local-repo");
        when(centralConfigDescriptor.getMultiLocalReplications("local-repo"))
                .thenReturn(List.of());

        boolean result = repoConfigMapper.hasReplication(repoConfig);

        assertFalse(result);
    }

    @Test
    void hasReplication_returnsTrueForRemoteRepoWithEnabledReplication() {
        RepoConfig repoConfig = mock(RemoteRepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repoConfig.getKey()).thenReturn("remote-repo");
        RemoteReplicationDescriptor enabledReplication = mock(RemoteReplicationDescriptor.class);
        when(enabledReplication.isEnabled()).thenReturn(true);
        when(centralConfigDescriptor.getRemoteReplication("remote-repo"))
                .thenReturn(enabledReplication);

        boolean result = repoConfigMapper.hasReplication(repoConfig);

        assertTrue(result);
    }

    @Test
    void hasReplication_returnsFalseForRemoteRepoWithNoReplication() {
        RepoConfig repoConfig = mock(RemoteRepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repoConfig.getKey()).thenReturn("remote-repo");
        when(centralConfigDescriptor.getRemoteReplication("remote-repo"))
                .thenReturn(null);

        boolean result = repoConfigMapper.hasReplication(repoConfig);

        assertFalse(result);
    }

    @Test
    void hasReplication_returnsFalseForUnsupportedRepoType() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);

        boolean result = repoConfigMapper.hasReplication(repoConfig);

        assertFalse(result);
    }

    @Test
    void toResponse_mapsCommonAndLocalRepoConfigCorrectly() {
        var repoConfig = mock(LocalRepoConfig.class);
        var repoTypeConfig = mock(LocalRepositoryRepoTypeConfig.class);
        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoConfig.getKey()).thenReturn("local-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        RepoStatDto repoStatDto = mock(RepoStatDto.class);
        when(repoStatDto.getLastDownloaded()).thenReturn(100L);
        when(repoStatDto.getLastUploaded()).thenReturn(200L);

        when(packageSpecification.hasReindex(any(), any())).thenReturn(true);
        when(packageSpecification.hasMigrationAction(any())).thenReturn(false);

        when(projectsService.getProjectKeyOfRepository("local-repo")).thenReturn("proj");
        ProjectSlimEntity projectEntity = mock(ProjectSlimEntity.class);
        when(projectEntity.getName()).thenReturn("Project Name");
        when(projectsService.getProjectSpec("proj")).thenReturn(projectEntity);

        when(federatedRepoService.isProjectBuildInfoRepoFederated("proj")).thenReturn(false);

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, repoStatDto,
                Collections.emptyMap(), Set.of());

        assertEquals("local-repo", response.getRepoKey());
        assertEquals("local", response.getRepoType());
        assertEquals(RepoType.Generic, response.getPackageType());
        assertEquals(200L, response.getLastActivity());
        assertFalse(response.getInactive());
        assertTrue(response.getHasReindexAction());
        assertFalse(response.getHasMigrationAction());
        assertTrue(response.isEnableConvertToFederated());
        assertEquals("proj", response.getProjectKey());
        assertEquals("Project Name", response.getProjectName());
    }

    @Test
    void toResponse_mapsCommonAndLocalRepoConfigCorrectlyIsEnableConvertToFederated() {
        var repoConfig = mock(LocalRepoConfig.class);
        var repoTypeConfig = mock(LocalRepositoryRepoTypeConfig.class);
        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoConfig.getKey()).thenReturn("local-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        when(projectsService.getProjectKeyOfRepository("local-repo")).thenReturn("proj");

        when(federatedRepoService.isProjectBuildInfoRepoFederated("proj")).thenReturn(true);

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of());

        assertEquals("local-repo", response.getRepoKey());
        assertEquals("local", response.getRepoType());
        assertEquals(RepoType.Generic, response.getPackageType());
        assertFalse(response.isEnableConvertToFederated());
    }

    @Test
    void toResponse_mapsRemoteRepoConfigCorrectly() {
        RemoteRepoConfig repoConfig = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoConfig.getKey()).thenReturn("remote-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(repoConfig.isSmart()).thenReturn(true);
        when(repoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUrl()).thenReturn("http://remote.url");

        when(centralConfigDescriptor.getRemoteReplication("remote-repo")).thenReturn(null);

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of());

        assertEquals("remote-repo", response.getRepoKey());
        assertEquals("remote", response.getRepoType());
        assertTrue(response.getSmart());
        assertEquals("http://remote.url", response.getUrl());
        assertFalse(response.getHasEnabledReplication());
    }

    @Test
    void toResponse_mapsVirtualRepoConfigCorrectly() {
        VirtualRepoConfig repoConfig = mock(VirtualRepoConfig.class);
        VirtualRepositoryRepoTypeConfig virtualRepositoryRepoTypeConfig = mock(VirtualRepositoryRepoTypeConfig.class);
        List<String> selectedRepos = List.of("local-repo1", "remote-repo1");
        when(repoConfig.getKey()).thenReturn("virtual-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        when(repoConfig.getRepoTypeConfig()).thenReturn(virtualRepositoryRepoTypeConfig);
        when(virtualRepositoryRepoTypeConfig.getRepositoryRefs()).thenReturn(selectedRepos);

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of());

        assertEquals("virtual-repo", response.getRepoKey());
        assertEquals("virtual", response.getRepoType());
        assertEquals(selectedRepos, response.getSelectedRepos());
    }

    @Test
    void toResponse_mapsFederatedRepoConfigCorrectly() {
        FederatedRepoConfig repoConfig = mock(FederatedRepoConfig.class);
        when(repoConfig.getKey()).thenReturn("federated-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        when(centralConfigDescriptor.getMultiLocalReplications("federated-repo"))
                .thenReturn(Collections.emptyList());

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of());

        assertEquals("federated-repo", response.getRepoKey());
        assertEquals("federated", response.getRepoType());
        assertNull(response.getFederated());
        assertFalse(response.getInactive());
        assertFalse(response.getReplications());
    }

    @Test
    void toResponse_mapsFederatedRepoConfigAsLocalWithFederatedAttributeWhenFlagEnabled() {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled))
                .thenReturn(true);

        FederatedRepoConfig repoConfig = mock(FederatedRepoConfig.class);
        when(repoConfig.getKey()).thenReturn("federated-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);
        when(repoConfig.isFederated()).thenReturn(true);

        when(centralConfigDescriptor.getMultiLocalReplications("federated-repo"))
                .thenReturn(Collections.emptyList());

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of());

        assertEquals("federated-repo", response.getRepoKey());
        assertEquals("local", response.getRepoType());
        assertTrue(response.getFederated());
        assertFalse(response.getInactive());
        assertFalse(response.getReplications());
    }

    @ParameterizedTest
    @MethodSource("provideFederationAttributeScenarios")
    void toResponse_setsFederationAttributeAccordingToFlag(RepoDetailsType repoType, boolean isFederated,
                                                           boolean flagEnabled, Boolean expectedFederated,
                                                           String message) {
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.repositoriesFederationsAttributeEnabled))
                .thenReturn(flagEnabled);
        RepoConfig repoConfig = mockRepoConfigForType(repoType, isFederated);

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of());

        assertEquals(repoType.typeNameLowercase(), response.getRepoType(), message);
        assertEquals(expectedFederated, response.getFederated(), message);
    }

    private RepoConfig mockRepoConfigForType(RepoDetailsType repoType, boolean isFederated) {
        return switch (repoType) {
            case LOCAL -> {
                LocalRepoConfig config = mock(LocalRepoConfig.class);
                when(config.getRepoTypeConfig()).thenReturn(mock(LocalRepositoryRepoTypeConfig.class));
                when(config.getKey()).thenReturn("local-repo");
                when(config.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
                when(config.getPackageType()).thenReturn(RepoType.Generic);
                when(config.isFederated()).thenReturn(isFederated);
                yield config;
            }
            case REMOTE -> {
                RemoteRepoConfig config = mock(RemoteRepoConfig.class);
                RemoteRepositoryRepoTypeConfig typeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
                when(config.getRepoTypeConfig()).thenReturn(typeConfig);
                when(config.getKey()).thenReturn("remote-repo");
                when(config.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
                when(config.getPackageType()).thenReturn(RepoType.Maven);
                when(typeConfig.getUrl()).thenReturn("http://remote.url");
                when(config.isFederated()).thenReturn(isFederated);
                yield config;
            }
            case VIRTUAL -> {
                VirtualRepoConfig config = mock(VirtualRepoConfig.class);
                VirtualRepositoryRepoTypeConfig typeConfig = mock(VirtualRepositoryRepoTypeConfig.class);
                when(config.getRepoTypeConfig()).thenReturn(typeConfig);
                when(config.getKey()).thenReturn("virtual-repo");
                when(config.getRepoType()).thenReturn(RepoDetailsType.VIRTUAL);
                when(config.getPackageType()).thenReturn(RepoType.Docker);
                when(typeConfig.getRepositoryRefs()).thenReturn(List.of());
                when(config.isFederated()).thenReturn(isFederated);
                yield config;
            }
            default -> throw new IllegalArgumentException("Unsupported repo type for this test: " + repoType);
        };
    }

    private static Stream<Arguments> provideFederationAttributeScenarios() {
        return Stream.of(
                Arguments.of(RepoDetailsType.LOCAL, true, true, true,
                        "local + federated, flag ON -> federated=true"),
                Arguments.of(RepoDetailsType.REMOTE, true, true, true,
                        "remote + federated, flag ON -> federated=true"),
                Arguments.of(RepoDetailsType.VIRTUAL, true, true, true,
                        "virtual + federated, flag ON -> federated=true"),
                Arguments.of(RepoDetailsType.LOCAL, false, true, null,
                        "local non-federated, flag ON -> federated=null"),
                Arguments.of(RepoDetailsType.LOCAL, true, false, null,
                        "local + federated, flag OFF (BC) -> federated=null"),
                Arguments.of(RepoDetailsType.REMOTE, true, false, null,
                        "remote + federated, flag OFF (BC) -> federated=null"),
                Arguments.of(RepoDetailsType.VIRTUAL, true, false, null,
                        "virtual + federated, flag OFF (BC) -> federated=null")
        );
    }

    @Test
    void toResponse_mapsInActiveFederatedRepoConfigCorrectly() {
        FederatedRepoConfig repoConfig = mock(FederatedRepoConfig.class);
        String key = "federated-repo";
        when(repoConfig.getKey()).thenReturn(key);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.FEDERATED);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        when(centralConfigDescriptor.getMultiLocalReplications("federated-repo"))
                .thenReturn(Collections.emptyList());

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of(key));

        assertEquals("federated-repo", response.getRepoKey());
        assertEquals("federated", response.getRepoType());
        assertNull(response.getFederated());
        assertTrue(response.getInactive());
        assertFalse(response.getReplications());
    }

    @Test
    void toResponse_mapsReleaseBundleRepoConfigCorrectly() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("release-bundle-repo");
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.RELEASE_BUNDLE);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        when(projectsService.getProjectKeyOfRepository("release-bundle-repo")).thenReturn(null);

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                Collections.emptyMap(), Set.of());

        assertEquals("release-bundle-repo", response.getRepoKey());
        assertEquals("release bundle", response.getRepoType());
        assertFalse(response.getHasReindexAction());
    }

    @Test
    void toResponse_handlesProjectConfiguration() {
        LocalRepoConfig repoConfig = mock(LocalRepoConfig.class);
        when(repoConfig.getKey()).thenReturn("local-repo");
        var typeConfig = mock(LocalRepositoryRepoTypeConfig.class);
        when(repoConfig.getRepoTypeConfig()).thenReturn(typeConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repoConfig.getPackageType()).thenReturn(RepoType.Generic);

        String repoKey = "local-repo";
        String projectKey = "proj";
        Map<String, List<ProjectRepo>> allProjectRepos = new HashMap<>();
        ProjectResourceEntity assignedProject = mock(ProjectResourceEntity.class);
        when(assignedProject.getEnvironments()).thenReturn(Set.of("DEV"));
        when(assignedProject.getResourceStatus()).thenReturn(ProjectResourceStatus.SHARED);
        when(assignedProject.getSharedWithProjects()).thenReturn(Set.of("proj2"));
        when(assignedProject.isSharedReadOnly()).thenReturn(true);

        when(projectsService.getProjectKeyOfRepository(repoKey)).thenReturn(projectKey);
        when(projectRepoHelper.getAssignedProject(repoKey, projectKey, allProjectRepos)).thenReturn(assignedProject);

        RepoConfigResponse response = repoConfigMapper.toResponse(repoConfig, null,
                allProjectRepos, Set.of());

        assertEquals(Set.of("DEV"), response.getEnvironments());
        assertFalse(response.isTarget());
        assertEquals(Set.of("proj2"), response.getSharedWithProjects());
        assertTrue(response.isSharedReadOnly());
        assertFalse(response.isShareWithAllProjects());
    }

    @ParameterizedTest
    @MethodSource("provideIsActiveScenarios")
    void isActive_returnsExpectedResult(RepoConfig repoConfig, Set<String> inActiveFederatedRepos, boolean expected) {
        boolean result = repoConfigMapper.isActive(repoConfig, inActiveFederatedRepos);
        assertEquals(expected, result);
    }

    private static Stream<Arguments> provideIsActiveScenarios() {
        LocalRepoConfig activeLocalRepo = mock(LocalRepoConfig.class);
        LocalRepositoryRepoTypeConfig typeConfig = mock(LocalRepositoryRepoTypeConfig.class);
        when(activeLocalRepo.getRepoTypeConfig()).thenReturn(typeConfig);
        when(typeConfig.isBlackedOut()).thenReturn(false);

        LocalRepoConfig blackedOutLocalRepo = mock(LocalRepoConfig.class);
        LocalRepositoryRepoTypeConfig blackedOutLocalRepoTypeConfig = mock(LocalRepositoryRepoTypeConfig.class);
        when(blackedOutLocalRepo.getRepoTypeConfig()).thenReturn(blackedOutLocalRepoTypeConfig);
        when(blackedOutLocalRepoTypeConfig.isBlackedOut()).thenReturn(true);

        RemoteRepoConfig activeRemoteRepo = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig remoteTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(activeRemoteRepo.getRepoTypeConfig()).thenReturn(remoteTypeConfig);
        when(remoteTypeConfig.isBlackedOut()).thenReturn(false);
        when(remoteTypeConfig.isOffline()).thenReturn(false);

        RemoteRepoConfig offlineRemoteRepo = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig offlineRemoteTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(offlineRemoteRepo.getRepoTypeConfig()).thenReturn(offlineRemoteTypeConfig);
        when(offlineRemoteTypeConfig.isBlackedOut()).thenReturn(false);
        when(offlineRemoteTypeConfig.isOffline()).thenReturn(true);

        FederatedRepoConfig activeFederatedRepo = mock(FederatedRepoConfig.class);
        when(activeFederatedRepo.getKey()).thenReturn("active-federated");

        FederatedRepoConfig inactiveFederatedRepo = mock(FederatedRepoConfig.class);
        when(inactiveFederatedRepo.getKey()).thenReturn("inactive-federated");

        return Stream.of(
                Arguments.of(activeLocalRepo, Set.of(), true),
                Arguments.of(blackedOutLocalRepo, Set.of(), false),
                Arguments.of(activeRemoteRepo, Set.of(), true),
                Arguments.of(offlineRemoteRepo, Set.of(), false),
                Arguments.of(activeFederatedRepo, Set.of("inactive-federated"), true),
                Arguments.of(inactiveFederatedRepo, Set.of("inactive-federated"), false),
                Arguments.of(mock(RepoConfig.class), Set.of(), true)
        );
    }
}
