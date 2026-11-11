package org.artifactory.ui.rest.resource.admin.configuration.repositories;

import com.jfrog.build.acl.bridge.BuildFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.model.RepoConfig;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.pipelines.InternalPipelinesService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.repo.ProjectRepoPermissionsHelper;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.ServiceExecutor;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.artifactory.ui.rest.service.admin.configuration.repositories.DeleteRepositoryConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class DeleteRepositoryConfigServiceLongRunTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RepositoryConfigService repositoryConfigService;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private InternalPipelinesService pipelinesService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private ProjectRepoPermissionsHelper projectRepoPermissionsHelper;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        ArtifactoryTracer tracer = mock(ArtifactoryTracer.class);
        when(context.beanForType(ArtifactoryTracer.class)).thenReturn(tracer);
        ArtifactoryContextThreadBinder.bind(context);
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closeable.close();
    }

    @Test
    void whenRequestFitsInTime() {
        RepoConfigResourceForTest resource = createResourceWithDelay(0);
        Response response = resource.deleteRepository("repoKey");
        assertEquals(SC_OK, response.getStatus());
        assertTrue(response.getEntity().toString().contains("Successfully deleted repository"));
    }

    private RepoConfigResourceForTest createResourceWithDelay(
            long sleep) {
        DeleteRepositoryConfigServiceForTest service = new DeleteRepositoryConfigServiceForTest();
        service.sleep = sleep;
        ConfigServiceFactory factory = mock(ConfigServiceFactory.class);
        when(factory.deleteRepositoryConfig()).thenReturn(service);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getContextPath()).thenReturn("");
        when(servletRequest.getRequestURI()).thenReturn("");
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getQueryParameters()).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getPathParameters()).thenReturn(mock(MultivaluedMap.class));
        doReturn(true).when(projectsService).canDoToProjectRepoResource(any(), any(), any());
        var repoConfigMock = mock(RepoConfig.class);
        when(repositoryConfigService.getRepoConfigByKey(any())).thenReturn(repoConfigMock);

        RepoConfigResourceForTest resource = new RepoConfigResourceForTest();
        ReflectionTestUtils.setField(resource, "repositoryConfigService", repositoryConfigService);
        ReflectionTestUtils.setField(resource, "projectsService", projectsService);
        ReflectionTestUtils.setField(resource, "uriInfo", uriInfo);
        ReflectionTestUtils.setField(resource, "projectRepoPermissionsHelper", projectRepoPermissionsHelper);
        resource.setRequest(servletRequest);
        resource.setConfigServiceFactory(factory);
        resource.setArtifactoryResponse(new ArtifactoryRestResponse());
        resource.setExecutor();

        return resource;
    }

    class DeleteRepositoryConfigServiceForTest extends DeleteRepositoryConfigService<RepositoryConfigModel> {
        public long sleep;

        public DeleteRepositoryConfigServiceForTest() {
            super(repositoryService, repositoryConfigService, addonsManager, buildFacade,
                    pipelinesService, securityService);
        }

        @Override
        public void execute(ArtifactoryRestRequest request, RestResponse response) {
            try {
                Thread.sleep(sleep);
                response.info("Successfully deleted repository");
                response.responseCode(SC_OK);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    class RepoConfigResourceForTest extends RepoConfigResource {

        public void setRequest(HttpServletRequest servletRequest) {
            this.servletRequest = servletRequest;
        }

        public void setExecutor() {
            this.serviceExecutor = new ServiceExecutor();
        }

        public void setConfigServiceFactory(ConfigServiceFactory factory) {
            this.configServiceFactory = factory;
        }

        @Override
        protected long getTimeout() {
            return 500L;
        }
    }
}