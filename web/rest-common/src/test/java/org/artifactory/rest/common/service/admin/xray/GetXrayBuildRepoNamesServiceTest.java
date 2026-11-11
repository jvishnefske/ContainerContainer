package org.artifactory.rest.common.service.admin.xray;

import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Mor Merhav
 */
public class GetXrayBuildRepoNamesServiceTest {

    @Mock
    private BuildFacade buildFacadeMock;
    @Mock
    private ArtifactoryRestRequest<String> requestMock;
    @Mock
    private RestResponse responseMock;

    private GetXrayBuildRepoNamesService getXrayBuildRepoNamesService;
    private MockitoSession mockitoSession;

    @BeforeMethod
    public void setUp() {
        mockitoSession = Mockito.mockitoSession()
        .strictness(Strictness.STRICT_STUBS)
        .initMocks(this)
        .startMocking();
        getXrayBuildRepoNamesService = new GetXrayBuildRepoNamesService(buildFacadeMock);
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void getAllXrayBuildRepoNames() {
        when(requestMock.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn(null);

        getXrayBuildRepoNamesService.execute(requestMock, responseMock);

        verify(buildFacadeMock, times(1)).getAllBuildInfoRepos();
        verifyNoMoreInteractions(buildFacadeMock);
    }

    @Test
    public void getBuildRepoNamesByProjectKey() {
        when(requestMock.getQueryParamByKey(ProjectsService.PROJECT_KEY)).thenReturn("projectKey");

        getXrayBuildRepoNamesService.execute(requestMock, responseMock);

        verify(buildFacadeMock, times(1)).getBuildInfoRepoKeyByProject("projectKey", true);
        verifyNoMoreInteractions(buildFacadeMock);
    }
}