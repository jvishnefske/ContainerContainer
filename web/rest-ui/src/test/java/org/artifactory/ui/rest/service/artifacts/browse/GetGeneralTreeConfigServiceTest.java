package org.artifactory.ui.rest.service.artifacts.browse;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.trashcan.TrashcanConfigDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.GeneralConfigModel;
import org.artifactory.ui.rest.model.general.MetadataMigrationStatusModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.treebrowserFolderCompact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class GetGeneralTreeConfigServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private AutoCloseable mocks;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private MutableCentralConfigDescriptor mutableCentralConfigDescriptor;
    @Mock
    private TrashcanConfigDescriptor trashcanConfigDescriptor;
    @InjectMocks
    private GetGeneralTreeConfigService getGeneralTreeConfigService;

    @BeforeEach
    public void beforeEach() {
        mocks = openMocks(this);
        doReturn(mutableCentralConfigDescriptor).when(centralConfigService).getDescriptor();
        doReturn(trashcanConfigDescriptor).when(mutableCentralConfigDescriptor).getTrashcanConfig();
    }

    @AfterEach
    public void afterEach() throws Exception {
        mocks.close();
    }

    @ParameterizedTest
    @MethodSource("testExecuteArguments")
    void testExecute(boolean isTrashEnabled,
            boolean isTreebrowserFolderCompact, boolean expectedIsTrashEnabled,
            boolean expectedIsTreebrowserFolderCompact) {
        doReturn(isTrashEnabled).when(trashcanConfigDescriptor).isEnabled();
        getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .setProperty(treebrowserFolderCompact.getPropertyName(), String.valueOf(isTreebrowserFolderCompact));

        ArtifactoryRestRequest<?> artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse<MetadataMigrationStatusModel> artifactoryRestResponse = new ArtifactoryRestResponse<>();
        getGeneralTreeConfigService.execute(artifactoryRestRequest, artifactoryRestResponse);
        GeneralConfigModel generalConfigModel = (GeneralConfigModel) artifactoryRestResponse.getEntity();

        assertEquals(200, artifactoryRestResponse.getResponseCode());
        assertEquals(expectedIsTrashEnabled, generalConfigModel.isTrashEnabled());
        assertEquals(expectedIsTreebrowserFolderCompact, generalConfigModel.isTreebrowserFolderCompact());
    }

    private static Stream<Arguments> testExecuteArguments() {
        return Stream.of(
                Arguments.of(true, true, true, true),
                Arguments.of(true, false, true, false),
                Arguments.of(true, true, true, true),
                Arguments.of(true, false, true, false),
                Arguments.of(true, true, true, true),
                Arguments.of(true, false, true, false),
                Arguments.of(true, true, true, true),
                Arguments.of(true, false, true, false),
                Arguments.of(false, true, false, true),
                Arguments.of(false, false, false, false),
                Arguments.of(false, true, false, true),
                Arguments.of(false, false, false, false),
                Arguments.of(false, true, false, true),
                Arguments.of(false, false, false, false),
                Arguments.of(false, true, false, true),
                Arguments.of(false, false, false, false)
        );
    }
}
