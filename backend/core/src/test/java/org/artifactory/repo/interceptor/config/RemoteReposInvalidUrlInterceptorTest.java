package org.artifactory.repo.interceptor.config;

import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.modle.AllowedRemoteUrlsConfig;
import org.artifactory.service.ProhibitedUrlsService;
import org.jfrog.common.JsonUtils;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class RemoteReposInvalidUrlInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String ALLOWLIST_JSON = """
            {
                "globallyAllowedUrls": {
                    "https://registry.npmjs.org": ["Npm"],
                    "https://repo1.maven.org": ["Maven", "Gradle"],
                    "https://registry-1.docker.io": ["Docker"]
                },
                "explicitlyDeniedUrlRoute": {
                    "https://nodejs.org/dist": ["Npm"],
                    "https://registry.npmjs.org/-/npm/v1/security/audits": ["Npm"]
                }
            }
            """;

    AutoCloseable closeable;
    private RemoteReposInvalidUrlInterceptor interceptor;
    @Mock
    private ProhibitedUrlsService prohibitedUrlsService;

    @BeforeEach
    public void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        interceptor = spy(new RemoteReposInvalidUrlInterceptor());
        interceptor.setProhibitedUrlsService(prohibitedUrlsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void shouldThrowWhenUrlNotAllowedOnCreate() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org/", RepoType.Maven, "npm-as-maven", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldThrowWhenTakingOfflineRepoOnlineWithInvalidUrl() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig oldConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org/", RepoType.Maven, "npm-as-maven", true);
        RemoteRepoConfig newConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org/", RepoType.Maven, "npm-as-maven", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_UPDATE, newConfig, oldConfig);
        assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldSkipValidationWhenUrlUnchangedAndStillOnline() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        RemoteRepoConfig oldConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org/", RepoType.Maven, "npm-as-maven", false);
        RemoteRepoConfig newConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org/", RepoType.Maven, "npm-as-maven", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_UPDATE, newConfig, oldConfig);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldNotThrowWhenUrlAllowedForPackageType() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org", RepoType.Npm, "npm-remote", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldAllowUnknownUrls() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://custom-registry.example.com", RepoType.Npm, "custom-npm", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldAllowWhenDryRunEvenIfBlocked() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(true).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org", RepoType.Docker, "npm-as-docker", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldAllowWhenConfigIsNull() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(null);
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org", RepoType.Docker, "npm-as-docker", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldNotThrowExceptionWhenNoRemoteRepos() {
        assertDoesNotThrow(() -> interceptor.onBeforeSave(Map.of()));
    }

    @Test
    void shouldThrowWhenExplicitlyDeniedUrlOnCreate() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://nodejs.org/dist", RepoType.Npm, "node-remote", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldAllowExplicitlyDeniedUrlForNonDeniedType() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://nodejs.org/dist", RepoType.Docker, "node-docker", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldAllowExplicitlyDeniedUrlWithExtraPath() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://nodejs.org/dist/v18", RepoType.Npm, "node-v18", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertDoesNotThrow(() -> interceptor.onBeforeSave(changes));
    }

    @Test
    void shouldThrowWhenDeniedRouteIsSubPathOfAllowedPrefix() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org/-/npm/v1/security/audits", RepoType.Npm, "npm-audits", false);
        Map<RepoDetailsType, List<RepoConfigChange>> changes = buildChanges(REPO_CREATE, repoConfig, null);
        assertThrows(ConfigurationException.class, () -> interceptor.onBeforeSave(changes));
    }

    @Test
    void testIsRemoteDownloadBlocked_explicitlyDenied() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://nodejs.org/dist", RepoType.Npm, "node-remote", false);
        assertFalse(interceptor.isRemoteDownloadAllowed(repoConfig, mock(RepoPath.class)));
    }

    @Test
    void testIsRemoteDownloadAllowed_explicitlyDeniedNonMatchingType() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://nodejs.org/dist", RepoType.Docker, "node-docker", false);
        assertTrue(interceptor.isRemoteDownloadAllowed(repoConfig, mock(RepoPath.class)));
    }

    @Test
    void testIsRemoteDownloadAllowed_blocked() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        doReturn(false).when(interceptor).isDryRun();
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org", RepoType.Docker, "npm-as-docker", false);
        assertFalse(interceptor.isRemoteDownloadAllowed(repoConfig, mock(RepoPath.class)));
    }

    @Test
    void testIsRemoteDownloadAllowed_allowed() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(buildConfig());
        RemoteRepoConfig repoConfig = mockRemoteRepoConfig(
                "https://registry.npmjs.org", RepoType.Npm, "npm-remote", false);
        assertTrue(interceptor.isRemoteDownloadAllowed(repoConfig, mock(RepoPath.class)));
    }

    @Test
    void testIsRemoteDownloadAllowed_nullConfig() {
        when(prohibitedUrlsService.getAllowedRemoteUrlsConfig()).thenReturn(null);
        assertTrue(interceptor.isRemoteDownloadAllowed(null, null));
    }

    @ParameterizedTest
    @MethodSource("provideRepoConfigsForUrlChange")
    void testIsUrlChange(RemoteRepoConfig oldRepoConfig, RemoteRepoConfig newRepoConfig, boolean expected) {
        RepoConfigChange change = mock(RepoConfigChange.class);
        when(change.getOldRepoConfig()).thenReturn(oldRepoConfig);
        when(change.getNewRepoConfig()).thenReturn(newRepoConfig);
        assertEquals(expected, interceptor.isUrlChange(change));
    }

    static Stream<Arguments> provideRepoConfigsForUrlChange() {
        RemoteRepoConfig repoA = mockWithUrl("http://url-a.com");
        RemoteRepoConfig repoB = mockWithUrl("http://url-b.com");
        RemoteRepoConfig repoNull = mockWithUrl(null);
        return Stream.of(
                Arguments.of(null, null, true),
                Arguments.of(null, repoA, true),
                Arguments.of(repoA, null, true),
                Arguments.of(repoNull, repoNull, false),
                Arguments.of(repoA, repoB, true),
                Arguments.of(repoA, repoA, false),
                Arguments.of(repoA, repoNull, true),
                Arguments.of(repoNull, repoA, true));
    }

    @ParameterizedTest
    @MethodSource("provideOfflineChangeCases")
    void testIsBeingTakenOnline(RemoteRepoConfig oldConfig, RemoteRepoConfig newConfig, boolean expected) {
        RepoConfigChange change = mock(RepoConfigChange.class);
        when(change.getOldRepoConfig()).thenReturn(oldConfig);
        when(change.getNewRepoConfig()).thenReturn(newConfig);
        assertEquals(expected, interceptor.isBeingTakenOnline(change));
    }

    static Stream<Arguments> provideOfflineChangeCases() {
        RemoteRepoConfig offline = mockWithOffline(true);
        RemoteRepoConfig online = mockWithOffline(false);
        return Stream.of(
                Arguments.of(offline, online, true),   // was offline, now online
                Arguments.of(online, online, false),   // was online, still online
                Arguments.of(offline, offline, false),  // was offline, still offline
                Arguments.of(online, offline, false),   // was online, now offline
                Arguments.of(null, online, false));     // null old (create)
    }

    private static RemoteRepoConfig mockWithUrl(String url) {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(typeConfig.getUrl()).thenReturn(url);
        when(config.getRepoTypeConfig()).thenReturn(typeConfig);
        return config;
    }

    private static RemoteRepoConfig mockWithOffline(boolean offline) {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(typeConfig.isOffline()).thenReturn(offline);
        when(config.getRepoTypeConfig()).thenReturn(typeConfig);
        return config;
    }

    private AllowedRemoteUrlsConfig buildConfig() {
        return JsonUtils.getInstance().readValue(ALLOWLIST_JSON, AllowedRemoteUrlsConfig.class);
    }

    private RemoteRepoConfig mockRemoteRepoConfig(String url, RepoType packageType, String key, boolean offline) {
        RemoteRepoConfig config = mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig typeConfig = mock(RemoteRepositoryRepoTypeConfig.class);
        when(typeConfig.getUrl()).thenReturn(url);
        when(typeConfig.isOffline()).thenReturn(offline);
        when(config.getRepoTypeConfig()).thenReturn(typeConfig);
        when(config.getPackageType()).thenReturn(packageType);
        when(config.getKey()).thenReturn(key);
        return config;
    }

    private Map<RepoDetailsType, List<RepoConfigChange>> buildChanges(
            RepoConfigChange.ConfigAction action, RemoteRepoConfig newConfig, RemoteRepoConfig oldConfig) {
        return Map.of(RepoDetailsType.REMOTE,
                List.of(new RepoConfigChange("repoKey", action, newConfig, oldConfig)));
    }
}
