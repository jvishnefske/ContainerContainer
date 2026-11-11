package org.artifactory.ui.rest.service.utils.repositories;


import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.service.utils.repositories.repodata.GetRepositoriesDataHandler;
import org.artifactory.ui.rest.service.utils.repositories.repodata.RepositoriesDataHandlerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;


import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAllRepositoriesServiceTest {
    @Mock
    private RepositoriesDataHandlerFactory<Object> repositoriesDataHandlerFactory;

    @Mock
    private GetRepositoriesDataHandler<Object> handler;

    @Mock
    private ArtifactoryRestRequest<Object> request;

    @Mock
    private RestResponse response;

    @InjectMocks
    private GetAllRepositoriesService<Object> getAllRepositoriesService;
    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void teardown() throws Exception {
        closeable.close();
    }

    @Test
    void testExecuteWithValidQueryParam() {
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
        queryParameters.add("validQueryParam", "value");
        when(repositoriesDataHandlerFactory.getHandledQueryParams()).thenReturn(Set.of("validQueryParam"));
        when(request.getUriInfo()).thenReturn(mock(UriInfo.class));
        when(request.getUriInfo().getQueryParameters()).thenReturn(queryParameters);
        when(repositoriesDataHandlerFactory.getHandler("validQueryParam")).thenReturn(handler);

        getAllRepositoriesService.execute(request, response);

        verify(repositoriesDataHandlerFactory).getHandler("validQueryParam");
        verify(handler).handle(request, response);
    }

    @Test
    void testExecuteWithInvalidQueryParam() {
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
        queryParameters.add("invalidQueryParam", "value");
        when(request.getUriInfo()).thenReturn(mock(UriInfo.class));
        when(request.getUriInfo().getQueryParameters()).thenReturn(queryParameters);
        when(repositoriesDataHandlerFactory.getHandler("")).thenReturn(handler);

        getAllRepositoriesService.execute(request, response);

        verify(repositoriesDataHandlerFactory).getHandler("");
        verify(handler).handle(request, response);
    }

    @Test
    void testGetRequestQueryParamWithValidParam() {
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
        queryParameters.add("validQueryParam", "value");
        when(repositoriesDataHandlerFactory.getHandledQueryParams())
                .thenReturn(Set.of("validQueryParam", "anotherParam"));
        when(request.getUriInfo()).thenReturn(mock(UriInfo.class));
        when(request.getUriInfo().getQueryParameters()).thenReturn(queryParameters);

        String queryParam = getAllRepositoriesService.getRequestQueryParam(request);

        assertEquals("validQueryParam", queryParam);
    }

    @Test
    void testGetRequestQueryParamWithNoValidParam() {
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
        queryParameters.add("someOtherParam", "value");
        when(repositoriesDataHandlerFactory.getHandledQueryParams()).thenReturn(Set.of("validQueryParam"));
        when(request.getUriInfo()).thenReturn(mock(UriInfo.class));
        when(request.getUriInfo().getQueryParameters()).thenReturn(queryParameters);

        String queryParam = getAllRepositoriesService.getRequestQueryParam(request);

        assertEquals("", queryParam);
    }

    @Test
    void testGetRequestQueryParamWithEmptyParams() {
        MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
        when(repositoriesDataHandlerFactory.getHandledQueryParams()).thenReturn(Set.of("validQueryParam"));
        when(request.getUriInfo()).thenReturn(mock(UriInfo.class));
        when(request.getUriInfo().getQueryParameters()).thenReturn(queryParameters);

        String queryParam = getAllRepositoriesService.getRequestQueryParam(request);
        assertEquals("", queryParam);
    }
}