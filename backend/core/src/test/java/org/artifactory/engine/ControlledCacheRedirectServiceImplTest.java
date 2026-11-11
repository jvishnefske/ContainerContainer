package org.artifactory.engine;

import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ControlledCacheRedirectService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.Request;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.util.Enumeration;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.api.request.ArtifactoryHeaders.PARAM_FORCE_GET_STREAM;
import static org.artifactory.api.request.ArtifactoryHeaders.X_JFROG_CONTROLLED_CACHE_REQUEST;
import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_DOWNLOAD_VALUE;
import static org.artifactory.engine.ControlledCacheConstants.JFROG_CONTROLLED_CACHE_VALIDATE_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ControlledCacheRedirectServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private ControlledCacheRedirectService controlledCacheRedirectService;
    private RepositoryService repositoryService;

    @BeforeEach
    void setup() {
        repositoryService = mock(RepositoryService.class);
        controlledCacheRedirectService = new ControlledCacheRedirectServiceImpl(repositoryService);
    }

    @Test
    void testCreateRedirect() {
        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        when(repoResourceInfo.getRepoPath()).thenReturn(RepoPathFactory.create("repoKey", "repoPath"));
        when(repoResourceInfo.getSha1()).thenReturn("sha1");
        URI uri = controlledCacheRedirectService.createRedirectUri(repoResourceInfo);
        assertEquals("/artifactory/repoKey/repoPath?key=sha1", uri.toString());
    }

    @Test
    void testCreateRedirectRemoveDashCache() {
        RepoResourceInfo repoResourceInfo = mock(RepoResourceInfo.class);
        when(repoResourceInfo.getRepoPath()).thenReturn(RepoPathFactory.create("maven-repo-cache", "repoPath"));
        when(repoResourceInfo.getSha1()).thenReturn("sha1");
        URI uri = controlledCacheRedirectService.createRedirectUri(repoResourceInfo);
        assertEquals("/artifactory/maven-repo/repoPath?key=sha1", uri.toString());
    }

    @ParameterizedTest
    @MethodSource("shouldRedirectProvider")
    void testShouldRedirect(Args args) {
        mockFeatureFlag(args.isFeatureFlagEnabled());

        Request request = mockRequest(args.isHead(), args.isRange());
        mockHeaders(request, args.getHeaders());
        mockQueryParams(request, args.getQueryParams());

        RepoResource resource = mockRepoResource(args.isFound(), args.isExpirable());

        if (args.isRemote()) {
            mockRemoteRepo(args.isStoreArtifactsLocally());
        }
        boolean shouldRedirect =
                controlledCacheRedirectService.shouldRedirect(request, resource, args.getRedirectType());
        assertEquals(args.isExpectedResult(), shouldRedirect);
    }

    private void mockRemoteRepo(boolean storeArtifactsLocally) {
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class, RETURNS_DEEP_STUBS);
        when(remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally()).thenReturn(storeArtifactsLocally);
        when(repositoryService.getRemoteRepoConfigByKey("repoKey")).thenReturn(remoteRepoConfig);
    }

    @Nonnull
    private static Request mockRequest(boolean head, boolean range) {
        Request request = mock(Request.class);
        when(request.isHeadOnly()).thenReturn(head);
        when(request.isRange()).thenReturn(range);
        return request;
    }

    private void mockFeatureFlag(boolean flagEnabled) {
        getBound().setProperty(ConstantValues.artifactControlledCacheEnabled, String.valueOf(flagEnabled));
    }

    private void mockQueryParams(Request request, Map<String, String> queryParams) {
        for (Map.Entry<String, String> queryParam : queryParams.entrySet()) {
            when(request.getParameter(queryParam.getKey())).thenReturn(queryParam.getValue());
        }
    }

    @Nonnull
    private static RepoResource mockRepoResource(boolean found, boolean expirable) {
        RepoResource resource = mock(RepoResource.class);
        when(resource.isFound()).thenReturn(found);
        when(resource.isExpirable()).thenReturn(expirable);
        RepoPath repoPath = mock(RepoPath.class);
        when(repoPath.getRepoKey()).thenReturn("repoKey");
        when(repoPath.getPath()).thenReturn("repoPath");
        when(resource.getRepoPath()).thenReturn(repoPath);
        return resource;
    }

    private static void mockHeaders(Request request, Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            Enumeration<String> enumeration = mock(Enumeration.class);
            when(enumeration.hasMoreElements()).thenReturn(true);
            when(enumeration.nextElement()).thenReturn(header.getValue());
            when(request.getHeaderValues(header.getKey())).thenReturn(enumeration);
        }
    }

    private static Stream<Args> shouldRedirectProvider() {
        return Stream.of(
                Args.builder().featureFlagEnabled(false).expectedResult(false).build(),
                // Download request from cache proxy should not trigger another redirect
                Args.builder().expectedResult(false).build().setHeadersRedirectedFromCacheProxy(),
                // Null redirect type is not supported
                Args.builder().redirectType(null).expectedResult(false).build(),
                // Resource not found
                Args.builder().found(false).expectedResult(false).build(),
                // Do not redirect HEAD requests
                Args.builder().head(true).expectedResult(false).build(),
                Args.builder().remote(true).storeArtifactsLocally(false).expectedResult(false).build(),
                // Do not redirect expirable resources
                Args.builder().remote(true).storeArtifactsLocally(true).expirable(true).expectedResult(false).build(),
                Args.builder().expirable(true).expectedResult(false).build(),
                // Do not redirect range requests
                Args.builder().range(true).expectedResult(false).build(),
                // Do not redirect when force-get-stream is set (internal package manager requests)
                Args.builder().queryParams(Map.of(PARAM_FORCE_GET_STREAM, "true")).expectedResult(false).build(),
                // No header to use controlled cache set
                Args.builder().expectedResult(false).build().setNoHeadersToUseControlledCache(),
                // Should redirect (positive path) - all redirect types
                Args.builder().redirectType(RedirectType.DIRECT).expectedResult(true).build(),
                Args.builder().redirectType(RedirectType.S3).expectedResult(true).build(),
                Args.builder().redirectType(RedirectType.CLOUD_FRONT).expectedResult(true).build(),
                Args.builder().redirectType(RedirectType.GCS).expectedResult(true).build(),
                Args.builder().redirectType(RedirectType.ABS).expectedResult(true).build(),
                Args.builder().redirectType(RedirectType.PDN).expectedResult(true).build(),
                Args.builder().remote(true).storeArtifactsLocally(true).expectedResult(true).build()
        );
    }

    @Builder
    @Getter
    @ToString
    private static class Args {
        // Default values allow positive path

        @Builder.Default
        private boolean featureFlagEnabled = true;
        @Builder.Default
        private Map<String, String> headers =
                Map.of(X_JFROG_CONTROLLED_CACHE_REQUEST, JFROG_CONTROLLED_CACHE_VALIDATE_VALUE);
        @Builder.Default
        private Map<String, String> queryParams = Map.of();
        @Builder.Default
        private RedirectType redirectType = RedirectType.DIRECT;
        @Builder.Default
        private boolean found = true;
        @Builder.Default
        private boolean head = false;
        @Builder.Default
        private boolean remote = false;
        @Builder.Default
        private boolean storeArtifactsLocally = false;
        @Builder.Default
        private boolean expirable = false;
        @Builder.Default
        private boolean range = false;
        private boolean expectedResult;

        public Args setNoHeadersToUseControlledCache() {
            this.headers = Map.of();
            return this;
        }

        public Args setHeadersRedirectedFromCacheProxy() {
            this.headers = Map.of(X_JFROG_CONTROLLED_CACHE_REQUEST, JFROG_CONTROLLED_CACHE_DOWNLOAD_VALUE);
            return this;
        }
    }
}