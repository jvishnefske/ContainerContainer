package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetDependencyDeclarationServiceTest {

    private static final String REPO_KEY_PARAM = "repoKey";
    private static final String PATH_PARAM = "path";
    private static final String REPO_KEY = "REPO";
    private static final String PATH = "path";

    @Mock
    private ArtifactoryRestRequest request;
    @Mock
    private RestResponse response;
    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private GetDependencyDeclarationService getDependencyDeclarationService;

    @Test
    void testExecuteWhenRepoDoesNotExist() {
        when(request.getQueryParamByKey(REPO_KEY_PARAM)).thenReturn(REPO_KEY);
        when(request.getQueryParamByKey(PATH_PARAM)).thenReturn(PATH);
        when(repositoryService.isVirtualRepoExist(REPO_KEY)).thenReturn(false);

        final ItemNotFoundRuntimeException itemNotFoundRuntimeException =
                assertThrows(ItemNotFoundRuntimeException.class, () ->
                        getDependencyDeclarationService.execute(request, response));
        assertEquals("Repo " + REPO_KEY + " does not exist",
                     itemNotFoundRuntimeException.getMessage());
    }
}