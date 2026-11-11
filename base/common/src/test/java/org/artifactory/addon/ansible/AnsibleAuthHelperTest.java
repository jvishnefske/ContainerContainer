package org.artifactory.addon.ansible;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnsibleAuthHelperTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "ansible-remote";
    private static final String AUTH_URL =
            "https://sso.redhat.com/auth/realms/redhat-external/protocol/openid-connect/token";
    private static final String PASSWORD = "test-refresh-token";

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private RepositoryService repositoryService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(RepositoryService.class))
                .thenReturn(repositoryService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void shouldUseSsoAuthReturnsFalseWhenRepoKeyIsNull() {
        assertFalse(AnsibleAuthHelper.shouldUseSsoAuth(null));
    }

    @Test
    void shouldUseSsoAuthReturnsFalseWhenConfigIsNull() {
        when(repositoryService.getRemoteRepoConfigByKey(any())).thenReturn(null);

        assertFalse(AnsibleAuthHelper.shouldUseSsoAuth(REPO_KEY));
    }

    @Test
    void shouldUseSsoAuthReturnsTrueWhenAuthUrlAndPasswordConfigured() {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.getPackageTypeConfig()).thenReturn(Map.of("authUrl", AUTH_URL));
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getPassword()).thenReturn(PASSWORD);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        assertTrue(AnsibleAuthHelper.shouldUseSsoAuth(REPO_KEY));
    }

    @Test
    void shouldUseSsoAuthReturnsFalseWhenAuthUrlMissing() {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.getPackageTypeConfig()).thenReturn(Map.of());
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getPassword()).thenReturn(PASSWORD);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        assertFalse(AnsibleAuthHelper.shouldUseSsoAuth(REPO_KEY));
    }

    @Test
    void shouldUseSsoAuthReturnsFalseWhenPasswordMissing() {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.getPackageTypeConfig()).thenReturn(Map.of("authUrl", AUTH_URL));
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getPassword()).thenReturn(null);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        assertFalse(AnsibleAuthHelper.shouldUseSsoAuth(REPO_KEY));
    }

    @Test
    void shouldUseSsoAuthReturnsFalseWhenAuthUrlIsBlank() {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.getPackageTypeConfig()).thenReturn(Map.of("authUrl", "  "));
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getPassword()).thenReturn(PASSWORD);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        assertFalse(AnsibleAuthHelper.shouldUseSsoAuth(REPO_KEY));
    }

    @Test
    void shouldUseSsoAuthReturnsFalseWhenPasswordIsBlank() {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.getPackageTypeConfig()).thenReturn(Map.of("authUrl", AUTH_URL));
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(repoTypeConfig.getPassword()).thenReturn("  ");
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        assertFalse(AnsibleAuthHelper.shouldUseSsoAuth(REPO_KEY));
    }

    @Test
    void shouldUseSsoAuthReturnsFalseWhenPackageTypeConfigIsNull() {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        assertFalse(AnsibleAuthHelper.shouldUseSsoAuth(REPO_KEY));
    }
}
