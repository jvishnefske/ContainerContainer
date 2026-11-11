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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class ArtifactoryRestRequestTest {
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private Request request;
    @Mock
    private UriInfo uriInfo;
    @Mock
    private HttpHeaders httpHeaders;

    AutoCloseable closeable;

    public static Stream<Arguments> pathParamProvider() {
        return Stream.of(
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("non-existent-key", "pathParamValue1");
                    }}, ""),
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("", "pathParamValue1");
                    }}, ""),
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("pathParamKey", "pathParamValue1");
                    }}, "pathParamValue1"),
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("pathParamKey", "pathParamValue1");
                        add("pathParamKey", "pathParamValue2");
                    }}, "pathParamValue1"),
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("pathParamKey", "pathParamValue1");
                    }}, "pathParamValue1"),
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("pathParamKey", "/path");
                    }}, "path")
        );
    }

    public static Stream<Arguments> queryParamProvider() {
        return Stream.of(
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("non-existent-key", "queryParamValue1");
                    }}, ""),
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("queryParamKey", "queryParamValue1");
                        add("queryParamKey", "queryParamValue2");
                    }}, "queryParamValue1")
        );
    }

    public static Stream<Arguments> queryParamListProvider() {
        return Stream.of(
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("non-existent-key", "queryParamValue1");
                    }}, List.of()),
                Arguments.of(new MultivaluedHashMap<String, String>() {{
                        add("queryParamKey", "queryParamValue1");
                        add("queryParamKey", "queryParamValue2");
                    }}, List.of("queryParamValue1", "queryParamValue2"))
        );
    }

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @ParameterizedTest
    @MethodSource("pathParamProvider")
    void testGetPathParamByKey(MultivaluedMap<String, String> pathParamMap, String expectedValue) {
        ArtifactoryRestRequest.RequestBuilder<String> builder =
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, request, uriInfo, httpHeaders);
        when(uriInfo.getPathParameters()).thenReturn(pathParamMap);

        ArtifactoryRestRequest<String> artifactoryRestRequest;
        try (MockedStatic<UiRequestUtils> uiRequestUtilsMock = mockStatic(UiRequestUtils.class)) {
            uiRequestUtilsMock.when(() -> UiRequestUtils.isUiRestRequest(servletRequest)).thenReturn(true);
            artifactoryRestRequest = new ArtifactoryRestRequest<>(builder);
        }

        assertEquals(expectedValue, artifactoryRestRequest.getPathParamByKey("pathParamKey"));
    }

    @ParameterizedTest
    @MethodSource("queryParamProvider")
    void testGetQueryParamByKey(MultivaluedMap<String, String> queryParamMap, String expectedValue) {
        ArtifactoryRestRequest.RequestBuilder<String> builder =
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, request, uriInfo, httpHeaders);
        when(uriInfo.getQueryParameters()).thenReturn(queryParamMap);

        ArtifactoryRestRequest<String> artifactoryRestRequest;
        try (MockedStatic<UiRequestUtils> uiRequestUtilsMock = mockStatic(UiRequestUtils.class)) {
            uiRequestUtilsMock.when(() -> UiRequestUtils.isUiRestRequest(servletRequest)).thenReturn(true);
            artifactoryRestRequest = new ArtifactoryRestRequest<>(builder);
        }

        assertEquals(expectedValue, artifactoryRestRequest.getQueryParamByKey("queryParamKey"));
    }

    @ParameterizedTest
    @MethodSource("queryParamListProvider")
    void testGetQueryParamListByKey(MultivaluedMap<String, String> queryParamMap, List<String> expectedList) {
        ArtifactoryRestRequest.RequestBuilder<String> builder =
                new ArtifactoryRestRequest.RequestBuilder<>(servletRequest, request, uriInfo, httpHeaders);
        when(uriInfo.getQueryParameters()).thenReturn(queryParamMap);

        ArtifactoryRestRequest<String> artifactoryRestRequest;
        try (MockedStatic<UiRequestUtils> uiRequestUtilsMock = mockStatic(UiRequestUtils.class)) {
            uiRequestUtilsMock.when(() -> UiRequestUtils.isUiRestRequest(servletRequest)).thenReturn(true);
            artifactoryRestRequest = new ArtifactoryRestRequest<>(builder);
        }

        assertTrue(artifactoryRestRequest.getQueryParamListByKey("queryParamKey").containsAll(expectedList));
    }
}