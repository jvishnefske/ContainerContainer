package org.artifactory.util;

import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.repo.http.CloseableHttpExecute;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for ArtifactoryVersionUtil error handling
 */
class ArtifactoryVersionUtilTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private CloseableHttpExecute closeableHttpExecute;

    @Mock
    private CloseableHttpClient closeableHttpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @Mock
    private StatusLine statusLine;

    private final String instanceUrl = "http://test-server:8082";
    private final Map<String, String> headers = Collections.emptyMap();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // Comprehensive parameterized test for all exception handling scenarios

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("allExceptionScenarios")
    void testExceptionHandling(String clientType, String testName, Exception exception,
            Class<? extends Exception> expectedExceptionClass,
            String expectedMessage) throws IOException {
        // Given
        if ("CloseableHttpExecute".equals(clientType)) {
            when(closeableHttpExecute.executeMethod(any(HttpGet.class))).thenThrow(exception);
        } else {
            when(closeableHttpClient.execute(any(HttpGet.class))).thenThrow(exception);
        }

        // When & Then
        Exception actualException;
        if ("CloseableHttpExecute".equals(clientType)) {
            actualException = assertThrows(expectedExceptionClass, () ->
                    ArtifactoryVersionUtil.getArtifactoryServiceVersion(closeableHttpExecute, instanceUrl, headers));
        } else {
            actualException = assertThrows(expectedExceptionClass, () ->
                    ArtifactoryVersionUtil.getArtifactoryServiceVersion(closeableHttpClient, instanceUrl, headers));
        }

        if (actualException instanceof NotFoundException) {
            assertEquals(expectedMessage, actualException.getMessage());
            assertInstanceOf(exception.getClass(), actualException.getCause());
        } else if (actualException instanceof UncheckedIOException) {
            assertEquals(exception, actualException.getCause());
        }
    }

    // Tests for successful execution scenarios

    @Test
    void testSuccessfulExecution_CloseableHttpExecute() throws IOException {
        // Given
        when(closeableHttpExecute.executeMethod(any(HttpGet.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

        // When
        CloseableHttpResponse result = ArtifactoryVersionUtil.getArtifactoryServiceVersion(
                closeableHttpExecute, instanceUrl, headers);

        // Then
        assertSame(httpResponse, result);
        verify(closeableHttpExecute).executeMethod(any(HttpGet.class));
    }

    @Test
    void testSuccessfulExecution_CloseableHttpClient() throws IOException {
        // Given
        when(closeableHttpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

        // When
        CloseableHttpResponse result = ArtifactoryVersionUtil.getArtifactoryServiceVersion(
                closeableHttpClient, instanceUrl, headers);

        // Then
        assertSame(httpResponse, result);
        verify(closeableHttpClient).execute(any(HttpGet.class));
    }

    // Tests for backward compatibility method

    @Test
    void testBackwardCompatibility_NotFound() throws IOException {
        // Given
        CloseableHttpResponse notFoundResponse = mock(CloseableHttpResponse.class);
        CloseableHttpResponse successResponse = mock(CloseableHttpResponse.class);
        StatusLine notFoundStatusLine = mock(StatusLine.class);
        StatusLine successStatusLine = mock(StatusLine.class);

        when(notFoundResponse.getStatusLine()).thenReturn(notFoundStatusLine);
        when(notFoundStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);
        when(successResponse.getStatusLine()).thenReturn(successStatusLine);
        when(successStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

        when(closeableHttpClient.execute(any(HttpGet.class)))
                .thenReturn(notFoundResponse)
                .thenReturn(successResponse);

        // When
        CloseableHttpResponse result = ArtifactoryVersionUtil.getArtifactoryServiceVersion(
                closeableHttpClient, instanceUrl, headers);

        // Then
        assertSame(successResponse, result);
        verify(closeableHttpClient, times(2)).execute(any(HttpGet.class));
        verify(notFoundResponse).close();
    }

    @Test
    void testBackwardCompatibility_Success() throws IOException {
        // Given
        when(closeableHttpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

        // When
        CloseableHttpResponse result = ArtifactoryVersionUtil.getArtifactoryServiceVersion(
                closeableHttpClient, instanceUrl, headers);

        // Then
        assertSame(httpResponse, result);
        verify(closeableHttpClient, times(1)).execute(any(HttpGet.class));
        verify(httpResponse, never()).close();
    }

    // Comprehensive test data provider for all scenarios

    static Stream<Arguments> allExceptionScenarios() {
        return Stream.of(
                // CloseableHttpExecute scenarios
                Arguments.of("CloseableHttpExecute", "ConnectTimeoutException",
                        new ConnectTimeoutException("Connection timeout"),
                        NotFoundException.class,
                        "Connection timeout when accessing remote url: 'http://test-server:8082'"),
                Arguments.of("CloseableHttpExecute", "UnknownHostException",
                        new UnknownHostException("api"),
                        NotFoundException.class,
                        "Unknown host when accessing remote url: 'http://test-server:8082'"),
                Arguments.of("CloseableHttpExecute", "IllegalArgumentException",
                        new IllegalArgumentException("Invalid URL"),
                        NotFoundException.class,
                        "Invalid URL format when accessing remote url: 'http://test-server:8082'"),
                Arguments.of("CloseableHttpExecute", "SocketTimeoutException",
                        new SocketTimeoutException("Read timeout"),
                        UncheckedIOException.class,
                        null),

                // CloseableHttpClient scenarios
                Arguments.of("CloseableHttpClient", "ConnectTimeoutException",
                        new ConnectTimeoutException("Connection timeout"),
                        NotFoundException.class,
                        "Connection timeout when accessing remote url: 'http://test-server:8082'"),
                Arguments.of("CloseableHttpClient", "UnknownHostException",
                        new UnknownHostException("api"),
                        NotFoundException.class,
                        "Unknown host when accessing remote url: 'http://test-server:8082'"),
                Arguments.of("CloseableHttpClient", "IllegalArgumentException",
                        new IllegalArgumentException("Invalid URL"),
                        NotFoundException.class,
                        "Invalid URL format when accessing remote url: 'http://test-server:8082'"),
                Arguments.of("CloseableHttpClient", "HttpHostConnectException",
                        new HttpHostConnectException(new IOException("Connection refused"), null, null),
                        NotFoundException.class,
                        "Resource not found on remote url: 'http://test-server:8082'"),
                Arguments.of("CloseableHttpClient", "SocketTimeoutException",
                        new SocketTimeoutException("Read timeout"),
                        UncheckedIOException.class,
                        null)
        );
    }
}
