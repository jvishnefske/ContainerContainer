package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.repo.storage.FolderSummaryInfo;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.info.BaseInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class GetArtifactsCountAndSizeServiceTest {

    private final GetArtifactsCountAndSizeService getArtifactsCountAndSizeService =
            new GetArtifactsCountAndSizeService();
    @Mock
    private SecurityService securityService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryRestRequest request;
    private AutoCloseable autoCloseable;
    private ArtifactoryRestResponse<?> response;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        response = new ArtifactoryRestResponse<>();
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testGetCountAndSizeofTrashCanWithAnonymousAccess() {
        getArtifactsCountAndSizeService.setSecurityService(securityService);
        when(securityService.isAnonymous()).thenReturn(true);
        BaseInfo baseInfo = mock(BaseInfo.class);
        when(baseInfo.getRepositoryPath()).thenReturn("auto-trashcan");
        when(request.getImodel()).thenReturn(baseInfo);
        getArtifactsCountAndSizeService.execute(request, response);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getResponseCode());
    }

    @Test
    void testGetCountAndSizeofTrashCan() {
        getArtifactsCountAndSizeService.setSecurityService(securityService);
        getArtifactsCountAndSizeService.setRepositoryService(repositoryService);
        when(securityService.isAnonymous()).thenReturn(false);
        BaseInfo baseInfo = mock(BaseInfo.class);
        when(baseInfo.getRepositoryPath()).thenReturn("auto-trashcan");
        when(request.getImodel()).thenReturn(baseInfo);
        FolderSummaryInfo folderSummaryInfo = mock(FolderSummaryInfo.class);
        when(repositoryService.getArtifactCountAndSize(any())).thenReturn(folderSummaryInfo);
        getArtifactsCountAndSizeService.execute(request, response);
        assertEquals(HttpStatus.OK.value(), response.getResponseCode());
    }
}