package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.permission;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Yevdo Abramov
 * Created on 27/10/2020
 */
@Test
public class GetRepoEffectivePermissionsByEntityServiceTest {
    @Mock
    private AuthorizationService authService;
    @Mock
    private SecurityService securityService;
    @Mock
    private UserGroupService userGroupService;

    private GetRepoEffectivePermissionsByEntityService service;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new GetRepoEffectivePermissionsByEntityService(userGroupService, authService, securityService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testExecuteWhenNoPermissions() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        when(request.getQueryParamByKey("repoKey")).thenReturn("my_repo");
        when(request.getQueryParamByKey("path")).thenReturn("my_path");
        when(authService.canManage(any(RepoPath.class))).thenReturn(false);
        when(response.responseCode(HttpServletResponse.SC_FORBIDDEN)).thenReturn(mock(ArtifactoryRestResponse.class));

        service.execute(request, response);

        verify(request).getQueryParamByKey("repoKey");
        verify(request).getQueryParamByKey("path");
        verify(authService).canManage(any(RepoPath.class));
        verify(response).responseCode(HttpServletResponse.SC_FORBIDDEN);
        verifyNoMoreInteractions(userGroupService);
    }

    @Test
    public void testExecuteIsAcceptingManagePermissions() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        RestResponse response = mock(RestResponse.class);

        when(request.getQueryParamByKey("repoKey")).thenReturn("my_repo");
        when(request.getQueryParamByKey("path")).thenReturn("my_path");
        when(authService.canManage(any(RepoPath.class))).thenReturn(true);

        service.execute(request, response);

        verify(request).getQueryParamByKey("repoKey");
        verify(request).getQueryParamByKey("path");
        verify(authService).canManage(any(RepoPath.class));
        verify(authService).getRepoPathAcls(any());
    }
}