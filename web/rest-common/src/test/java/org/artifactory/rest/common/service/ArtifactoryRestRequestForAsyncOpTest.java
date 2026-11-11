package org.artifactory.rest.common.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.UriInfo;
import org.artifactory.util.UiRequestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class ArtifactoryRestRequestForAsyncOpTest {
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private Request request;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private HttpHeaders httpHeaders;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testGetPathParamByKey() {
        ArtifactoryRestRequest.RequestBuilder<String> builder =
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, request, uriInfo, httpHeaders);
        MultivaluedMap<String, String> pathParamMap = new MultivaluedHashMap<>();
        pathParamMap.add("pathParamKey", "pathParamValue1");
        pathParamMap.add("pathParamKey", "pathParamValue2");
        when(uriInfo.getPathParameters()).thenReturn(pathParamMap);

        ArtifactoryRestRequestForAsyncOp<String> artifactoryRestRequestForAsyncOp;
        try (MockedStatic<UiRequestUtils> uiRequestUtilsMock = mockStatic(UiRequestUtils.class)) {
            uiRequestUtilsMock.when(() -> UiRequestUtils.isUiRestRequest(servletRequest)).thenReturn(true);
            artifactoryRestRequestForAsyncOp = new ArtifactoryRestRequestForAsyncOp<>(builder);
        }

        assertEquals("pathParamValue1", artifactoryRestRequestForAsyncOp.getPathParamByKey("pathParamKey"));
    }

    @Test
    void testGetQueryParamByKey() {
        ArtifactoryRestRequest.RequestBuilder<String> builder =
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, request, uriInfo, httpHeaders);
        MultivaluedMap<String, String> queryParamMap = new MultivaluedHashMap<>();
        queryParamMap.add("queryParamKey", "queryParamValue1");
        queryParamMap.add("queryParamKey", "queryParamValue2");
        when(uriInfo.getQueryParameters()).thenReturn(queryParamMap);

        ArtifactoryRestRequestForAsyncOp<String> artifactoryRestRequestForAsyncOp;
        try (MockedStatic<UiRequestUtils> uiRequestUtilsMock = mockStatic(UiRequestUtils.class)) {
            uiRequestUtilsMock.when(() -> UiRequestUtils.isUiRestRequest(servletRequest)).thenReturn(true);
            artifactoryRestRequestForAsyncOp = new ArtifactoryRestRequestForAsyncOp<>(builder);
        }

        assertEquals("queryParamValue1", artifactoryRestRequestForAsyncOp.getQueryParamByKey("queryParamKey"));
    }

    @Test
    void testGetQueryParamListByKey() {
        ArtifactoryRestRequest.RequestBuilder<String> builder =
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, request, uriInfo, httpHeaders);
        MultivaluedMap<String, String> queryParamMap = new MultivaluedHashMap<>();
        queryParamMap.add("queryParamKey", "queryParamValue1");
        queryParamMap.add("queryParamKey", "queryParamValue2");
        when(uriInfo.getQueryParameters()).thenReturn(queryParamMap);

        ArtifactoryRestRequestForAsyncOp<String> artifactoryRestRequestForAsyncOp;
        try (MockedStatic<UiRequestUtils> uiRequestUtilsMock = mockStatic(UiRequestUtils.class)) {
            uiRequestUtilsMock.when(() -> UiRequestUtils.isUiRestRequest(servletRequest)).thenReturn(true);
            artifactoryRestRequestForAsyncOp = new ArtifactoryRestRequestForAsyncOp<>(builder);
        }

        assertTrue(artifactoryRestRequestForAsyncOp.getQueryParamListByKey("queryParamKey")
                .containsAll(Set.of("queryParamValue1", "queryParamValue2")));
    }
}