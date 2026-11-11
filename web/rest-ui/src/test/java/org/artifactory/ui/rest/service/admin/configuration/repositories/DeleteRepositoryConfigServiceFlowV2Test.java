package org.artifactory.ui.rest.service.admin.configuration.repositories;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * @author Mor Merhav
 */
@Slf4j
class DeleteRepositoryConfigServiceFlowV2Test extends DeleteRepositoryConfigServiceTest {

    @BeforeEach
    @Override
    void setUp() {
        super.setUp();
        enableDeleteRepoFlowV2();
    }

    @Test
    void testDeleteRepoFlowV2() {
        String repoKey = "repoKey";
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.status("Repo '%s' deleted successfully".formatted(repoKey), log);
        when(repositoryService.removeRepository(repoKey)).thenReturn(statusHolder);

        ArtifactoryRestRequest<?> request = mockRequest(repoKey);
        RestResponse<?> response = mock(RestResponse.class, RETURNS_DEEP_STUBS);

        deleteRepositoryConfigService.execute(request, response);

        verify(response.info(statusHolder.getStatusMsg())).responseCode(anyInt());
    }

    @Test
    void testDeleteRepoFlowV2_Error() {
        String repoKey = "repoKey";
        String errMsg = "Error added by test";
        int responseCode = SC_FORBIDDEN;

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.error(errMsg, responseCode, log);
        when(repositoryService.removeRepository(repoKey)).thenReturn(statusHolder);

        ArtifactoryRestRequest<?> request = mockRequest(repoKey);
        RestResponse<?> response = mock(RestResponse.class, RETURNS_DEEP_STUBS);

        deleteRepositoryConfigService.execute(request, response);

        verify(response.error(statusHolder.getStatusMsg())).responseCode(responseCode);
    }

    @Test
    void testDeleteRepoFlowV2_Warning() {
        String repoKey = "repoKey";
        String warnMsg = "Warning added by test";
        int responseCode = SC_FORBIDDEN;

        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.warn(warnMsg, responseCode, log);
        when(repositoryService.removeRepository(repoKey)).thenReturn(statusHolder);

        ArtifactoryRestRequest<?> request = mockRequest(repoKey);
        RestResponse<?> response = mock(RestResponse.class, RETURNS_DEEP_STUBS);

        deleteRepositoryConfigService.execute(request, response);

        verify(response.error(statusHolder.getStatusMsg())).responseCode(responseCode);
    }

    @Test
    void testDeleteRepoFlowV2_Exception() {
        String repoKey = "repoKey";
        String errMsg = "Thrown by test";

        when(repositoryService.removeRepository(repoKey)).thenThrow(new RuntimeException(errMsg));

        ArtifactoryRestRequest<?> request = mockRequest(repoKey);
        RestResponse<?> response = mock(RestResponse.class);

        deleteRepositoryConfigService.execute(request, response);

        verify(response).error("Deleting repo '%s' failed: %s".formatted(repoKey, errMsg));
    }

    @Override
    void testExecuteDeletePipeInfoRepo() {
        // Not needed at this service level on V2
    }

    @Override
    void testDeleteSystemRepo(String repoKey) {
        // Not needed at this service level on V2
    }

    @Override
    public void testDeleteLastLocalRepo() {
        // Eliminated this validation on V2
    }

    @Override
    void testDeleteLastLocalRepoOnAol() {
        // Eliminated this validation on V2
    }

    private static ArtifactoryRestRequest<?> mockRequest(String repoKey) {
        ArtifactoryRestRequest<?> request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn(repoKey);
        return request;
    }

    private void enableDeleteRepoFlowV2() {
        getBound().setProperty(ConstantValues.removeRepositoryFlowV2Enabled.getPropertyName(),
                Boolean.TRUE.toString());
    }
}