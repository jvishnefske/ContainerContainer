package org.artifactory.request;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Shahaf Golan
 */
class PathOverrideRequestContextTest {

    private static final String OVERRIDDEN_PATH = "normalized/path";
    private static final String OVERRIDDEN_REPO_KEY = "my-repo";

    @Mock
    private InternalRequestContext delegate;
    @Mock
    private RepoPath overriddenRepoPath;

    private PathOverrideRequestContext context;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(overriddenRepoPath.getPath()).thenReturn(OVERRIDDEN_PATH);
        when(overriddenRepoPath.getRepoKey()).thenReturn(OVERRIDDEN_REPO_KEY);
        context = new PathOverrideRequestContext(delegate, overriddenRepoPath);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ---- path overrides ----

    @Test
    void getResourcePath_returnsOverriddenPath() {
        assertEquals(OVERRIDDEN_PATH, context.getResourcePath());
    }

    @Test
    void getPath_returnsOverriddenPath() {
        assertEquals(OVERRIDDEN_PATH, context.getPath());
    }

    @Test
    void getRepoKey_returnsOverriddenRepoKey() {
        assertEquals(OVERRIDDEN_REPO_KEY, context.getRepoKey());
    }

    // ---- delegation ----

    @Test
    void isFromAnotherArtifactory_delegatesToOriginal() {
        when(delegate.isFromAnotherArtifactory()).thenReturn(true);
        assertTrue(context.isFromAnotherArtifactory());
        verify(delegate).isFromAnotherArtifactory();
    }

    @Test
    void getProperties_delegatesToOriginal() {
        Properties props = mock(Properties.class);
        when(delegate.getProperties()).thenReturn(props);
        assertSame(props, context.getProperties());
    }

    @Test
    void getRequest_delegatesToOriginal() {
        Request request = mock(Request.class);
        when(delegate.getRequest()).thenReturn(request);
        assertSame(request, context.getRequest());
    }

    @Test
    void setAttribute_delegatesToOriginal() {
        context.setAttribute("key", "value");
        verify(delegate).setAttribute("key", "value");
    }

    @Test
    void getAttribute_delegatesToOriginal() {
        when(delegate.getAttribute("key")).thenReturn("value");
        assertEquals("value", context.getAttribute("key"));
    }

    @Test
    void alterPath_delegatesToOriginal() {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        RepoPath alteredPath = mock(RepoPath.class);
        RepoPath inputPath = mock(RepoPath.class);
        when(delegate.alterPath(response, inputPath)).thenReturn(alteredPath);
        assertSame(alteredPath, context.alterPath(response, inputPath));
    }

    @Test
    void isSkipCurationOnResource_delegatesToOriginal() {
        when(delegate.isSkipCurationOnResource()).thenReturn(false);
        assertFalse(context.isSkipCurationOnResource());
    }

    @Test
    void setSkipCurationOnResource_delegatesToOriginal() {
        context.setSkipCurationOnResource(false);
        verify(delegate).setSkipCurationOnResource(false);
    }

    @Test
    void isForceExpiryCheck_delegatesToOriginal() {
        when(delegate.isForceExpiryCheck()).thenReturn(true);
        assertTrue(context.isForceExpiryCheck());
    }

    @Test
    void getRequestHeaders_delegatesToOriginal() {
        Map<String, String> headers = Map.of("X-Custom", "val");
        when(delegate.getRequestHeaders()).thenReturn(headers);
        assertSame(headers, context.getRequestHeaders());
    }

    @Test
    void shouldUnExpireCachedResource_delegatesToOriginal() {
        RepoResource resource = mock(RepoResource.class);
        when(delegate.shouldUnExpireCachedResource(resource)).thenReturn(true);
        assertTrue(context.shouldUnExpireCachedResource(resource));
    }

    @Test
    void getServletContextUrl_delegatesToOriginal() {
        when(delegate.getServletContextUrl())
                .thenReturn("http://localhost:8081/artifactory");
        assertEquals("http://localhost:8081/artifactory",
                context.getServletContextUrl());
    }

    @Test
    void getZipResourcePath_delegatesToOriginal() {
        when(delegate.getZipResourcePath()).thenReturn("path/in/zip");
        assertEquals("path/in/zip", context.getZipResourcePath());
    }
}
