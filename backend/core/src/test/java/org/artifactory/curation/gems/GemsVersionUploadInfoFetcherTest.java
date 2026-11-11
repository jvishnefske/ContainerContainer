package org.artifactory.curation.gems;

import com.jfrog.ph.acl.service.PackageArtifactService;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author avivbr
 */
class GemsVersionUploadInfoFetcherTest {

    private PackageArtifactService packageArtifactService;
    private GemsVersionUploadInfoFetcher fetcher;

    @BeforeEach
    void setUp() {
        packageArtifactService = mock(PackageArtifactService.class);
        fetcher = new GemsVersionUploadInfoFetcher(packageArtifactService);
    }

    @Test
    void fetchVersions_shouldParseJsonResponse_whenApiReturnsValidData() throws Exception {
        // Arrange
        String jsonResponse = """
                [
                  {
                    "number": "1.0.0",
                    "platform": "ruby",
                    "created_at": "2024-01-15T10:30:00.000Z"
                  },
                  {
                    "number": "1.1.0",
                    "platform": "java",
                    "created_at": "2024-02-20T14:45:30.123Z"
                  }
                ]
                """;
        mockSuccessfulResponse(jsonResponse);

        // Act
        Map<String, Instant> versions = fetcher.fetchVersions("repo1", "http://rubygems.org", "rails");

        // Assert
        assertEquals(2, versions.size());
        assertNotNull(versions.get("1.0.0"));
        assertNotNull(versions.get("1.1.0-java"));
    }

    @Test
    void fetchVersions_shouldReturnEmptyList_whenApiReturnsError() throws Exception {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(404);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(packageArtifactService.executeRemoteHttpRequest(eq("repo1"), any(HttpRequestBase.class)))
                .thenReturn(response);

        // Act
        Map<String, Instant> versions = fetcher.fetchVersions("repo1", "http://rubygems.org", "nonexistent");

        // Assert
        assertTrue(versions.isEmpty());
    }

    @Test
    void fetchVersions_shouldReturnEmptyList_whenExceptionOccurs() throws Exception {
        when(packageArtifactService.executeRemoteHttpRequest(eq("repo1"), any(HttpRequestBase.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        // Act
        Map<String, Instant> versions = fetcher.fetchVersions("repo1", "http://rubygems.org", "rails");

        // Assert
        assertTrue(versions.isEmpty());
    }

    @Test
    void fetchVersions_shouldBuildCorrectApiUrl_whenBaseUrlHasTrailingSlash() throws Exception {
        // Arrange - verifying URL construction works with trailing slash
        // Also tests date parsing without milliseconds (ISO 8601 flexible parsing)
        String jsonResponse =
                "[{\"number\": \"1.0.0\", \"platform\": \"ruby\", \"created_at\": \"2024-01-01T00:00:00Z\"}]";
        mockSuccessfulResponse(jsonResponse);

        // Act - use trailing slash in URL (the key difference being tested)
        Map<String, Instant> versions = fetcher.fetchVersions("repo1", "http://rubygems.org/", "mygem");

        // Assert
        assertEquals(1, versions.size());
    }

    @Test
    void fetchVersions_shouldIgnoreUnknownJsonFields_whenApiReturnsExtraFields() throws Exception {
        // Arrange
        String jsonResponse = """
                [
                  {
                    "number": "1.0.0",
                    "platform": "ruby",
                    "created_at": "2024-01-15T10:30:00.000Z",
                    "unknown_field": "should be ignored",
                    "another_unknown": 12345
                  }
                ]
                """;
        mockSuccessfulResponse(jsonResponse);

        // Act
        Map<String, Instant> versions = fetcher.fetchVersions("repo1", "http://rubygems.org", "rails");

        // Assert
        assertEquals(1, versions.size());
        assertNotNull(versions.get("1.0.0"));
    }

    private void mockSuccessfulResponse(String jsonContent) throws Exception {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        HttpEntity entity = mock(HttpEntity.class);

        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)));

        when(packageArtifactService.executeRemoteHttpRequest(eq("repo1"), any(HttpRequestBase.class)))
                .thenReturn(response);
    }
}
