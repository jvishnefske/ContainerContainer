package org.artifactory.ui.rest.resource.artifacts.versions;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.artifactory.ui.rest.service.artifacts.search.NativeSearchServiceFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import java.util.List;

import static org.mockito.Mockito.when;

class VersionNativeResourceTest {
    @Mock private BrowseServiceFactory browseServiceFactory;
    @Mock private PackageResourceService packageResourceService;
    @Mock private NativeSearchServiceFactory nativeSearchServiceFactory;

    @Mock private UriInfo uriInfo;
    @Mock private HttpServletRequest servletRequest;
    @Mock private HttpServletResponse servletResponse;
    @Mock private Request request;
    @Mock private HttpHeaders httpHeaders;

    private VersionNativeResource resource;
    private AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);

        resource = new VersionNativeResource();
        resource.setBrowseServiceFactory(browseServiceFactory);
        resource.setPackageResourceService(packageResourceService);
        resource.setNativeSearchServiceFactory(nativeSearchServiceFactory);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testGetReadme() throws PackageException {
        MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
        queryParams.put("path", List.of("npm-local/colors/-/-colors-1.0.0.tgz"));
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);

        resource.getReadme(servletRequest, servletResponse, request, uriInfo, httpHeaders, "npm");
        Mockito.verify(packageResourceService).getVersionNativeReadme("npm", "npm-local/colors/-/-colors-1.0.0.tgz");
    }
}