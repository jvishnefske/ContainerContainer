package org.artifactory.addon.docker;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.docker.rest.DockerRemoteTokenProvider;
import org.artifactory.addon.docker.rest.DockerTokenCacheKey;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DockerRemoteTokenProviderTest {

    private static final String REPO_KEY = "navops-remote";
    private static final String REALM = "https://registry.navops.io/oauth2/token";

    @Mock
    private ArtifactoryContext mockContext;
    @Mock
    private AddonsManager mockAddonsManager;
    @Mock
    private DockerAddon mockDockerAddon;

    private MockedStatic<ContextHelper> contextHelperMock;
    private DockerRemoteTokenProvider provider;

    @BeforeEach
    void setUp() {
        contextHelperMock = mockStatic(ContextHelper.class);
        contextHelperMock.when(ContextHelper::get).thenReturn(mockContext);
        lenient().when(mockContext.beanForType(AddonsManager.class)).thenReturn(mockAddonsManager);
        lenient().when(mockAddonsManager.addonByType(DockerAddon.class)).thenReturn(mockDockerAddon);

        ArtifactoryHome.bind(new ArtifactoryHomeStub(getClass().getName()));
        provider = new DockerRemoteTokenProvider();
        provider.initTokensCache();
    }

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
        if (contextHelperMock != null) {
            contextHelperMock.close();
        }
    }

    @ParameterizedTest(name = "method={0} sets scope {1}")
    @CsvSource({
            "HEAD,'repository:nats:pull'",
            "GET,'repository:nats:pull'",
            "PUT,'repository:nats:push'",
            "head,'repository:nats:pull'",
            "get,'repository:nats:pull'",
            "put,'repository:nats:push'"
    })
    void getTokenSetsScopeForMethod(String method, String expectedScope) {
        when(mockDockerAddon.fetchDockerAuthToken(org.mockito.ArgumentMatchers.any())).thenReturn("test-token");

        Map<String, String> challengeParams = new HashMap<>();
        challengeParams.put("realm", REALM);

        String token = provider.getToken(challengeParams, method, "/v2/nats/manifests/2025.2.5", REPO_KEY);

        assertEquals("test-token", token);
        ArgumentCaptor<DockerTokenCacheKey> keyCaptor = ArgumentCaptor.forClass(DockerTokenCacheKey.class);
        verify(mockDockerAddon).fetchDockerAuthToken(keyCaptor.capture());
        assertEquals(expectedScope, keyCaptor.getValue().getScope());
    }

    @ParameterizedTest(name = "GET {0} -> scope {1}")
    @CsvSource({
            "'/v2/library/nginx/manifests/latest','repository:library/nginx:pull'",
            "'/v2/library/a/b/c/nginx/manifests/latest','repository:library/a/b/c/nginx:pull'",
            "'/v2/nats/blobs/sha256:4930dd079eb2129796528b15b7a879ce5c04ef5ebc4276e8afb04f26663fea28'," +
                    "'repository:nats:pull'",
            "'/v2/myimage/tags/list','repository:myimage:pull'",
            "'/v2/myimage/tags/list?n=100','repository:myimage:pull'",
            "'/v2/org/foo/bar/tags/list','repository:org/foo/bar:pull'",
            "'/v2/myimage/referrers/sha256:abc123','repository:myimage:pull'",
            "'/v2/org/myimage/referrers/sha256:abc123','repository:org/myimage:pull'",
            "'/v2/myimage/referrers/sha256:abc123?artifactType=application/vnd.example','repository:myimage:pull'",
            "'/v2/org/manifests/service/manifests/latest','repository:org/manifests/service:pull'",
            // URI whose last segment is '_catalog' but is NOT the catalog endpoint — must not get catalog scope
            "'/v2/myimage/manifests/_catalog','repository:myimage:pull'"
    })
    void getTokenWithGetUriSetsCorrectScope(String uri, String expectedScope) {
        when(mockDockerAddon.fetchDockerAuthToken(org.mockito.ArgumentMatchers.any())).thenReturn("test-token");

        Map<String, String> challengeParams = new HashMap<>();
        challengeParams.put("realm", REALM);

        String token = provider.getToken(challengeParams, "GET", uri, REPO_KEY);

        assertEquals("test-token", token);
        ArgumentCaptor<DockerTokenCacheKey> keyCaptor = ArgumentCaptor.forClass(DockerTokenCacheKey.class);
        verify(mockDockerAddon).fetchDockerAuthToken(keyCaptor.capture());
        assertEquals(expectedScope, keyCaptor.getValue().getScope());
    }

    @ParameterizedTest(name = "catalog URI {0} method={1} -> scope {2}")
    @CsvSource({
            "'/v2/_catalog','GET','registry:catalog:*'",
            "'/v2/_catalog','HEAD','registry:catalog:*'",
            "'/v2/_catalog?n=100','GET','registry:catalog:*'",
            "'/v2/_catalog?n=50&last=alpine','GET','registry:catalog:*'",
            "'/v2/_catalog/','GET','registry:catalog:*'",
            "'/v2/_catalog/?n=100','GET','registry:catalog:*'"
    })
    void getTokenWithCatalogUriSetsCatalogScope(String uri, String method, String expectedScope) {
        when(mockDockerAddon.fetchDockerAuthToken(org.mockito.ArgumentMatchers.any())).thenReturn("test-token");

        Map<String, String> challengeParams = new HashMap<>();
        challengeParams.put("realm", REALM);

        String token = provider.getToken(challengeParams, method, uri, REPO_KEY);

        assertEquals("test-token", token);
        ArgumentCaptor<DockerTokenCacheKey> keyCaptor = ArgumentCaptor.forClass(DockerTokenCacheKey.class);
        verify(mockDockerAddon).fetchDockerAuthToken(keyCaptor.capture());
        assertEquals(expectedScope, keyCaptor.getValue().getScope());
    }

    @ParameterizedTest(name = "method={0} uri={1} -> no scope set")
    @CsvSource(nullValues = "null", value = {
            "PUT,'/v2/_catalog'",
            "null,'/v2/myimage/manifests/latest'",
            "null,'/v2/_catalog'",
            "DELETE,'/v2/myimage/manifests/latest'",
            "GET,'/v1/some/path'"
    })
    void getTokenWhenScopeCannotBeDeterminedSetsNoScope(String method, String uri) {
        when(mockDockerAddon.fetchDockerAuthToken(org.mockito.ArgumentMatchers.any())).thenReturn("test-token");

        Map<String, String> challengeParams = new HashMap<>();
        challengeParams.put("realm", REALM);

        provider.getToken(challengeParams, method, uri, REPO_KEY);

        ArgumentCaptor<DockerTokenCacheKey> keyCaptor = ArgumentCaptor.forClass(DockerTokenCacheKey.class);
        verify(mockDockerAddon).fetchDockerAuthToken(keyCaptor.capture());
        assertNull(keyCaptor.getValue().getScope());
    }

    @Test
    void getTokenWithPingUriSetsEmptyRepoNameScope() {
        when(mockDockerAddon.fetchDockerAuthToken(org.mockito.ArgumentMatchers.any())).thenReturn("test-token");

        Map<String, String> challengeParams = new HashMap<>();
        challengeParams.put("realm", REALM);

        provider.getToken(challengeParams, "GET", "/v2/", REPO_KEY);

        ArgumentCaptor<DockerTokenCacheKey> keyCaptor = ArgumentCaptor.forClass(DockerTokenCacheKey.class);
        verify(mockDockerAddon).fetchDockerAuthToken(keyCaptor.capture());
        assertEquals("repository::pull", keyCaptor.getValue().getScope());
    }

    @Test
    void getTokenWithScopeAlreadyInChallengePreservesExistingScope() {
        when(mockDockerAddon.fetchDockerAuthToken(org.mockito.ArgumentMatchers.any())).thenReturn("test-token");

        Map<String, String> challengeParams = new HashMap<>();
        challengeParams.put("realm", REALM);
        challengeParams.put("scope", "repository:myimage:pull,push");

        provider.getToken(challengeParams, "GET", "/v2/myimage/manifests/latest", REPO_KEY);

        ArgumentCaptor<DockerTokenCacheKey> keyCaptor = ArgumentCaptor.forClass(DockerTokenCacheKey.class);
        verify(mockDockerAddon).fetchDockerAuthToken(keyCaptor.capture());
        assertEquals("repository:myimage:pull,push", keyCaptor.getValue().getScope());
    }

    @Test
    void getTokenAutoPopulatesServiceFromRealmHost() {
        when(mockDockerAddon.fetchDockerAuthToken(org.mockito.ArgumentMatchers.any())).thenReturn("test-token");

        Map<String, String> challengeParams = new HashMap<>();
        challengeParams.put("realm", "https://auth.docker.io/token");

        provider.getToken(challengeParams, "GET", "/v2/library/alpine/manifests/latest", REPO_KEY);

        ArgumentCaptor<DockerTokenCacheKey> keyCaptor = ArgumentCaptor.forClass(DockerTokenCacheKey.class);
        verify(mockDockerAddon).fetchDockerAuthToken(keyCaptor.capture());
        assertEquals("auth.docker.io", keyCaptor.getValue().getService());
    }

}
