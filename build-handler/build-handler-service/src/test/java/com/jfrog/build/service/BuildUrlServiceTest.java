package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildUrlServiceTest {

    private BuildUrlService service;

    private BuildBridgeToArtifactory bridge;

    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        bridge = mock(BuildBridgeToArtifactory.class);
        request = mock(HttpServletRequest.class);
        service = new BuildUrlService(bridge);
    }

    @Test
    void getServletContextUrl_delegatesToBridge() {
        when(bridge.getServletContextUrl(request)).thenReturn("https://acme.com/artifactory");

        assertEquals("https://acme.com/artifactory", service.getServletContextUrl(request));
    }

    @Test
    void redirectBuildUpload_delegatesToBridge() throws IOException {
        HttpServletResponse response = mock(HttpServletResponse.class);

        service.redirectBuildUpload(request, response, "api/build/my-repo/upload");

        verify(bridge).redirectBuildUpload(same(request), same(response), eq("api/build/my-repo/upload"));
    }

    @Test
    void getBaseBuildsHref_suffixesServletContextUrlWithApiBuild() {
        when(bridge.getServletContextUrl(request)).thenReturn("https://acme.com/artifactory");

        assertEquals("https://acme.com/artifactory/api/build", service.getBaseBuildsHref(request));
    }

    @Test
    void getBuildInfoHref_concatenatesSegments() {
        when(bridge.getServletContextUrl(request)).thenReturn("https://acme.com/artifactory");

        assertEquals(
                "https://acme.com/artifactory/api/build/my-app/1?buildRepo=artifactory-build-info",
                service.getBuildInfoHref(request, "my-app", "1", "artifactory-build-info"));
    }

    @Test
    void getBuildRelativeHref_prefixesSlashAndDoesNotLeaveSpacesLiteral() {
        String href = BuildUrlService.getBuildRelativeHref("a b");

        assertTrue(href.startsWith("/"));
        assertFalse(href.contains(" "), "space must not appear unencoded in the href");
    }

    @Test
    void getBuildRepoRelativeHref_includesBuildRepoQueryParam() {
        assertEquals("?buildRepo=my-repo", BuildUrlService.getBuildRepoRelativeHref("my-repo"));
    }

    @Test
    void getBuildNumberRelativeHref_prefixesSlashAndDoesNotLeaveSpacesLiteral() {
        String href = BuildUrlService.getBuildNumberRelativeHref("1 2");

        assertEquals('/', href.charAt(0));
        assertFalse(href.contains(" "), "space must not appear unencoded in the href");
    }
}
