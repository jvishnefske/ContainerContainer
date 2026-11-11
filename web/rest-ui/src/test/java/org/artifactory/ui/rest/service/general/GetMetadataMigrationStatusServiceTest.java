package org.artifactory.ui.rest.service.general;

import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.general.MetadataMigrationStatusModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class GetMetadataMigrationStatusServiceTest {

    private AutoCloseable mocks;
    @Mock
    private MetadataEventService metadataEventService;
    @InjectMocks
    private GetMetadataMigrationStatusService getMetadataMigrationStatusService;

    @BeforeEach
    public void beforeEach() {
        mocks = openMocks(this);
    }

    @AfterEach
    public void afterEach() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExecute(boolean metadataMigrationInProgress) {
        doReturn(metadataMigrationInProgress).when(metadataEventService).isMigrating();
        ArtifactoryRestRequest<?> artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse<MetadataMigrationStatusModel> artifactoryRestResponse = new ArtifactoryRestResponse<>();
        getMetadataMigrationStatusService.execute(artifactoryRestRequest, artifactoryRestResponse);
        MetadataMigrationStatusModel metadataMigrationStatusModel =
                (MetadataMigrationStatusModel) artifactoryRestResponse.getEntity();

        assertEquals(200, artifactoryRestResponse.getResponseCode());
        assertEquals(metadataMigrationInProgress, metadataMigrationStatusModel.isMetadataMigrationInProgress());
    }
}
