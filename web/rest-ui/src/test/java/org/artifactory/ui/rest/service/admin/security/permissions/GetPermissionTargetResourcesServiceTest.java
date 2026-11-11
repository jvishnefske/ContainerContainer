package org.artifactory.ui.rest.service.admin.security.permissions;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class GetPermissionTargetResourcesServiceTest {

    @Mock
    private RepositoryService repositoryService;
    @InjectMocks
    private GetPermissionTargetResourcesService getPermissionTargetResourcesService;

    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
    }

    @Test
    void getAllRepoKeysAllEmpty() {
        when(repositoryService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of());
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of());

        List<RepoKeyType> allRepoKeys = getPermissionTargetResourcesService.getAllRepoKeys();

        Assertions.assertNotNull(allRepoKeys);
        Assertions.assertTrue(allRepoKeys.isEmpty());
    }

    @Test
    void getAllRepoKeysLocalOnly() {
        LocalRepoConfig localConfig1 = LocalRepoConfig.create("repoKey1", RepoType.Generic);
        LocalRepoConfig localConfig2 = LocalRepoConfig.create("repoKey2", RepoType.Generic);

        when(repositoryService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of(localConfig1, localConfig2));
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of());

        List<RepoKeyType> allRepoKeys = getPermissionTargetResourcesService.getAllRepoKeys();

        Assertions.assertNotNull(allRepoKeys);
        Assertions.assertEquals(2, allRepoKeys.size());

        Assertions.assertEquals("repoKey1", allRepoKeys.get(0).getRepoKey());
        Assertions.assertEquals(RepoType.Generic, allRepoKeys.get(0).getRepoType());
        Assertions.assertEquals("repoKey2", allRepoKeys.get(1).getRepoKey());
        Assertions.assertEquals(RepoType.Generic, allRepoKeys.get(1).getRepoType());
    }

    @Test
    void getAllRepoKeysRemoteOnly() {
        RemoteRepoConfig remoteRepoConfig1 = RemoteRepoConfig.create("repoKey1", RepoType.Generic);
        RemoteRepoConfig remoteRepoConfig2 = RemoteRepoConfig.create("repoKey2", RepoType.Generic);

        when(repositoryService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of());
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(remoteRepoConfig1, remoteRepoConfig2));

        List<RepoKeyType> allRepoKeys = getPermissionTargetResourcesService.getAllRepoKeys();

        Assertions.assertNotNull(allRepoKeys);
        Assertions.assertEquals(2, allRepoKeys.size());

        Assertions.assertEquals("repoKey1", allRepoKeys.get(0).getRepoKey());
        Assertions.assertEquals(RepoType.Generic, allRepoKeys.get(0).getRepoType());
        Assertions.assertEquals("repoKey2", allRepoKeys.get(1).getRepoKey());
        Assertions.assertEquals(RepoType.Generic, allRepoKeys.get(1).getRepoType());
    }

    @Test
    void getAllRepoKeysLocalAndRemote() {
        LocalRepoConfig localConfig1 = LocalRepoConfig.create("repoKey1", RepoType.Generic);
        LocalRepoConfig localConfig2 = LocalRepoConfig.create("repoKey2", RepoType.Generic);

        RemoteRepoConfig remoteRepoConfig1 = RemoteRepoConfig.create("repoKey1", RepoType.Generic);
        RemoteRepoConfig remoteRepoConfig2 = RemoteRepoConfig.create("repoKey2", RepoType.Generic);

        when(repositoryService.getLocalRepoConfigsExcludingInfoRepos()).thenReturn(List.of(localConfig1, localConfig2));
        when(repositoryService.getRemoteRepoConfigs()).thenReturn(List.of(remoteRepoConfig1, remoteRepoConfig2));

        List<RepoKeyType> allRepoKeys = getPermissionTargetResourcesService.getAllRepoKeys();
        Assertions.assertNotNull(allRepoKeys);
        Assertions.assertEquals(4, allRepoKeys.size());
    }
}