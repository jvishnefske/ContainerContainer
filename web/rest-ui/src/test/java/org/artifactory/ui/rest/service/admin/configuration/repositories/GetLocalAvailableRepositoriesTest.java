package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.repo.RepoDetailsType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetLocalAvailableRepositoriesTest {

    private RepositoryConfigService repositoryConfigService;
    private AuthorizationService authorizationService;
    private GetLocalAvailableRepositories localAvailableRepositories;

    @BeforeEach
    public void setup() {
        repositoryConfigService = Mockito.mock(RepositoryConfigService.class);
        authorizationService = Mockito.mock(AuthorizationService.class);
        localAvailableRepositories = new GetLocalAvailableRepositories(authorizationService, repositoryConfigService);
    }

    @Test
    public void testGetLocalAvailableRepositories() {
        int limit = 1;
        var repoConfig1 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        var repoConfig2 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        var repoConfig3 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        var repoConfig4 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        when(repoConfig1.getKey()).thenReturn("repo1");
        when(repoConfig2.getKey()).thenReturn("repo2");
        when(repoConfig3.getKey()).thenReturn("repo3");
        when(repoConfig4.getKey()).thenReturn("repo4");

        when(authorizationService.userHasPermissionsOnRepositoryRoot(Mockito.any())).thenReturn(true);
        Mockito.when(repositoryConfigService.getRepoConfigsBySearchPattern(Set.of(RepoDetailsType.LOCAL),
                        null, null))
                .thenReturn(List.of(repoConfig1, repoConfig2, repoConfig3, repoConfig4));

        var result = localAvailableRepositories.getLocalRepos(null, null, limit);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(limit, result.size());
        verify(authorizationService, times(limit)).userHasPermissionsOnRepositoryRoot(Mockito.any());
    }

    @Test
    public void testGetLocalAvailableRepositoriesWithSearch() {
        String search = "repo";
        int limit = 3;
        var repoConfig1 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        var repoConfig2 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        var repoConfig3 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        var repoConfig4 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        when(repoConfig1.getKey()).thenReturn("repo");
        when(repoConfig2.getKey()).thenReturn("repo1");
        when(repoConfig3.getKey()).thenReturn("repo11");
        when(repoConfig4.getKey()).thenReturn("repo111");

        when(authorizationService.userHasPermissionsOnRepositoryRoot(Mockito.any())).thenReturn(true);
        Mockito.when(repositoryConfigService.getRepoConfigsBySearchPattern(Set.of(RepoDetailsType.LOCAL),
                        null, search))
                .thenReturn(List.of(repoConfig1, repoConfig2, repoConfig3, repoConfig4));

        var result = localAvailableRepositories.getLocalRepos(null, search, limit);
        Assertions.assertNotNull(result);
        Assertions.assertFalse(result.isEmpty());
        Assertions.assertEquals(limit, result.size());
        verify(authorizationService, times(limit)).userHasPermissionsOnRepositoryRoot(Mockito.any());
        Assertions.assertEquals("repo", result.get(0));
        Assertions.assertEquals("repo1", result.get(1));
        Assertions.assertEquals("repo11", result.get(2));

    }

    @Test
    public void testGetLocalAvailableRepositoriesWithPermissions() {
        var repoConfig1 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        var repoConfig2 = Mockito.mock(org.artifactory.model.RepoConfig.class);
        when(repoConfig1.getKey()).thenReturn("repo1");
        when(repoConfig2.getKey()).thenReturn("repo2");

        when(authorizationService.userHasPermissionsOnRepositoryRoot("repo1")).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot("repo2")).thenReturn(false);
        Mockito.when(repositoryConfigService.getRepoConfigsBySearchPattern(Set.of(RepoDetailsType.LOCAL),
                        null, null))
                .thenReturn(List.of(repoConfig1, repoConfig2));

        var result = localAvailableRepositories.getLocalRepos(null, null, 100);
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        verify(authorizationService, times(2)).userHasPermissionsOnRepositoryRoot(Mockito.any());
    }
}
