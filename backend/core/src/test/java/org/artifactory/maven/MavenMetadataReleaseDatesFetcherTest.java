package org.artifactory.maven;

import com.jfrog.ph.acl.exception.PackageNotFoundException;
import com.jfrog.ph.acl.service.PackageArtifactService;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.BasicHttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MavenMetadataReleaseDatesFetcherTest {

    private PackageArtifactService packageArtifactService;
    private MavenMetadataReleaseDatesFetcher fetcher;

    @BeforeEach
    void setUp() {
        packageArtifactService = mock(PackageArtifactService.class);
        fetcher = new MavenMetadataReleaseDatesFetcher(packageArtifactService);
    }

    @Test
    void testFetchReleaseDatesWhenDateNotMatchRegex() throws Exception {
        String htmlWithInvalidDate = """
                <a href="1.0.0/" title="1.0.0/">1.0.0/</a>
                <a href="2.0.0/" title="2.0.0/">2.0.0/</a> 2023-02-01 13:30
                """;
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(htmlWithInvalidDate.getBytes()));
            }});

        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);
        Map<String, Instant> result =
                fetcher.fetchReleaseDates("repo", "http://host/path/maven-metadata.xml",
                        new HashSet<>(List.of("1.0.0", "2.0.0")));
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchReleaseDatesWhenDateParsingFails() throws Exception {
        String validHtml = """
                <a href="1.0.0/" title="1.0.0/">1.0.0/</a>  13:30 2023-02-01
                """;
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(validHtml.getBytes()));
            }});

        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);

        Map<String, Instant> result =
                fetcher.fetchReleaseDates("repo", "http://host/path/maven-metadata.xml",
                        new HashSet<>(List.of("1.0.0", "2.0.0")));
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchReleaseDatesWithInvalidVersionLinks() throws Exception {
        // Create HTML with various invalid version links to test isValidVersionLink conditions
        String htmlWithInvalidLinks = """
                <a href="../">../</a> 2023-01-01 12:00
                <a href="1.0.0">1.0.0</a> 2023-02-01 13:30
                <a href="3.0.0/">3.0.0/</a> 2023-03-01 14:30
                <a href="1.0.0/">1.0.0/</a> 2023-04-01 15:30
                """;

        Set<String> versionsSet = new HashSet<>(List.of("1.0.0", "2.0.0"));
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(htmlWithInvalidLinks.getBytes()));
            }});

        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);

        Map<String, Instant> result = fetcher.fetchReleaseDates("repo",
                "http://host/path/maven-metadata.xml", versionsSet);

        // Should only contain 1.0.0 (the valid one), not ../, 1.0.0 without slash, or 3.0.0 not in set
        assertEquals(1, result.size());
        assertTrue(result.containsKey("1.0.0"));
    }

    @Test
    void testFetchReleaseDatesWithNonTextNodeAfterLink() throws Exception {
        String htmlWithNoTextAfterLink = """
                <a href="1.0.0/">1.0.0/</a><span>some element</span>
                """;

        Set<String> versionsSet = new HashSet<>(List.of("1.0.0"));
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(htmlWithNoTextAfterLink.getBytes()));
            }});

        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);

        Map<String, Instant> result = fetcher.fetchReleaseDates("repo",
                "http://host/path/maven-metadata.xml", versionsSet);

        // Should return empty map when nextNode is not a TextNode
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchReleaseDatesWithInsufficientDateParts() throws Exception {
        String htmlWithInsufficientDateParts = """
                <a href="1.0.0/">1.0.0/</a> onlyonepart
                <a href="2.0.0/">2.0.0/</a> 2023-02-01 13:30
                """;

        Set<String> versionsSet = new HashSet<>(List.of("1.0.0", "2.0.0"));
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(htmlWithInsufficientDateParts.getBytes()));
            }});

        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);

        Map<String, Instant> result = fetcher.fetchReleaseDates("repo",
                "http://host/path/maven-metadata.xml", versionsSet);

        // Should return empty map when date parsing fails due to insufficient parts
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchReleaseDatesWhenNextNodeIsNull() throws Exception {
        // Create HTML where the link has no next sibling (no text after the link)
        String htmlWithNoNextSibling = "<a href=\"1.0.0/\">1.0.0/</a>";

        Set<String> versionsSet = new HashSet<>(List.of("1.0.0"));
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(htmlWithNoNextSibling.getBytes()));
            }});

        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);

        Map<String, Instant> result = fetcher.fetchReleaseDates("repo",
                "http://host/path/maven-metadata.xml", versionsSet);

        // Should return empty map when nextNode is null
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchReleaseDatesParsesDatesSuccessfully() throws Exception {
        String html = """
                <a href="1.0.0/" title="1.0.0/">1.0.0/</a> 2023-01-01 12:00
                <a href="2.0.0/" title="2.0.0/">2.0.0/</a> 2023-02-01 13:30
                """;
        Set<String> versionsSet = new HashSet<>(List.of("1.0.0", "2.0.0"));
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(new BasicHttpEntity() {{
                setContent(new ByteArrayInputStream(html.getBytes()));
            }});
        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);
        doReturn(response).when(packageArtifactService).executeRemoteHttpRequest(anyString(), any());
        Map<String, Instant> result =
                fetcher.fetchReleaseDates("repo", "http://host/path/maven-metadata.xml", versionsSet);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("1.0.0"));
        assertTrue(result.containsKey("2.0.0"));
    }

    @Test
    void testFetchReleaseDatesHttpErrorReturnsEmptyMap() throws Exception {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(404);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenReturn(response);
        Map<String, Instant> result =
                fetcher.fetchReleaseDates("repo", "http://host/path/maven-metadata.xml",
                        new HashSet<>(List.of("1.0.0", "2.0.0")));
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchReleaseDatesExceptionReturnsEmptyMap() throws Exception {
        when(packageArtifactService.executeRemoteHttpRequest(anyString(), any())).thenThrow(
                new PackageNotFoundException());
        Map<String, Instant> result =
                fetcher.fetchReleaseDates("repo", "http://host/path/maven-metadata.xml",
                        new HashSet<>(List.of("1.0.0", "2.0.0")));
        assertTrue(result.isEmpty());
    }
}