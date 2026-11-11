package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.ph.PackageSpecificationImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.AvailableRepositories;
import org.artifactory.util.DebianPackageTypeConfigMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Set;

import static org.artifactory.util.PackageTypeConfigFields.HUGGINGFACE_ML_REPO_LAYOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author BarakH
 */
public class GetAvailableRepositoriesTest {

    private enum UserType {SIMPLE, PROJECT_ADMIN, ADMIN}

    private static final String TEST_PROJECT = "stam";

    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private SecurityService securityService;

    @InjectMocks
    private GetAvailableRepositories getAvailableRepositories;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        PackageSpecificationImpl packageSpecification = new PackageSpecificationImpl(null);
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(LocalRepoConfig.create("A", RepoType.Generic)));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED))
                .thenReturn(List.of(FederatedRepoConfig.create("B", RepoType.Generic)));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL))
                .thenReturn(List.of(FederatedRepoConfig.create("C", RepoType.Generic)));
        getAvailableRepositories.setPackageSpecification(packageSpecification);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("provideUserType")
    void testAvailableRepos(UserType userType, String projectName, boolean shouldBeExist) {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getQueryParamByKey("type")).thenReturn(RepoType.Generic.getDisplayName());
        when(restRequest.getQueryParamByKey("projectKey")).thenReturn(projectName);
        switch (userType) {
            case SIMPLE:
                when(securityService.isAdmin()).thenReturn(false);
                when(projectsService.getProjectRepositoriesKeys(eq(TEST_PROJECT), anyBoolean())).thenReturn(Set.of());
                break;
            case PROJECT_ADMIN:
                when(securityService.isAdmin()).thenReturn(false);
                when(projectsService.getProjectRepositoriesKeys(eq(TEST_PROJECT), anyBoolean()))
                        .thenReturn(Set.of("A", "B", "C"));
                break;
            case ADMIN:
                when(securityService.isAdmin()).thenReturn(true);
                when(projectsService.getProjectRepositoriesKeys(eq(TEST_PROJECT), anyBoolean()))
                        .thenReturn(Set.of("A", "B", "C"));
                break;
        }
        getAvailableRepositories.execute(restRequest, response);
        AvailableRepositories availableRepositories = (AvailableRepositories) response.getIModel();
        if (shouldBeExist) {
            assertTrue(availableRepositories.getAvailableLocalRepos().contains("A"));
            assertTrue(availableRepositories.getAvailableVirtualRepos().contains("C"));
            assertTrue(availableRepositories.getAvailableFederatedRepos().contains("B"));
        } else {
            assertFalse(availableRepositories.getAvailableLocalRepos().contains("A"));
            assertFalse(availableRepositories.getAvailableVirtualRepos().contains("C"));
            assertFalse(availableRepositories.getAvailableFederatedRepos().contains("B"));
        }
    }

    @Test
    void testGetAllAvailableReposDebian() {
        LocalRepoConfig debianRepo = LocalRepoConfig.create("A", RepoType.Debian);
        debianRepo.getPackageTypeConfig().put(DebianPackageTypeConfigMapper.DEBIAN_TRIVIAL_LAYOUT, "false");
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of(debianRepo));
        when(securityService.isAdmin()).thenReturn(true);
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getQueryParamByKey("projectKey")).thenReturn(ProjectsService.DEFAULT_PROJECT);
        when(restRequest.getQueryParamByKey("type")).thenReturn(RepoType.Debian.getDisplayName());

        getAvailableRepositories.execute(restRequest, response);

        Object iModel = response.getIModel();
        assertNotNull(iModel);
        assertTrue(iModel instanceof AvailableRepositories);
        AvailableRepositories availableRepositories = (AvailableRepositories) iModel;
        List<String> localRepos = availableRepositories.getAvailableLocalRepos();
        assertNotNull(localRepos);
        assertEquals(1, localRepos.size());
        assertEquals("A", localRepos.get(0));
    }

    @ParameterizedTest
    @EnumSource(value = RepoType.class, names = {"OCI", "HelmOCI"})
    void testGetAllAvailableReposOci(RepoType virtualRepoType) {
        LocalRepoConfig helmOciRepo = LocalRepoConfig.create("A", RepoType.HelmOCI);
        LocalRepoConfig ociRepo = LocalRepoConfig.create("B", RepoType.OCI);

        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of(helmOciRepo,ociRepo));
        when(securityService.isAdmin()).thenReturn(true);
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getQueryParamByKey("type")).thenReturn(virtualRepoType.getDisplayName());
        getAvailableRepositories.execute(restRequest, response);

        Object iModel = response.getIModel();
        assertNotNull(iModel);
        assertTrue(iModel instanceof AvailableRepositories);
        AvailableRepositories availableRepositories = (AvailableRepositories) iModel;
        List<String> localRepos = availableRepositories.getAvailableLocalRepos();
        assertNotNull(localRepos);
        assertEquals("A", localRepos.get(0));
        if (virtualRepoType.equals(RepoType.OCI)) {
            assertEquals(2, localRepos.size());
            assertEquals("B", localRepos.get(1));
        } else {
            assertEquals(1, localRepos.size());
        }
    }

    @Test
    void testAvailableReposWithMixedMlLayout() {
        final String LOCAL_OLD = "local-old";
        final String LOCAL_NEW = "local-new";
        final String FEDERATED_ML = "federated-ml";
        final String VIRTUAL_ML = "virtual-ml";
        final String REMOTE_OLD = "remote-ml";
        LocalRepoConfig localOldLayout = LocalRepoConfig.create(LOCAL_OLD, RepoType.HuggingFaceML);
        localOldLayout.getPackageTypeConfig().put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), "false");
        LocalRepoConfig localNewLayout = LocalRepoConfig.create(LOCAL_NEW, RepoType.HuggingFaceML);
        localNewLayout.getPackageTypeConfig().put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), "true");
        FederatedRepoConfig fedratedRepo = FederatedRepoConfig.create(FEDERATED_ML, RepoType.HuggingFaceML);
        fedratedRepo.getPackageTypeConfig().put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), "true");
        FederatedRepoConfig virtualRepo = FederatedRepoConfig.create(VIRTUAL_ML, RepoType.HuggingFaceML);
        virtualRepo.getPackageTypeConfig().put(HUGGINGFACE_ML_REPO_LAYOUT.getKey(), "true");
        RemoteRepoConfig remoteRepo = RemoteRepoConfig.create(REMOTE_OLD, RepoType.HuggingFaceML);
        when(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos())
                .thenReturn(List.of(localOldLayout, localNewLayout));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED))
                .thenReturn(List.of(fedratedRepo));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL))
                .thenReturn(List.of(virtualRepo));
        when(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE)).thenReturn(List.of(remoteRepo));
        when(securityService.isAdmin()).thenReturn(true);
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();
        when(restRequest.getQueryParamByKey("type")).thenReturn(RepoType.HuggingFaceML.getDisplayName());
        getAvailableRepositories.execute(restRequest, response);
        AvailableRepositories availableRepositories = (AvailableRepositories) response.getIModel();
        assertNotNull(availableRepositories);
        assertTrue(availableRepositories.getAvailableLocalRepos().contains(LOCAL_NEW));
        assertFalse(availableRepositories.getAvailableLocalRepos().contains(LOCAL_OLD));
        assertFalse(availableRepositories.getAvailableRemoteRepos().contains(REMOTE_OLD));
        assertTrue(availableRepositories.getAvailableFederatedRepos().contains(FEDERATED_ML));
        assertTrue(availableRepositories.getAvailableVirtualRepos().contains(VIRTUAL_ML));
    }

    public static Object[][] provideUserType() {
        return new Object[][] {
                {UserType.SIMPLE, "", false},
                {UserType.SIMPLE, TEST_PROJECT, false},
                {UserType.PROJECT_ADMIN, "", false},
                {UserType.PROJECT_ADMIN, TEST_PROJECT, true},
                {UserType.ADMIN, "", true},
                {UserType.ADMIN, TEST_PROJECT, true},
        };
    }
}