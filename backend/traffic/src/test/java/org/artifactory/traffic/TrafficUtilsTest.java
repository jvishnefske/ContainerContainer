package org.artifactory.traffic;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.Request;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.traffic.TrafficEntry.TrafficActor;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.ServiceType;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.artifactory.api.request.ArtifactoryHeaders.ARTIFACTORY_ARCHIVE;
import static org.artifactory.api.request.ArtifactoryHeaders.PARAM_REPLICATION_DOWNLOAD_REQUEST;
import static org.artifactory.common.ConstantValues.trafficReplicationAddActorEnabled;
import static org.artifactory.traffic.TrafficUtils.getTrafficActor;
import static org.artifactory.traffic.TrafficUtils.getTrafficActorFromRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class TrafficUtilsTest {

    @Mock
    private ArtifactorySystemProperties properties;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private AutoCloseable closable;

    @BeforeEach
    void setup() {
        closable = openMocks(this);
        ArtifactoryHome.bind(artifactoryHome);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        closable.close();
    }

    static Stream<Arguments> subjects() {
        return Stream.of(
                Arguments.of(null, TrafficActor.USER),
                Arguments.of("", TrafficActor.USER),
                Arguments.of("@", TrafficActor.USER),
                Arguments.of("jfxr@", TrafficActor.USER),
                Arguments.of("@sded", TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.ACCESS), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.ACCESS_LEGACY), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.ARTIFACTORY), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.ARTIFACTORY_LEGACY), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.MISSION_CONTROL), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.XRAY), TrafficActor.XRAY),
                Arguments.of(getFormattedName(ServiceType.XRAY_INDEXER), TrafficActor.XRAY),
                Arguments.of(getFormattedName(ServiceType.XRAY_PERSIST), TrafficActor.XRAY),
                Arguments.of(getFormattedName(ServiceType.XRAY_ANALYSIS), TrafficActor.XRAY),
                Arguments.of(getFormattedName(ServiceType.DISTRIBUTION), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.FRONTEND), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.EVENT), TrafficActor.USER),
                Arguments.of(getFormattedName(ServiceType.PIPELINES), TrafficActor.PIPELINES),
                Arguments.of(getFormattedName(ServiceType.REPLICATOR), TrafficActor.REPLICATOR),
                Arguments.of(getFormattedName(ServiceType.OBSERVABILITY), TrafficActor.OBSERVABILITY),
                Arguments.of("jfxr@cve_applicability_12345678", TrafficActor.XRAY_REPETITIVE),
                Arguments.of("jfxr@actionable_analysis_12345678", TrafficActor.XRAY_CREDIT),
                Arguments.of("jfxr@extra_analysis_12345678", TrafficActor.XRAY_REPETITIVE)
        );
    }

    @ParameterizedTest
    @MethodSource("subjects")
    void testAccessToken(String subject, TrafficActor expected) {
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getSubject()).thenReturn(subject);
        when(authentication.getAccessToken()).thenReturn(token);
        TrafficActor actual = getTrafficActor(null, null, authentication);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("subjects")
    void testAccessTokenWithReplicationFlagEnable(String subject) {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(true);
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getSubject()).thenReturn(subject);
        when(authentication.getAccessToken()).thenReturn(token);
        Request request = mock(Request.class);
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn("true");

        TrafficActor actual = getTrafficActorFromRequest(null, null, authentication, request);
        assertEquals(TrafficActor.REPLICATION, actual);
    }

    static Stream<Arguments> scope() {
        return Stream.of(
                Arguments.of("artifactory-cold", TrafficActor.ARCHIVE),
                Arguments.of("artifactory-regular", TrafficActor.USER)
        );
    }

    @ParameterizedTest
    @MethodSource("scope")
    void testActorIsArchive(String scope, TrafficActor expected) {
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(List.of(scope));
        when(authentication.getAccessToken()).thenReturn(token);
        TrafficActor actual = getTrafficActor(ARTIFACTORY_ARCHIVE, null, authentication);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("scope")
    void testActorIsArchiveWithReplicationFlagEnable(String scope) {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(true);
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getScope()).thenReturn(List.of(scope));
        when(authentication.getAccessToken()).thenReturn(token);
        Request request = mock(Request.class);
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn("true");

        TrafficActor actual = getTrafficActorFromRequest(ARTIFACTORY_ARCHIVE, null, authentication, request);
        assertEquals(TrafficActor.REPLICATION, actual);
    }

    static Stream<Arguments> userdata() {
        return Stream.of(
                Arguments.of("", "", TrafficActor.USER),
                Arguments.of(null, null, TrafficActor.USER),
                Arguments.of("", "xray", TrafficActor.XRAY),
                Arguments.of("useragentxray", "", TrafficActor.XRAY),
                Arguments.of("useragentjfrog-replicator/", "", TrafficActor.REPLICATOR),
                Arguments.of("observability/", "_system_", TrafficActor.OBSERVABILITY),
                Arguments.of("observability/", "", TrafficActor.USER),
                Arguments.of("jfconnect/", "_system_", TrafficActor.JFCONNECT),
                Arguments.of("jfconnect/", "", TrafficActor.USER),
                Arguments.of("jflink/", "_system_", TrafficActor.JFLINK),
                Arguments.of("jflink/", "", TrafficActor.USER),
                Arguments.of("ba-billing-logshipping", "_system_", TrafficActor.BA_BILLING_LOGSHIPPING),
                Arguments.of("ba-billing-logshipping/", "_system_", TrafficActor.BA_BILLING_LOGSHIPPING),
                Arguments.of("ba-billing-logshipping/", "", TrafficActor.USER),
                Arguments.of("noise", "", TrafficActor.USER),
                Arguments.of(null, "", TrafficActor.USER),
                Arguments.of(ARTIFACTORY_ARCHIVE, "_system_", TrafficActor.ARCHIVE),
                Arguments.of(ARTIFACTORY_ARCHIVE, "_not_system_", TrafficActor.USER)
        );
    }

    @ParameterizedTest
    @MethodSource("userdata")
    void testUserData(String userAgent, String username, TrafficActor expected) {
        TrafficActor actual = getTrafficActor(userAgent, username, null);
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("userdata")
    void testUserDataWithReplicationFlagEnable(String userAgent, String username) {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(true);
        Request request = mock(Request.class);
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn("true");
        TrafficActor actual = getTrafficActorFromRequest(userAgent, username, null, request);
        assertEquals(TrafficActor.REPLICATION, actual);
    }

    static Stream<Arguments> testGetTrafficActorV2DataProvider() {
        AccessTokenAuthentication jfxrToken = mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtJfxrToken = mock(JwtAccessToken.class);
        doReturn(jwtJfxrToken).when(jfxrToken).getAccessToken();
        doReturn("jfxr@cool").when(jwtJfxrToken).getSubject();

        AccessTokenAuthentication jfpipToken = mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtJfpipToken = mock(JwtAccessToken.class);
        doReturn(jwtJfpipToken).when(jfpipToken).getAccessToken();
        doReturn("jfpip@cool").when(jwtJfpipToken).getSubject();

        AccessTokenAuthentication jfcoolToken = mock(AccessTokenAuthentication.class);
        JwtAccessToken jwtJfcoolToken = mock(JwtAccessToken.class);
        doReturn(jwtJfcoolToken).when(jfcoolToken).getAccessToken();
        doReturn("jfcool@cool").when(jwtJfcoolToken).getSubject(); // No such service

        return Stream.of(
                Arguments.of("", false, null, TrafficActor.USER),
                Arguments.of("cool", false, null, TrafficActor.USER),
                Arguments.of("cool", true, jfpipToken, TrafficActor.PIPELINES),
                Arguments.of("cool", true, jfxrToken, TrafficActor.XRAY),
                Arguments.of("cool", true, jfcoolToken, TrafficActor.XRAY),
                Arguments.of("jfrog-replicator/", false, null, TrafficActor.REPLICATOR),
                Arguments.of("observability", false, null, TrafficActor.OBSERVABILITY),
                Arguments.of("observability/", false, null, TrafficActor.OBSERVABILITY),
                Arguments.of("ba-billing-logshipping", false, null, TrafficActor.BA_BILLING_LOGSHIPPING),
                Arguments.of("ba-billing-logshipping/", false, null, TrafficActor.BA_BILLING_LOGSHIPPING)
        );
    }

    @ParameterizedTest
    @MethodSource("testGetTrafficActorV2DataProvider")
    void testGetTrafficActorV2(String identifier, boolean xrayAuthenticationUser, Authentication authentication,
            TrafficActor expectedActor) {
        TrafficActor trafficActor = getTrafficActor(identifier, xrayAuthenticationUser, authentication);
        assertEquals(expectedActor, trafficActor);
    }

    @Test
    void testSmartRemoteActorEnabled() {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(false);

        // Mock request and repo path
        Request request = mock(Request.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("smart-remote-repo");
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn(null);

        // Mock ContextHelper and RepositoryConfigService
        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
            RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
            RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);

            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
            when(repositoryConfigService.getRemoteRepoConfigByKey("smart-remote-repo")).thenReturn(remoteRepoConfig);
            when(remoteRepoConfig.isSmart()).thenReturn(true);

            TrafficActor actual = getTrafficActorFromRequest("test-agent", "test-user", null, request);
            assertEquals(TrafficActor.SMART_REMOTE, actual);
        }
    }

    @Test
    void testNonSmartRemoteRepo() {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(false);

        Request request = mock(Request.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("regular-remote-repo");
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn(null);

        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
            RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
            RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);

            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
            when(repositoryConfigService.getRemoteRepoConfigByKey("regular-remote-repo")).thenReturn(remoteRepoConfig);
            when(remoteRepoConfig.isSmart()).thenReturn(false);

            TrafficActor actual = getTrafficActorFromRequest("test-agent", "test-user", null, request);
            assertEquals(TrafficActor.USER, actual);
        }
    }

    @Test
    void testNullRemoteRepoConfig() {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(false);

        Request request = mock(Request.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("non-existent-repo");
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn(null);

        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
            RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);

            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
            when(repositoryConfigService.getRemoteRepoConfigByKey("non-existent-repo")).thenReturn(null);

            TrafficActor actual = getTrafficActorFromRequest("test-agent", "test-user", null, request);
            assertEquals(TrafficActor.USER, actual);
        }
    }

    @Test
    void testReplicationFlagTakesPrecedenceOverSmartRemote() {
        // Test that replication flag takes precedence over smart remote detection
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(true);

        Request request = mock(Request.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("smart-remote-repo");
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn("true");

        // Even though the repo could be smart, replication should take precedence
        TrafficActor actual = getTrafficActorFromRequest("test-agent", "test-user", null, request);
        assertEquals(TrafficActor.REPLICATION, actual);
    }

    @Test
    void testSmartRemoteWithXrayAuthentication() {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(false);

        Request request = mock(Request.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("smart-remote-repo");
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn(null);

        // Setup Xray authentication
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getSubject()).thenReturn(getFormattedName(ServiceType.XRAY));
        when(authentication.getAccessToken()).thenReturn(token);

        try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
            RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
            RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);

            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
            when(repositoryConfigService.getRemoteRepoConfigByKey("smart-remote-repo")).thenReturn(remoteRepoConfig);
            when(remoteRepoConfig.isSmart()).thenReturn(true);

            TrafficActor actual = getTrafficActorFromRequest("test-agent", "test-user", authentication, request);
            assertEquals(TrafficActor.SMART_REMOTE, actual);
        }
    }

    static Stream<Arguments> smartRemoteScenarios() {
        return Stream.of(
                Arguments.of(false, null, true, TrafficActor.SMART_REMOTE),
                Arguments.of(false, null, false, TrafficActor.USER),
                Arguments.of(true, "true", true, TrafficActor.REPLICATION),
                Arguments.of(true, "true", false, TrafficActor.REPLICATION),
                Arguments.of(true, "false", true, TrafficActor.SMART_REMOTE),
                Arguments.of(true, null, true, TrafficActor.SMART_REMOTE)
        );
    }

    @ParameterizedTest
    @MethodSource("smartRemoteScenarios")
    void testSmartRemoteScenarios(boolean replicationEnabled, String replicationParam, boolean isSmartRepo,
            TrafficActor expectedActor) {
        when(properties.getBooleanProperty(trafficReplicationAddActorEnabled)).thenReturn(replicationEnabled);

        Request request = mock(Request.class);
        RepoPath repoPath = mock(RepoPath.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        when(repoPath.getRepoKey()).thenReturn("test-repo");
        when(request.getParameter(PARAM_REPLICATION_DOWNLOAD_REQUEST)).thenReturn(replicationParam);

        boolean replicationRequest = replicationEnabled &&
                Boolean.parseBoolean(replicationParam != null ? replicationParam : "false");

        if (replicationRequest) {
            TrafficActor actual = getTrafficActorFromRequest("test-agent", "test-user", null, request);
            assertEquals(expectedActor, actual);
        } else {
            try (MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
                ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
                RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
                RemoteRepoConfig remoteRepoConfig = mock(RemoteRepoConfig.class);

                contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);
                when(artifactoryContext.beanForType(RepositoryConfigService.class)).thenReturn(repositoryConfigService);
                when(repositoryConfigService.getRemoteRepoConfigByKey("test-repo")).thenReturn(remoteRepoConfig);
                when(remoteRepoConfig.isSmart()).thenReturn(isSmartRepo);

                TrafficActor actual = getTrafficActorFromRequest("test-agent", "test-user", null, request);
                assertEquals(expectedActor, actual);
            }
        }
    }

    private static String getFormattedName(String serviceName) {
        return new ServiceId(serviceName, UUID.randomUUID().toString()).getFormattedName();
    }
}
