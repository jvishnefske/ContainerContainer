package org.artifactory.repo.cleanup;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.testng.annotations.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Alexei Vainshtein
 */
public class DockerUploadsFolderCleanupServiceImplTest {

    @Test
    public void testDoCleanup() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        when(repositoryService.getLocalAndFederatedRepoConfigsExcludingInfoRepos()).thenReturn(
                List.of(LocalRepoConfig.create("key", RepoType.Docker),
                        FederatedRepoConfig.create("fed", RepoType.Docker),
                        FederatedRepoConfig.create("fed1", RepoType.Generic)));
        AddonsManager addonsManager = mock(AddonsManager.class);
        DockerAddon dockerAddon = mock(DockerAddon.class);
        when(addonsManager.addonByType(DockerAddon.class)).thenReturn(dockerAddon);
        DockerUploadsFolderCleanupServiceImpl dockerUploadsFolderCleanupService =
                new DockerUploadsFolderCleanupServiceImpl(repositoryService, addonsManager, null);
        dockerUploadsFolderCleanupService.doCleanup();
        verify(dockerAddon, times(1)).searchAndCleanupTempFolders("key");
        verify(dockerAddon, times(1)).searchAndCleanupTempFolders("fed");
        verify(dockerAddon, times(0)).searchAndCleanupTempFolders("fed1");
    }
}