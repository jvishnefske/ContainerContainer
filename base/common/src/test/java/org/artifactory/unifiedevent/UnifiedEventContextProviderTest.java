package org.artifactory.unifiedevent;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ha.HaNodeProperties;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.model.HeadersData;
import org.artifactory.unifiedevent.model.UserContextData;
import org.artifactory.unifiedevent.model.artifact.ArtifactData;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class UnifiedEventContextProviderTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String MOCK_SERVER_ID = "test-server-id";

    private SecurityService securityService;
    private RepositoryConfigService repositoryConfigService;
    private UnifiedEventContextProvider contextProvider;
    private AccessService accessService;

    @BeforeEach
    void setUp() {
        getOrCreateArtifactoryHomeStub().getHaNodeProperties().getProperties()
                .setProperty(HaNodeProperties.PROP_NODE_ID, MOCK_SERVER_ID);

        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        ArtifactoryContextThreadBinder.bind(context);

        securityService = mock(SecurityService.class);
        repositoryConfigService = mock(RepositoryConfigService.class);
        accessService = mock(AccessService.class);
        contextProvider = new UnifiedEventContextProvider(securityService, repositoryConfigService, accessService);
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testGenerateEventId() {
        String eventId = contextProvider.generateEventId();
        assertNotNull(eventId);
        assertEquals(36, eventId.length());
    }

    @Test
    void testGetCurrentTimestamp() {
        String timestamp = contextProvider.getCurrentTimestamp();
        assertNotNull(timestamp);
        // Verify it's an ISO-8601 timestamp format
        assert (timestamp.contains("T"));
        assert (timestamp.contains("Z"));
    }

    @Test
    void testGetDataSchema() {
        SupportedUnifiedEvents eventType = SupportedUnifiedEvents.BEFORE_DOWNLOAD;
        String dataSchema = contextProvider.getDataSchema(eventType);
        assertEquals(eventType.getId(), dataSchema);
    }

    @Test
    void testGetEventHeaders() {
        HeadersData headers = contextProvider.getEventHeaders();
        assertNotNull(headers);
    }

    @Test
    void testGetUserContextWithRegularAuthentication() {
        Authentication authentication = mock(Authentication.class);
        try (MockedStatic<org.artifactory.security.AuthenticationHelper> authHelperMock = mockStatic(
                org.artifactory.security.AuthenticationHelper.class)) {
            authHelperMock.when(org.artifactory.security.AuthenticationHelper::getAuthentication)
                    .thenReturn(authentication);

            when(securityService.currentUsername()).thenReturn("testUser");

            UserContextData userContext = contextProvider.getUserContext();

            assertNotNull(userContext);
            assertEquals("testUser", userContext.actor());
        }
    }

    @Test
    void testGetUserContextWithAccessTokenAuthentication() {
        AccessTokenAuthentication accessTokenAuth = mock(AccessTokenAuthentication.class);
        JwtAccessToken accessToken = mock(JwtAccessToken.class);

        try (MockedStatic<org.artifactory.security.AuthenticationHelper> authHelperMock = mockStatic(
                org.artifactory.security.AuthenticationHelper.class)) {
            authHelperMock.when(org.artifactory.security.AuthenticationHelper::getAuthentication)
                    .thenReturn(accessTokenAuth);

            when(securityService.currentUsername()).thenReturn("regularUser");
            when(accessTokenAuth.getAccessToken()).thenReturn(accessToken);
            when(accessToken.getSubject()).thenReturn("tokenSubject");

            UserContextData userContext = contextProvider.getUserContext();

            assertNotNull(userContext);
            assertEquals("tokenSubject", userContext.actor());
        }
    }

    @Test
    void testGetSubjectForArtifactData() {
        ArtifactData artifactData = mock(ArtifactData.class);
        when(artifactData.repoKey()).thenReturn("test-repo");
        when(artifactData.path()).thenReturn("path/to/artifact");

        String subject = contextProvider.getSubjectForArtifactData(artifactData);

        assertEquals("test-repo/path/to/artifact", subject);
    }

    @Test
    void testGetRepoTypeReturnsRepoType() {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repositoryConfigService.getRepoConfigByKey("some-repo")).thenReturn(repoConfig);
        when(repoConfig.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        RepoDetailsType repoType = contextProvider.getRepoType("some-repo");
        assertEquals(RepoDetailsType.LOCAL, repoType);
    }

    @Test
    void testGetRepoTypeWhenRepoConfigIsNull() {
        when(repositoryConfigService.getRepoConfigByKey("unknown-repo")).thenReturn(null);
        RepoDetailsType repoType = contextProvider.getRepoType("unknown-repo");
        assertNull(repoType);
    }

    @Test
    void testGetRepoTypeWhenExceptionIsThrown() {
        when(repositoryConfigService.getRepoConfigByKey("invalid-repo"))
                .thenThrow(new IllegalArgumentException("Invalid repo"));
        RepoDetailsType repoType = contextProvider.getRepoType("invalid-repo");
        assertNull(repoType);
    }

    @Test
    void testBuildSourceHeader_returnsFormattedSourceUri() {
        ServiceId serviceId = mock(ServiceId.class);
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        when(serviceId.getFormattedName()).thenReturn("my-artifactory-service");

        String result = contextProvider.buildSourceHeader();

        assertEquals("my-artifactory-service//" + MOCK_SERVER_ID, result);
    }
}
