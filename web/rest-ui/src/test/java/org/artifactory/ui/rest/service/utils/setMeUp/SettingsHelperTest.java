package org.artifactory.ui.rest.service.utils.setMeUp;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.security.ArtifactoryPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
class SettingsHelperTest {

    @Mock
    private InternalRepositoryService repositoryService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private RepositoryConfigService repositoryConfigService;

    @Mock
    private VirtualRepo virtualRepo;

    @Mock
    private VirtualRepoDescriptor virtualRepoDescriptor;

    @Mock
    private RepoConfig repoConfig;

    private AutoCloseable closeable;

    @BeforeEach
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    protected void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void getReadableVirtualRepoDescriptorsWithReadableRepos() {
        when(repositoryService.getVirtualRepositories()).thenReturn(List.of(virtualRepo));
        when(virtualRepo.getDescriptor()).thenReturn(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getType()).thenReturn(RepoType.Maven);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(virtualRepo.getKey(),
                Set.of(ArtifactoryPermission.READ), new HashMap<>())).thenReturn(true);

        List<VirtualRepoDescriptor> result =
                SettingsHelper.getReadableVirtualRepoDescriptors(repositoryService, authorizationService);

        assertEquals(1, result.size());
        assertEquals(virtualRepoDescriptor, result.getFirst());
    }

    @Test
    void getReadableVirtualRepoDescriptorsWithNoReadableRepos() {
        when(repositoryService.getVirtualRepositories()).thenReturn(List.of(virtualRepo));
        when(virtualRepo.getDescriptor()).thenReturn(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getType()).thenReturn(RepoType.Maven);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(virtualRepo.getKey(),
                Set.of(ArtifactoryPermission.READ), new HashMap<>())).thenReturn(false);

        List<VirtualRepoDescriptor> result =
                SettingsHelper.getReadableVirtualRepoDescriptors(repositoryService, authorizationService);

        assertTrue(result.isEmpty());
    }

    @Test
    void getReadableRepoConfigsWithReadableRepos() {
        when(repositoryConfigService.getRepoConfigsByTypes(
                Set.of(RepoDetailsType.LOCAL, RepoDetailsType.FEDERATED, RepoDetailsType.VIRTUAL))).thenReturn(
                List.of(repoConfig));
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoConfig.getKey(),
                Set.of(ArtifactoryPermission.READ), new HashMap<>())).thenReturn(true);

        List<RepoConfig> result = SettingsHelper.getReadableRepoConfigs(
                Set.of(RepoDetailsType.LOCAL, RepoDetailsType.FEDERATED, RepoDetailsType.VIRTUAL),
                repositoryConfigService, authorizationService);

        assertEquals(1, result.size());
        assertEquals(repoConfig, result.getFirst());
    }

    @Test
    void getReadableRepoConfigsWithNoReadableRepos() {
        when(repositoryConfigService.getRepoConfigsByTypes(
                Set.of(RepoDetailsType.LOCAL, RepoDetailsType.FEDERATED, RepoDetailsType.VIRTUAL))).thenReturn(
                List.of(repoConfig));
        when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(repoConfig.getKey(),
                Set.of(ArtifactoryPermission.READ), new HashMap<>())).thenReturn(false);

        List<RepoConfig> result = SettingsHelper.getReadableRepoConfigs(
                Set.of(RepoDetailsType.LOCAL, RepoDetailsType.FEDERATED, RepoDetailsType.VIRTUAL),
                repositoryConfigService, authorizationService);

        assertTrue(result.isEmpty());
    }
}