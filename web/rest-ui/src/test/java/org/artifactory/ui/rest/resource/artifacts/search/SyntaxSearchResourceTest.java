package org.artifactory.ui.rest.resource.artifacts.search;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.service.ServiceExecutor;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Search;
import org.artifactory.ui.rest.service.artifacts.search.SearchServiceFactory;
import org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchRestService;
import org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resource-layer unit tests for {@link SyntaxSearchResource}. Locks the static
 * {@link Path}/{@link RolesAllowed} contract, asserts the feature-flag guard
 * ({@code assertEnabled}) wiring, and pins the {@link SyntaxSearchServiceException}
 * trap to a 400 + standard {@link ErrorResponse} envelope so the UI never sees a 500
 * from this endpoint.
 *
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class SyntaxSearchResourceTest {

    @Mock
    private SearchServiceFactory searchFactory;

    @Mock
    private SyntaxSearchRestService syntaxSearchRestService;

    @Mock
    private ServiceExecutor serviceExecutor;

    @Mock
    private ArtifactoryRestResponse restResponse;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private HttpServletResponse servletResponse;

    @Mock
    private Request request;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private HttpHeaders httpHeaders;

    private SyntaxSearchResource resource;

    @BeforeEach
    void setUp() {
        resource = new SyntaxSearchResource();
        ReflectionTestUtils.setField(resource, "searchFactory", searchFactory);
        ReflectionTestUtils.setField(resource, "serviceExecutor", serviceExecutor);
        ReflectionTestUtils.setField(resource, "artifactoryResponse", restResponse);
        ReflectionTestUtils.setField(resource, "enabled", true);
        // @Context-injected fields on BaseResource — needed by runService → ArtifactoryRestRequest
        ReflectionTestUtils.setField(resource, "servletRequest", servletRequest);
        ReflectionTestUtils.setField(resource, "servletResponse", servletResponse);
        ReflectionTestUtils.setField(resource, "request", request);
        ReflectionTestUtils.setField(resource, "uriInfo", uriInfo);
        ReflectionTestUtils.setField(resource, "httpHeaders", httpHeaders);
        // UiRequestUtils.isUiRestRequest reads contextPath + requestURI; lenient because not every test uses it.
        org.mockito.Mockito.lenient().when(servletRequest.getContextPath()).thenReturn("/artifactory");
        org.mockito.Mockito.lenient().when(servletRequest.getRequestURI()).thenReturn("/artifactory/ui/syntax-search");
    }

    @Test
    void pathAnnotationIsSyntaxSearch() {
        Path path = SyntaxSearchResource.class.getAnnotation(Path.class);
        assertNotNull(path, "SyntaxSearchResource must carry @Path at class level");
        assertEquals("syntax-search", path.value(), "@Path must remain 'syntax-search' (UI contract)");
    }

    @Test
    void rolesAllowedDeclaresAdminAndUser() {
        RolesAllowed rolesAllowed = SyntaxSearchResource.class.getAnnotation(RolesAllowed.class);
        assertNotNull(rolesAllowed, "SyntaxSearchResource must carry @RolesAllowed at class level");
        List<String> declared = Arrays.asList(rolesAllowed.value());
        assertTrue(declared.contains(AuthorizationService.ROLE_ADMIN),
                "@RolesAllowed must include ROLE_ADMIN; got: " + declared);
        assertTrue(declared.contains(AuthorizationService.ROLE_USER),
                "@RolesAllowed must include ROLE_USER; got: " + declared);
        assertEquals(2, declared.size(),
                "@RolesAllowed must be exactly {ROLE_ADMIN, ROLE_USER}; got: " + declared);
    }

    @Test
    void getSearchResultsThrowsBadRequestWhenDisabled() {
        ReflectionTestUtils.setField(resource, "enabled", false);
        Search search = Mockito.mock(Search.class);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resource.getSearchResults(search));
        assertEquals("Syntax Search is disabled", ex.getMessage());
        // Service must NOT be invoked when the feature flag is off.
        verify(searchFactory, Mockito.never()).syntaxSearchService();
    }

    @Test
    void getSearchResultsDelegatesToRestServiceWhenEnabled() {
        Search search = Mockito.mock(Search.class);
        Response expected = Response.ok().build();
        when(searchFactory.syntaxSearchService()).thenReturn(syntaxSearchRestService);
        when(serviceExecutor.process(any(ArtifactoryRestRequest.class), eq(restResponse),
                eq(syntaxSearchRestService))).thenReturn(expected);

        Response actual = resource.getSearchResults(search);

        assertEquals(expected, actual, "Resource must return the Response built by ServiceExecutor as-is");
        verify(serviceExecutor).process(any(ArtifactoryRestRequest.class), eq(restResponse),
                eq(syntaxSearchRestService));
    }

    @Test
    void getSearchResultsConvertsServiceFailureTo400WithStandardEnvelope() {
        Search search = Mockito.mock(Search.class);
        when(searchFactory.syntaxSearchService()).thenReturn(syntaxSearchRestService);
        when(serviceExecutor.process(any(ArtifactoryRestRequest.class), any(RestResponse.class),
                any(RestService.class)))
                .thenThrow(new SyntaxSearchServiceException("Failed to execute syntax search"));

        Response response = resource.getSearchResults(search);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus(),
                "Server-side syntax search failures must be exposed as 400, not 500");
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType(),
                "Error response must be application/json");
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertNotNull(body, "Response entity must be a standard ErrorResponse envelope, not a bare string");
        assertEquals(1, body.getErrors().size(), "Envelope must contain exactly one error");
        ErrorResponse.Error error = body.getErrors().getFirst();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), error.getStatus());
        assertEquals("Failed to execute syntax search", error.getMessage());
    }

    @Test
    void getSearchResultsPropagatesNonSyntaxSearchExceptions() {
        Search search = Mockito.mock(Search.class);
        when(searchFactory.syntaxSearchService()).thenReturn(syntaxSearchRestService);
        when(serviceExecutor.process(any(ArtifactoryRestRequest.class), any(RestResponse.class),
                any(RestService.class)))
                .thenThrow(new BadRequestException("Search query can't be empty"));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> resource.getSearchResults(search));
        assertEquals("Search query can't be empty", ex.getMessage());
    }
}
