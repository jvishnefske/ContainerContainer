package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.views;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;
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
 * @author Natalie Berni
 */
class ViewSwiftResourceTest {

    @Mock
    private ConfigurableEnvironment env;
    @Mock
    private ViewsResource viewsResource;
    @Mock
    private PackageResourceService packageResourceService;
    @Mock
    private BrowseServiceFactory browseServiceFactory;
    @Mock
    private BaseArtifactInfo artifactInfo;
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

    private ViewSwiftResource resourceSelector;

    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void viewSwift() {
    }

    @Test
    void expectLegacySwiftView() throws PackageException {
        when(env.getActiveProfiles()).thenReturn(new String[] {});
        resourceSelector = new ViewSwiftResource(browseServiceFactory, viewsResource, env, packageResourceService);
        resourceSelector.viewSwift(artifactInfo, servletRequest, servletResponse, request, uriInfo, httpHeaders);
        Mockito.verify(viewsResource).runService(any(), any(BaseArtifactInfo.class),
                any(HttpServletRequest.class), any(HttpServletResponse.class), any(Request.class), any(UriInfo.class),
                any(HttpHeaders.class));
        Mockito.verifyNoInteractions(packageResourceService);
    }

    @Test
    void expectLegacySwiftViewForOtherProfiles() throws PackageException {
        when(env.getActiveProfiles()).thenReturn(new String[] {"notSwiftNew"});
        resourceSelector = new ViewSwiftResource(browseServiceFactory, viewsResource, env, packageResourceService);
        resourceSelector.viewSwift(artifactInfo, servletRequest, servletResponse, request, uriInfo, httpHeaders);
        Mockito.verify(viewsResource).runService(any(), any(BaseArtifactInfo.class),
                any(HttpServletRequest.class), any(HttpServletResponse.class), any(Request.class), any(UriInfo.class),
                any(HttpHeaders.class));
        Mockito.verifyNoInteractions(packageResourceService);
    }

    @Test
    void expectNewSwiftView() throws PackageException {
        when(env.getActiveProfiles()).thenReturn(new String[] {"swiftNew"});
        resourceSelector = new ViewSwiftResource(browseServiceFactory, viewsResource, env, packageResourceService);
        resourceSelector.viewSwift(artifactInfo, servletRequest, servletResponse, request, uriInfo, httpHeaders);
        Mockito.verify(packageResourceService).getPackageInfo(any(), any());
        Mockito.verifyNoInteractions(viewsResource);
    }
}