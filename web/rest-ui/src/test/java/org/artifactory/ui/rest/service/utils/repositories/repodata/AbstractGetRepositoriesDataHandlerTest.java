package org.artifactory.ui.rest.service.utils.repositories.repodata;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractGetRepositoriesDataHandlerTest extends RepositoriesDataHandlerTestBase {
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private ProjectsService projectsService;
    private AbstractGetRepositoriesDataHandler<?> handler;
    private AutoCloseable closeable;

    @BeforeEach
    public void before() {
        closeable = MockitoAnnotations.openMocks(this);
        handler = new AbstractGetRepositoriesDataHandler<>() {
            @Override
            protected void handle(ArtifactoryRestRequest<Object> request, RestResponse response, String projectKey) {

            }

            @Override
            public String getHandledQueryParam() {
                return "";
            }
        };
        handler.setAuthorizationService(authorizationService);
        handler.setSecurityService(securityService);
        handler.setRepositoryService(repositoryService);
        handler.setProjectsService(projectsService);
    }

    @AfterEach
    public void after() throws Exception {
        unbindArtifactoryHome();
        closeable.close();
    }

    @Test
    public void testHasReadPermission() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("repoKey");
        when(authorizationService.canRead(any())).thenReturn(true);
        boolean hasPermission = handler.hasReadPermission(repoConfig);
        assertTrue(hasPermission);
    }

    @Test
    public void testHasDeployPermission() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn("repoKey");
        when(repoConfig.isReal()).thenReturn(true);
        when(repoConfig.getPackageType()).thenReturn(RepoType.BuildInfo);
        when(authorizationService.hasBuildPermission(any(), any())).thenReturn(true);

        boolean hasPermission = handler.hasDeployPermission(repoConfig);
        assertTrue(hasPermission);
    }

    @Test
    public void testGetLimit() {
        var request = mock(ArtifactoryRestRequest.class);
        when(request.getQueryParamByKey("limit")).thenReturn("10");
        long limit = handler.getLimit(request);
        assertEquals(10, limit);
        when(request.getQueryParamByKey("limit")).thenReturn("invalid");
        limit = handler.getLimit(request);
        assertEquals(Long.MAX_VALUE, limit);
        when(request.getQueryParamByKey("limit")).thenReturn("");
        limit = handler.getLimit(request);
        assertEquals(Long.MAX_VALUE, limit);
        when(request.getQueryParamByKey("limit")).thenReturn(null);
        limit = handler.getLimit(request);
        assertEquals(Long.MAX_VALUE, limit);
    }

    @Test
    public void testIsAutoComplete() {
        var request = mock(ArtifactoryRestRequest.class);

        // Test with valid boolean true
        when(request.getQueryParamByKey(AbstractGetRepositoriesDataHandler.AUTO_COMPLETION)).thenReturn("true");
        assertTrue(handler.isAutoComplete(request));

        // Test with valid boolean false
        when(request.getQueryParamByKey(AbstractGetRepositoriesDataHandler.AUTO_COMPLETION)).thenReturn("false");
        assertFalse(handler.isAutoComplete(request));

        // Test with invalid boolean
        when(request.getQueryParamByKey(AbstractGetRepositoriesDataHandler.AUTO_COMPLETION)).thenReturn("invalid");
        assertFalse(handler.isAutoComplete(request));

        // Test with blank string
        when(request.getQueryParamByKey(AbstractGetRepositoriesDataHandler.AUTO_COMPLETION)).thenReturn("");
        assertFalse(handler.isAutoComplete(request));

        // Test with null
        when(request.getQueryParamByKey(AbstractGetRepositoriesDataHandler.AUTO_COMPLETION)).thenReturn(null);
        assertFalse(handler.isAutoComplete(request));
    }
}