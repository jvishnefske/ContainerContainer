package org.artifactory.federation.pfed;

import com.jfrog.platformfederation.client.PlatformFederationClient;
import com.jfrog.platformfederation.client.PlatformFederationClientBuilder;
import com.jfrog.platformfederation.client.model.TokenInfoModel;
import com.jfrog.sysconf.SysConfig;
import com.jfrog.sysconf.SysConfigHelper;
import lombok.NonNull;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.artifactory.common.ConstantValues.platformFederationFeatureEnabled;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformFederationServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private PlatformFederationServiceImpl platformFederationService;

    private final AccessService accessService = mock(AccessService.class);
    private final PlatformFederationClient platformFederationClient = mock(PlatformFederationClient.class);

    @BeforeEach
    public void setUp() {
        platformFederationService = spy(new PlatformFederationServiceImpl(accessService));
        // Avoid building a real client (which opens gRPC/JFBus connections) - return a mock instead.
        doReturn(platformFederationClient).when(platformFederationService).createPlatformFederationClient();
        // The feature flag defaults to TRUE; individual tests flip it as needed.
        homeStub.setProperty(platformFederationFeatureEnabled, "true");
    }

    @Test
    void initCreatesClientWhenFeatureEnabled() {
        platformFederationService.init();

        verify(platformFederationService).createPlatformFederationClient();
        assertTrue(platformFederationService.getPlatformFederationClient().isPresent());
    }

    @Test
    void initSkipsClientCreationWhenFeatureDisabled() {
        homeStub.setProperty(platformFederationFeatureEnabled, "false");

        platformFederationService.init();

        verify(platformFederationService, never()).createPlatformFederationClient();
        assertFalse(platformFederationService.getPlatformFederationClient().isPresent());
    }

    @Test
    void getClientReturnsCreatedClientWhenEnabled() {
        platformFederationService.init();

        Optional<PlatformFederationClient> client = platformFederationService.getPlatformFederationClient();

        assertTrue(client.isPresent());
        assertEquals(platformFederationClient, client.get());
    }

    @Test
    void getClientIsEmptyWhenClientNotCreated() {
        // Feature enabled, but init() was never called, so no client exists yet.
        assertFalse(platformFederationService.getPlatformFederationClient().isPresent());
    }

    @Test
    void getClientIsEmptyWhenFeatureFlagOffEvenWithClientCreated() {
        platformFederationService.init();
        assertTrue(platformFederationService.getPlatformFederationClient().isPresent());

        homeStub.setProperty(platformFederationFeatureEnabled, "false");

        assertFalse(platformFederationService.getPlatformFederationClient().isPresent());
    }

    @Test
    void destroyClosesAndClearsClient() {
        platformFederationService.init();

        platformFederationService.destroy();

        verify(platformFederationClient).close();
        assertFalse(platformFederationService.getPlatformFederationClient().isPresent());
    }

    @Test
    void destroyIsNoOpWhenClientNotCreated() {
        assertDoesNotThrow(() -> platformFederationService.destroy());

        verify(platformFederationClient, never()).close();
    }

    @Test
    void destroySwallowsCloseExceptionAndClearsClient() {
        platformFederationService.init();
        doThrow(new RuntimeException("boom")).when(platformFederationClient).close();

        assertDoesNotThrow(() -> platformFederationService.destroy());

        verify(platformFederationClient).close();
        assertFalse(platformFederationService.getPlatformFederationClient().isPresent());
    }

    @Test
    void createTokenSignsRequestViaAccessClientAndReportsExpiry() {
        TokenRequest request = mock(TokenRequest.class);
        AccessClient accessClient = mock(AccessClient.class);
        TokenClient tokenClient = mock(TokenClient.class);
        TokenResponse tokenResponse = mock(TokenResponse.class);
        JwtAccessToken parsedToken = mock(JwtAccessToken.class);

        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.token()).thenReturn(tokenClient);
        when(tokenClient.create(request)).thenReturn(tokenResponse);
        when(tokenResponse.getTokenValue()).thenReturn("signed-token");
        when(tokenClient.parse("signed-token")).thenReturn(parsedToken);
        when(parsedToken.getExpirationTime()).thenReturn(1781867188000L);

        TokenInfoModel result = platformFederationService.createToken(request);

        assertEquals("signed-token", result.getTokenValue());
        assertEquals(Long.valueOf(1781867188000L), result.getExpirationTimestampMs());
        // The incoming request is forwarded as-is (no lossy rebuild) and the issued token is parsed for expiry.
        verify(tokenClient).create(request);
        verify(tokenClient).parse("signed-token");
    }

    @Test
    void createPlatformFederationClientWiresBuilderAndSetsTechnicalServerName() {
        PlatformFederationClientBuilder builder = invokeCreateClient("z0dspfedt7jpd1");
        verify(builder).serviceName("artifactory");
        verify(builder).nodeId("node-1");
        verify(builder).routerPort(8046);
        verify(builder).singleTenantTechnicalServerName("z0dspfedt7jpd1");
        verify(builder).create();
    }

    @Test
    void createPlatformFederationClientSkipsTechnicalServerNameWhenNotConfigured() {
        PlatformFederationClientBuilder builder = invokeCreateClient(EMPTY);
        verify(builder, never()).singleTenantTechnicalServerName(anyString());
    }

    /**
     * Runs the real {@link PlatformFederationServiceImpl#createPlatformFederationClient()} with the
     * static collaborators ({@code PlatformFederationClient.builder()}, {@code ArtifactoryHome.get()},
     * {@code ContextHelper.get()}) mocked, and returns the fluent builder mock for verification.
     */
    private PlatformFederationClientBuilder invokeCreateClient(@NonNull String technicalServerName) {
        PlatformFederationServiceImpl service = new PlatformFederationServiceImpl(accessService);
        PlatformFederationClientBuilder builder = mock(PlatformFederationClientBuilder.class, RETURNS_SELF);
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfigHelper sysConfigHelper = mock(SysConfigHelper.class);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);

        Optional<String> techServerName = EMPTY.equals(technicalServerName) ? empty() : of(technicalServerName);
        when(artifactoryHome.getSysConfig()).thenReturn(sysConfig);
        when(sysConfig.helper()).thenReturn(sysConfigHelper);
        when(sysConfigHelper.getRouterPort()).thenReturn(8046);
        when(sysConfig.get("shared.system.technicalServerName")).thenReturn(techServerName);
        when(context.getServerId()).thenReturn("node-1");
        when(builder.create()).thenReturn(platformFederationClient);

        try (MockedStatic<PlatformFederationClient> client = mockStatic(PlatformFederationClient.class);
                MockedStatic<ArtifactoryHome> homeStatic = mockStatic(ArtifactoryHome.class);
                MockedStatic<ContextHelper> contextStatic = mockStatic(ContextHelper.class)) {
            client.when(PlatformFederationClient::builder).thenReturn(builder);
            homeStatic.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
            contextStatic.when(ContextHelper::get).thenReturn(context);
            assertEquals(platformFederationClient, service.createPlatformFederationClient());
        }
        return builder;
    }
}
