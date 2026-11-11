package org.artifactory.ui.rest.resource.admin.configuration.repositories;

import org.artifactory.api.repo.RepoActionType;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.junit.jupiter.api.Test;

import static org.artifactory.common.ConstantValues.removeRepositoryFlowV2Enabled;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
class RepoConfigResourceDeleteRepoFlowV2Test extends RepoConfigResourceTest {

    @Test
    void testDeleteRepository_deleteRepoFlowV2_NoProjectsServiceCall() {
        enableDeleteRepoFlowV2();
        repoConfigResource.deleteRepository("repoKey");
        verifyNoInteractions(projectsService);
    }

    @Test
    void testDeleteRepository_deleteRepoFlowV1_ProjectsServiceCall() {
        disableDeleteRepoFlowV2();
        String repoKey = "repoKey";
        var repoConfigMock = mock(RepoConfig.class);
        when(repositoryConfigService.getRepoConfigByKey(any())).thenReturn(repoConfigMock);
        assertThrows(AuthorizationRestException.class, () -> repoConfigResource.deleteRepository(repoKey));
        verify(projectsService).canDoToProjectRepoResource(eq(RepoActionType.DELETE), eq(repoKey), any());
    }

    private void disableDeleteRepoFlowV2() {
        getBound().setProperty(removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.FALSE.toString());
    }

    private void enableDeleteRepoFlowV2() {
        getBound().setProperty(removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.TRUE.toString());
    }
}