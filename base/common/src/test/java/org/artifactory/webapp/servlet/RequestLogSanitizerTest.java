package org.artifactory.webapp.servlet;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.requestLogsExcludedQueryParamsPaths;
import static org.artifactory.common.ConstantValues.requestLogsExcludedQueryParams;
import static org.artifactory.webapp.servlet.RequestLogSanitizer.getSanitizedUriWithQueryParams;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class RequestLogSanitizerTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private HttpServletRequest request;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeMethod() {
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void afterMethod() throws Exception {
        autoCloseable.close();
        RequestLogSanitizer.refreshExcludedParams();
        RequestLogSanitizer.refreshExcludedPaths();
    }

    @ParameterizedTest
    @MethodSource("queryProvider")
    void testGetServletPathWithQuery(String query, String expected) {
        when(request.getRequestURI()).thenReturn("/api/resource");
        when(request.getQueryString()).thenReturn(query);

        String result = getSanitizedUriWithQueryParams(request);

        assertEquals(expected, result);
    }

    private static Stream<Arguments> queryProvider() {
        return Stream.of(
                Arguments.of("packageSearch=true", "/api/resource?packageSearch=true"),
                Arguments.of("token=JRKMRKVSV", "/api/resource?token=****"),
                Arguments.of("token=abc123&password=secret&id=456",
                        "/api/resource?token=****&password=****&id=456"),
                Arguments.of("", "/api/resource"),
                Arguments.of(null, "/api/resource")
        );
    }

    @Test
    void testGetServletPathFromRequestWithSensitivePath() {
        when(request.getRequestURI()).thenReturn("/api/auth");
        when(request.getQueryString()).thenReturn("token=abc123&password=secret&id=456");

        String result = getSanitizedUriWithQueryParams(request);

        assertEquals("/api/auth", result);
    }

    @Test
    void testGetSanitizedUriWithExcludedPaths() {
        getBound().setProperty(requestLogsExcludedQueryParamsPaths.getPropertyName(), "/excludedPath1");
        RequestLogSanitizer.refreshExcludedPaths();
        when(request.getRequestURI()).thenReturn("/api/excludedPath1");
        when(request.getQueryString()).thenReturn("param1=value1&param2=value2");

        String result = getSanitizedUriWithQueryParams(request);

        assertEquals("/api/excludedPath1", result);
    }

    @Test
    void testGetSanitizedUriWithExcludedParams() {
        getBound().setProperty(requestLogsExcludedQueryParams.getPropertyName(), "excludedParam1,excludedParam2");
        RequestLogSanitizer.refreshExcludedParams();
        when(request.getRequestURI()).thenReturn("/api/resource");
        when(request.getQueryString()).thenReturn("param1=value1&excludedParam2=value2");

        String result = getSanitizedUriWithQueryParams(request);

        assertEquals("/api/resource?param1=value1&excludedParam2=****", result);
    }
}