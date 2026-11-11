package org.artifactory.addon.docker;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
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
import org.mockito.MockitoAnnotations;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DockerPreemptiveAuthHelperTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_KEY = "test-repo";

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private RepositoryService repositoryService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryContext.beanForType(RepositoryService.class)).thenReturn(repositoryService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void shouldUsePreemptiveAuthWhenConfigIsNull() {
        setStringSystemProperty(ConstantValues.dockerPreemptiveAuthOnSmartReposEnabled, "true");
        when(repositoryService.getRemoteRepoConfigByKey(any())).thenReturn(null);

        boolean result = DockerPreemptiveAuthHelper.shouldUsePreemptiveAuth(REPO_KEY);

        assertFalse(result);
    }

    @ParameterizedTest
    @MethodSource("flagSmartCredentialsScenarios")
    void shouldUsePreemptiveAuthWhenFlagSmartAndCredentials(
            boolean flagEnabled, boolean isSmartRepo, boolean hasCredentials, boolean expected) {
        setStringSystemProperty(ConstantValues.dockerPreemptiveAuthOnSmartReposEnabled,
                String.valueOf(flagEnabled));
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.isSmart()).thenReturn(isSmartRepo);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUsername()).thenReturn(hasCredentials ? "user" : null);
        when(repoTypeConfig.getPassword()).thenReturn(null);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        boolean result = DockerPreemptiveAuthHelper.shouldUsePreemptiveAuth(REPO_KEY);

        assertEquals(expected, result);
    }

    @Test
    void shouldUsePreemptiveAuthWhenPasswordOnlyCredentials() {
        setStringSystemProperty(ConstantValues.dockerPreemptiveAuthOnSmartReposEnabled, "true");
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.isSmart()).thenReturn(true);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(config.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUsername()).thenReturn(null);
        when(repoTypeConfig.getPassword()).thenReturn("api-token");
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        boolean result = DockerPreemptiveAuthHelper.shouldUsePreemptiveAuth(REPO_KEY);

        assertTrue(result);
    }

    @Test
    void shouldUsePreemptiveAuthWhenRepoTypeConfigIsNull() {
        setStringSystemProperty(ConstantValues.dockerPreemptiveAuthOnSmartReposEnabled, "true");
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        when(config.isSmart()).thenReturn(true);
        when(config.getRepoTypeConfig()).thenReturn(null);
        when(repositoryService.getRemoteRepoConfigByKey(eq(REPO_KEY))).thenReturn(config);

        boolean result = DockerPreemptiveAuthHelper.shouldUsePreemptiveAuth(REPO_KEY);

        assertFalse(result);
    }

    /**
     * (flagEnabled, isSmartRepo, hasCredentials, expected).
     * Preemptive auth is true only when all three conditions are true.
     */
    private static Stream<Arguments> flagSmartCredentialsScenarios() {
        return Stream.of(
                Arguments.of(true, true, true, true),
                Arguments.of(true, true, false, false),
                Arguments.of(true, false, true, false),
                Arguments.of(true, false, false, false),
                Arguments.of(false, true, true, false),
                Arguments.of(false, true, false, false),
                Arguments.of(false, false, true, false),
                Arguments.of(false, false, false, false)
        );
    }
}
