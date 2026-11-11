package org.artifactory.util.bearer;

import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.artifactory.addon.ansible.AnsibleAuthHelper;
import org.artifactory.addon.ansible.SsoRemoteTokenProvider;
import org.artifactory.addon.common.DefaultRemoteTokenProvider;
import org.artifactory.addon.docker.rest.DockerRemoteTokenProvider;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Nir
 */
public class ArtifactoryBearerSchemeTest extends ArtifactoryHomeBoundJUnitTest {
    private static final String DEFAULT_PROVIDER_TOKEN = "default-token";
    private static final String DOCKER_PROVIDER_TOKEN = "docker-token";
    private static final String ANSIBLE_PROVIDER_TOKEN = "ansible-token";

    @Mock
    private DefaultRemoteTokenProvider defaultRemoteTokenProvider;
    @Mock
    private DockerRemoteTokenProvider dockerRemoteTokenProvider;
    @Mock
    private SsoRemoteTokenProvider ssoRemoteTokenProvider;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    AutoCloseable autoCloseable;

    @BeforeEach
    public void setUp() throws Exception {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(defaultRemoteTokenProvider.getToken(any(), anyString(), anyString(), anyString())).thenReturn(
                DEFAULT_PROVIDER_TOKEN);
        when(dockerRemoteTokenProvider.getToken(any(), anyString(), anyString(), anyString())).thenReturn(
                DOCKER_PROVIDER_TOKEN);
        when(ssoRemoteTokenProvider.getToken(any(), anyString(), anyString(), anyString())).thenReturn(
                ANSIBLE_PROVIDER_TOKEN);
        when(artifactoryContext.beanForType(DefaultRemoteTokenProvider.class)).thenReturn(defaultRemoteTokenProvider);
        when(artifactoryContext.beanForType(DockerRemoteTokenProvider.class)).thenReturn(dockerRemoteTokenProvider);
        when(artifactoryContext.beanForType(SsoRemoteTokenProvider.class)).thenReturn(ssoRemoteTokenProvider);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        clearInvocations(defaultRemoteTokenProvider, dockerRemoteTokenProvider, ssoRemoteTokenProvider);
        autoCloseable.close();
    }


    @ParameterizedTest
    @MethodSource("dockerPreemptiveAuthScenarios")
    void getTokenFromDockerRepoTest(boolean preemptiveAuthEnabled, boolean isSmartRepo, boolean hasCredentials) {
        String repoKey = "dockerRepo";
        setStringSystemProperty(ConstantValues.dockerPreemptiveAuthOnSmartReposEnabled,
                String.valueOf(preemptiveAuthEnabled));
        mockRemoteRepoConfig(repoKey, isSmartRepo, hasCredentials);
        HttpRequest request = mockHttpRequest();

        ArtifactoryBearerScheme scheme = new ArtifactoryBearerScheme(repoKey, RepoType.Docker);
        String token = scheme.getToken(null, request, null);

        assertNotNull(token);
        boolean expectDefaultProvider = preemptiveAuthEnabled && isSmartRepo && hasCredentials;
        if (expectDefaultProvider) {
            assertEquals(DEFAULT_PROVIDER_TOKEN, token);
            verify(defaultRemoteTokenProvider, times(1)).getToken(any(), anyString(), anyString(), anyString());
            verifyNoInteractions(dockerRemoteTokenProvider);
        } else {
            assertEquals(DOCKER_PROVIDER_TOKEN, token);
            verify(dockerRemoteTokenProvider, times(1)).getToken(any(), anyString(), anyString(), anyString());
            verifyNoInteractions(defaultRemoteTokenProvider);
        }
    }

    @Test
    void getTokenFromNpmRepoTest() {
        String repoKey = "npmRepo";
        RepoType repoType = RepoType.Npm;
        HttpRequest request = mockHttpRequest();
        ArtifactoryBearerScheme scheme = new ArtifactoryBearerScheme(repoKey, repoType);
        assertNotNull(scheme);

        String token = scheme.getToken(null, request, null);
        assertNotNull(token);
        assertEquals(DEFAULT_PROVIDER_TOKEN, token);

        verify(defaultRemoteTokenProvider, times(1)).getToken(any(), anyString(), anyString(), anyString());
        verifyNoInteractions(dockerRemoteTokenProvider);
    }

    private HttpRequest mockHttpRequest() {
        HttpRequest request = mock(HttpRequest.class);
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getMethod()).thenReturn("GET");
        when(requestLine.getUri()).thenReturn("foo/bar");
        when(request.getRequestLine()).thenReturn(requestLine);
        return request;
    }

    private void mockRemoteRepoConfig(String repoKey, boolean isSmartRepo, boolean hasCredentials) {
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.isSmart()).thenReturn(isSmartRepo);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUsername()).thenReturn(hasCredentials ? "user" : null);
        when(repoTypeConfig.getPassword()).thenReturn(null);
        when(repositoryService.getRemoteRepoConfigByKey(eq(repoKey))).thenReturn(remoteRepoConfig);
    }

    @Test
    void testGetTokenFromDockerRepo() {
        String repoKey = "dockerRepo";
        setStringSystemProperty(ConstantValues.dockerPreemptiveAuthOnSmartReposEnabled, "true");
        RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);
        when(remoteRepoConfig.isSmart()).thenReturn(true);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepoConfig.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUsername()).thenReturn(null);
        when(repoTypeConfig.getPassword()).thenReturn("api-token");
        when(repositoryService.getRemoteRepoConfigByKey(eq(repoKey))).thenReturn(remoteRepoConfig);
        HttpRequest request = mockHttpRequest();

        ArtifactoryBearerScheme scheme = new ArtifactoryBearerScheme(repoKey, RepoType.Docker);
        String token = scheme.getToken(null, request, null);

        assertNotNull(token);
        assertEquals(DEFAULT_PROVIDER_TOKEN, token);
        verify(defaultRemoteTokenProvider, times(1)).getToken(any(), anyString(), anyString(), anyString());
        verifyNoInteractions(dockerRemoteTokenProvider);
    }

    /**
     * (preemptiveAuthEnabled, isSmartRepo, hasCredentials).
     * Default provider only when all three are true; otherwise Docker provider handles 401 flow.
     */
    private static Stream<Arguments> dockerPreemptiveAuthScenarios() {
        return Stream.of(
                Arguments.of(true, true, true),   // only case that uses default provider
                Arguments.of(true, true, false), // no credentials -> docker provider
                Arguments.of(true, false, true), // not smart -> docker provider
                Arguments.of(true, false, false),
                Arguments.of(false, true, true), // flag off -> docker provider
                Arguments.of(false, true, false),
                Arguments.of(false, false, true),
                Arguments.of(false, false, false)
        );
    }

    @Test
    void getTokenFromAnsibleRepoUsesAnsibleProviderWhenSsoEnabled() {
        String repoKey = "ansibleRepo";
        HttpRequest request = mockHttpRequest();

        try (MockedStatic<AnsibleAuthHelper> mockedHelper = org.mockito.Mockito.mockStatic(AnsibleAuthHelper.class)) {
            mockedHelper.when(() -> AnsibleAuthHelper.shouldUseSsoAuth(repoKey)).thenReturn(true);

            ArtifactoryBearerScheme scheme = new ArtifactoryBearerScheme(repoKey, RepoType.Ansible);
            String token = scheme.getToken(null, request, null);

            assertNotNull(token);
            assertEquals(ANSIBLE_PROVIDER_TOKEN, token);
            verify(ssoRemoteTokenProvider, times(1)).getToken(any(), anyString(), anyString(), anyString());
            verifyNoInteractions(defaultRemoteTokenProvider);
        }
    }

    @Test
    void getTokenFromAnsibleRepoUsesDefaultProviderWhenSsoDisabled() {
        String repoKey = "ansibleRepo";
        HttpRequest request = mockHttpRequest();

        try (MockedStatic<AnsibleAuthHelper> mockedHelper = org.mockito.Mockito.mockStatic(AnsibleAuthHelper.class)) {
            mockedHelper.when(() -> AnsibleAuthHelper.shouldUseSsoAuth(repoKey)).thenReturn(false);

            ArtifactoryBearerScheme scheme = new ArtifactoryBearerScheme(repoKey, RepoType.Ansible);
            String token = scheme.getToken(null, request, null);

            assertNotNull(token);
            assertEquals(DEFAULT_PROVIDER_TOKEN, token);
            verify(defaultRemoteTokenProvider, times(1)).getToken(any(), anyString(), anyString(), anyString());
            verifyNoInteractions(ssoRemoteTokenProvider);
        }
    }
}

