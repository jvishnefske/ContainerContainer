package org.artifactory.ui.rest.service.artifacts.deploy;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.artifact.UnitInfo;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.DeployService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.sapi.common.RepositoryRuntimeException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.artifacts.deploy.UploadArtifactInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class ArtifactDeployServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Spy
    private ArtifactDeployService artifactDeployService;
    @Mock
    private DeployService deployService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AddonsManager addonsManager;
    private AutoCloseable closable;
    private ArtifactoryHome artifactoryHome;

    @BeforeEach
    void before() {
        this.closable = MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(artifactDeployService, "deployService", deployService);
        ReflectionTestUtils.setField(artifactDeployService, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(artifactDeployService, "addonsManager", addonsManager);
        artifactoryHome = getBound();
        ArtifactoryHome.bind(artifactoryHome);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void after() throws Exception {
        this.closable.close();
        ArtifactoryContextThreadBinder.unbind();
        ArtifactoryHome.unbind();
    }

    @Test
    void testExcpetionThrownWhenNoDeployableRepo() throws RepoRejectException {
        ArtifactoryRestRequest artifactoryRestRequest = mock(ArtifactoryRestRequest.class);
        UploadArtifactInfo uploadArtifactInfo = mock(UploadArtifactInfo.class);
        when((UploadArtifactInfo) artifactoryRestRequest.getImodel()).thenReturn(uploadArtifactInfo);
        when(uploadArtifactInfo.getHandlingNode()).thenReturn("testNode");
        ArtifactoryRestResponse artifactoryResponse = new ArtifactoryRestResponse();
        HaCommonAddon haCommonAddon = mock(HaCommonAddon.class);
        when(addonsManager.addonByType(HaCommonAddon.class)).thenReturn(haCommonAddon);
        when(haCommonAddon.isHaEnabled()).thenReturn(false);
        when(artifactoryContext.getArtifactoryHome()).thenReturn(artifactoryHome);
        when(uploadArtifactInfo.getFileName()).thenReturn("file");
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(repositoryService.localFederatedOrCachedRepoDescriptorByKey(any())).thenReturn(localRepoDescriptor);
        UnitInfo unitInfo = mock(UnitInfo.class);
        when(uploadArtifactInfo.getUnitInfo()).thenReturn(unitInfo);
        when(unitInfo.getPath()).thenReturn("path:path");
        doThrow(new RepositoryRuntimeException("Error")).when(deployService).deploy(any(), any(), any(), any());
        artifactDeployService.execute(artifactoryRestRequest, artifactoryResponse);
        assertTrue(artifactoryResponse.getFeedbackMsg().getError().contains("Failed to deploy file"));
        assertEquals(artifactoryResponse.buildResponse().getStatus(), 400, "Response code should be 400 Bad Request");
    }
}
