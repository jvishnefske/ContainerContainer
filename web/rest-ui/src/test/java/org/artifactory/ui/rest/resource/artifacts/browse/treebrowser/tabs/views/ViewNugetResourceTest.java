package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.views;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.nugetinfo.NugetArtifactInfo;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.ConfigurableEnvironment;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Volodymyr Shulga
 */
class ViewNugetResourceTest {
    @Mock
    private ConfigurableEnvironment env;
    @Mock
    private ViewsResource viewsResource;
    @Mock
    private PackageResourceService packageResourceService;
    @Mock
    private BrowseServiceFactory browseServiceFactory;
    @Mock
    private NugetArtifactInfo artifactInfo;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private HttpHeaders httpHeaders;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private HttpServletResponse servletResponse;
    @Mock
    private Request request;

    private ViewNugetResource resourceSelector;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void cleanup() throws Exception {
        autoCloseable.close();
    }

    @Test
    void expectLegacyNugetView() throws PackageException {
        when(env.getActiveProfiles()).thenReturn(new String[] {});
        resourceSelector = new ViewNugetResource(browseServiceFactory, viewsResource, env, packageResourceService);
        resourceSelector.viewNuget(artifactInfo, servletRequest, servletResponse, request, uriInfo, httpHeaders);
        Mockito.verify(viewsResource).runService(any(), any(NugetArtifactInfo.class),
                any(HttpServletRequest.class), any(HttpServletResponse.class), any(Request.class), any(UriInfo.class),
                any(HttpHeaders.class));
    }

    @Test
    void expectLegacyNugetViewForOtherProfiles() throws PackageException {
        when(env.getActiveProfiles()).thenReturn(new String[] {"notNugetNew"});
        resourceSelector = new ViewNugetResource(browseServiceFactory, viewsResource, env, packageResourceService);
        resourceSelector.viewNuget(artifactInfo, servletRequest, servletResponse, request, uriInfo, httpHeaders);
        Mockito.verify(viewsResource).runService(any(), any(NugetArtifactInfo.class),
                any(HttpServletRequest.class), any(HttpServletResponse.class), any(Request.class), any(UriInfo.class),
                any(HttpHeaders.class));
    }

    @Test
    void expectNewNugetView() throws PackageException {
        when(env.getActiveProfiles()).thenReturn(new String[] {"nugetNew"});
        resourceSelector = new ViewNugetResource(browseServiceFactory, viewsResource, env, packageResourceService);
        resourceSelector.viewNuget(artifactInfo, servletRequest, servletResponse, request, uriInfo, httpHeaders);
        Mockito.verify(packageResourceService).getPackageInfo(any(), any());
    }
}