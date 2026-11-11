package org.artifactory.servicetrust;

import lombok.SneakyThrows;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.ProxyDescriptor;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.util.HttpClientConfigurator;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.app.service.trust.error.ServiceTrustException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.artifactory.servicetrust.ServiceTrustServiceImpl.ERROR_PAIRING_INSTANCE_WITH_ITSELF;
import static org.artifactory.servicetrust.ServiceTrustServiceImpl.ERROR_PAIRING_TOKEN_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceTrustServiceTest extends ArtifactoryHomeBoundTest {

    static final String BASE_URL = "http://localhost:57193";
    static final String EXCHANGE_URL = BASE_URL.concat("/artifactory/api/v1/service_trust/exchange");
    static final String TOKEN_EXTENSION_TEMPLATE =
            """
                    {
                        "base_url": "%1$s",
                        "exchange_url": "%1$s/artifactory/api/v1/service_trust/exchange",
                        "pairing_url": "%1$s/artifactory/api/v1/service_trust/pairing",
                        "revocable": true
                    }
                    """;

    static final String TOKEN_EXTENSION = TOKEN_EXTENSION_TEMPLATE.formatted(BASE_URL);
    static final String FEDERATED_REPO_TEST_PATH = "federated-repo/test";
    static final String COLD_REPO_TEST_PATH = "artifactory-cold/test";
    static final String REVOKE_PATH = "/artifactory/api/v1/service_trust/revoke/";
    static final String REVOKE_URL = BASE_URL + REVOKE_PATH + FEDERATED_REPO_TEST_PATH;
    static final String COLD_REVOKE_URL = BASE_URL + REVOKE_PATH + COLD_REPO_TEST_PATH;
    static final String ENCRYPTED_TOKEN = "encryptedToken";
    static final String DECRYPTED_TOKEN = "decryptedToken";
    static final String TOKEN = "token";

    static final long ONE_YEAR = Duration.ofDays(365).toMillis();

    static final String ADMIN_MASTER_SCOPE =
            "jfrt@th1sServ2ce1d:admin internal:federated-repo/* internal:service-trust/pairing/federated-repo/*:x";
    static final String READ_MASTER_SCOPE =
            "internal:federated-repo/r internal:service-trust/pairing/federated-repo/r:x";

    // Constants for field names to ensure consistency
    private static final String HTTP_CLIENT_FIELD = "httpClient";
    private static final String HTTP_CLIENT_WITH_RETRY_FIELD = "httpClientWithRetry";

    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private FederatedRepoAddon federatedRepoAddon;
    @Mock
    private CloseableHttpClient httpClient;
    @Mock
    private HttpUriRequest httpMethod;
    @Mock
    private CloseableHttpResponse httpResponse;
    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor oldDescriptor;
    @Mock
    private CentralConfigDescriptor newDescriptor;
    @Mock
    private ProxyDescriptor proxyDescriptor;
    @Mock
    private TokenSpecInboundService tokenSpecInboundService;

    private ServiceTrustServiceImpl serviceTrustService;
    private AutoCloseable closeableMock;

    @Mock
    private ArtifactoryHome artifactoryHome;

    @BeforeEach
    void setup() {
        closeableMock = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(addonsManager.addonByType(FederatedRepoAddon.class)).thenReturn(federatedRepoAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        ArtifactorySystemProperties artifactorySystemProperties = new ArtifactorySystemProperties();
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        ArtifactoryHome.bind(artifactoryHome);
    }

    @AfterEach
    void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        SecurityContextHolder.clearContext();
        closeableMock.close();
    }

    @Test
    void testPairingShouldFailInCasePairingTokenExpired() throws IOException {
        ServiceTrustServiceImpl service = spy(ServiceTrustServiceImpl.class);

        var jwt = mock(JwtAccessToken.class);
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() - 10000);

        doReturn(jwt).when(service).parseTokenValue(anyString());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            service.pairToken(ServiceTrustUseCase.FEDERATED_REPO, "token");
        });

        assertEquals(ERROR_PAIRING_TOKEN_EXPIRED, exception.getMessage());
    }

    @Test
    void testExecuteMethodSuccess() throws Exception {
        serviceTrustService = new ServiceTrustServiceImpl();
        setHttpClient(serviceTrustService, httpClient);
        when(httpClient.execute(httpMethod)).thenReturn(httpResponse);
        CloseableHttpResponse response = serviceTrustService.executeMethod(httpMethod);
        assertNotNull(response);
        verify(httpClient, times(1)).execute(httpMethod);
    }

    @Test
    void testGetHttpClientCallInit() throws Exception {
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        doAnswer(invocation -> {
            setHttpClient(serviceTrustService, httpClient);
            return null;
        }).when(serviceTrustService).initHttpClient();
        when(httpClient.execute(httpMethod)).thenReturn(httpResponse);
        CloseableHttpResponse response = serviceTrustService.executeMethod(httpMethod);
        assertNotNull(response);
        verify(httpClient, times(1)).execute(httpMethod);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testReload(boolean isDiff) {
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        serviceTrustService.setCentralConfigService(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(newDescriptor);
        when(newDescriptor.getDefaultProxy()).thenReturn(proxyDescriptor);
        when(oldDescriptor.getDefaultProxy()).thenReturn(proxyDescriptor);
        when(proxyDescriptor.diff(proxyDescriptor)).thenReturn(isDiff);
        serviceTrustService.reload(oldDescriptor, List.of());
        if (isDiff) {
            verify(serviceTrustService, times(0)).tearDownHttpClient();
        } else {
            verify(serviceTrustService, times(1)).tearDownHttpClient();
        }
    }


    @SuppressWarnings({"java:S3011", "java:S5977"})
    // Suppress warnings about reflection access and accessibility updates
    private void setHttpClient(ServiceTrustServiceImpl service, CloseableHttpClient httpClient) throws Exception {
        setField(service, HTTP_CLIENT_FIELD, httpClient);
    }

    @SuppressWarnings({"java:S3011", "java:S5977"})
    // Generic method to set field value using reflection
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings({"java:S3011"})
    // Generic method to get field value using reflection
    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    @SneakyThrows
    @Test
    void testPairingShouldFailInCasePairingTokenAudienceIsThisInstance() {
        ServiceTrustServiceImpl service = spy(ServiceTrustServiceImpl.class);
        AccessService accessService = mock(AccessService.class);
        ServiceId serviceId = new ServiceId("jfrt", "th1sServ2ce1d");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        service.setAccessService(accessService);

        var jwt = mock(JwtAccessToken.class);
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() + 10000);
        List<String> audiences = List.of(accessService.getArtifactoryServiceId().getFormattedName());
        when(jwt.getAudience()).thenReturn(audiences);

        doReturn(jwt).when(service).parseTokenValue(anyString());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> {
            service.pairToken(ServiceTrustUseCase.FEDERATED_REPO, "token");
        });

        assertEquals(ERROR_PAIRING_INSTANCE_WITH_ITSELF, exception.getMessage());
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("useCases")
    void testRefreshMasterTokens(ServiceTrustUseCase serviceTrustUseCase, String scope) {
        try (MockedStatic<JwtAccessTokenImpl> staticJwtAccessTokenImpl = mockStatic(JwtAccessTokenImpl.class)) {
            ServiceTrustServiceImpl service = spy(ServiceTrustServiceImpl.class);

            String tokenId = UUID.randomUUID().toString();

            ExchangeTokenResponse response = new ExchangeTokenResponse(tokenId, "token", ONE_YEAR, scope);

            doReturn(response).when(service)
                    .executeTokenExchangeRequest(anyString(), anyString(), any());

            JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
            when(jwtAccessToken.getExtension()).thenReturn(TOKEN_EXTENSION);

            staticJwtAccessTokenImpl.when(() -> JwtAccessTokenImpl.parseTokenValue(any()))
                    .thenReturn(jwtAccessToken);

            AccessService accessService = mock(AccessService.class);
            ServiceId serviceId = new ServiceId("jfrt", "th1sServ2ce1d");
            when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
            service.setAccessService(accessService);

            var jwt = mock(JwtAccessToken.class);
            when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() + 10000);
            List<String> audiences = List.of(accessService.getArtifactoryServiceId().getFormattedName());
            when(jwt.getAudience()).thenReturn(audiences);

            doReturn(jwt).when(service).parseTokenValue(anyString());

            service.refreshMasterToken(serviceTrustUseCase, EXCHANGE_URL, "token");

            verify(federatedRepoAddon).saveAuthenticationMasterToken(
                    eq(tokenId),
                    eq("access_token"),
                    eq(scope),
                    eq(BASE_URL),
                    eq(EXCHANGE_URL),
                    eq("token"),
                    eq(ONE_YEAR));
        }
    }

    @ParameterizedTest
    @MethodSource("validUseCaseProvider")
    void findUsecaseSuccessTest(String usecase) {
        ServiceTrustService service = new ServiceTrustServiceImpl();
        Optional<ServiceTrustUseCase> useCase = service.findUseCase(usecase);
        assertNotNull(useCase);
    }

    @ParameterizedTest
    @MethodSource("invalidUsecaseProvider")
    void findUsecaseFailTest(String usecase) {
        ServiceTrustService service = new ServiceTrustServiceImpl();
        assertThrows(ServiceTrustException.class, () -> {
            service.findUseCase(usecase);
        });
    }

    private static List<String> validUseCaseProvider() {
        return List.of(
                "federated-repo/fedrepo-1:x",
                "federated-repo/fedrepo-1:w",
                "federated-repo/*",
                "internal:federated-repo/*",
                "artifact:*:r"
        );
    }

    private static List<String> invalidUsecaseProvider() {
        return List.of(
                "federated-repo/fed-repo-1 ",
                "  federated-repo/fed-repo-1",
                "federated-repo/  fed-repo-1",
                "   federated-repo/*"
        );
    }

    private static Object[][] useCases() {
        return new Object[][]{
                {ServiceTrustUseCase.FEDERATED_REPO, ADMIN_MASTER_SCOPE},
                {ServiceTrustUseCase.FEDERATED_REPO_READ, READ_MASTER_SCOPE},
        };
    }

    @Test
    @SneakyThrows
    void testUnpairTokenWhenTokenNotFound() {
        // Setup
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        doReturn(tokenSpecInboundService).when(serviceTrustService)
                .getInboundSpecService(any(ServiceTrustUseCase.class));
        when(tokenSpecInboundService.getAuthenticationToken(anyString())).thenReturn(Optional.empty());

        // Execute & Verify
        ServiceTrustException exception = assertThrows(ServiceTrustException.class, () ->
                serviceTrustService.unpairToken(ServiceTrustUseCase.FEDERATED_REPO, FEDERATED_REPO_TEST_PATH, BASE_URL,
                        false));

        assertEquals("Pairing does not exist for instance", exception.getMessage());
        verify(tokenSpecInboundService).getAuthenticationToken(BASE_URL);
    }

    @Test
    @SneakyThrows
    void testForceUnpairTokenWithSuccessfulRevocation() {
        // Setup
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        doReturn(tokenSpecInboundService).when(serviceTrustService)
                .getInboundSpecService(any(ServiceTrustUseCase.class));
        when(tokenSpecInboundService.getAuthenticationToken(anyString())).thenReturn(Optional.of(ENCRYPTED_TOKEN));
        when(tokenSpecInboundService.isForcedUnpairingAllowed()).thenReturn(true);

        try (
                MockedStatic<CryptoHelper> cryptoHelperMock = mockStatic(CryptoHelper.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
                MockedStatic<JwtAccessTokenImpl> jwtAccessTokenImplMock = mockStatic(JwtAccessTokenImpl.class)
        ) {

            // Mock static calls
            ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
            cryptoHelperMock.when(() -> CryptoHelper.decryptIfNeeded(any(), eq(ENCRYPTED_TOKEN)))
                    .thenReturn(DECRYPTED_TOKEN);

            JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
            when(jwtAccessToken.getExtension()).thenReturn(TOKEN_EXTENSION);
            jwtAccessTokenImplMock.when(() -> JwtAccessTokenImpl.parseTokenValue(DECRYPTED_TOKEN))
                    .thenReturn(jwtAccessToken);

            // Mock executeRevokeTokenRequestWithRetries to return true (successful revocation)
            doReturn(true).when(serviceTrustService).executeRevokeTokenRequestWithRetries(
                    eq(COLD_REVOKE_URL),
                    eq(DECRYPTED_TOKEN));

            // Execute
            serviceTrustService.unpairToken(ServiceTrustUseCase.ARTIFACTORY_COLD, "artifactory-cold/test", BASE_URL,
                    true);

            // Verify
            verify(serviceTrustService).executeRevokeTokenRequestWithRetries(
                    eq(COLD_REVOKE_URL),
                    eq(DECRYPTED_TOKEN));
            verify(tokenSpecInboundService).deleteAuthenticationToken(BASE_URL);
        }
    }

    @Test
    @SneakyThrows
    void testForceUnpairTokenWithFailedRevocation() {
        // Setup
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        doReturn(tokenSpecInboundService).when(serviceTrustService)
                .getInboundSpecService(any(ServiceTrustUseCase.class));
        when(tokenSpecInboundService.getAuthenticationToken(anyString())).thenReturn(Optional.of(ENCRYPTED_TOKEN));
        when(tokenSpecInboundService.isForcedUnpairingAllowed()).thenReturn(true);

        try (
                MockedStatic<CryptoHelper> cryptoHelperMock = mockStatic(CryptoHelper.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
                MockedStatic<JwtAccessTokenImpl> jwtAccessTokenImplMock = mockStatic(JwtAccessTokenImpl.class)
        ) {

            // Mock static calls
            ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
            cryptoHelperMock.when(() -> CryptoHelper.decryptIfNeeded(any(), eq(ENCRYPTED_TOKEN)))
                    .thenReturn(DECRYPTED_TOKEN);

            JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
            when(jwtAccessToken.getExtension()).thenReturn(TOKEN_EXTENSION);
            jwtAccessTokenImplMock.when(() -> JwtAccessTokenImpl.parseTokenValue(DECRYPTED_TOKEN))
                    .thenReturn(jwtAccessToken);

            // Mock executeRevokeTokenRequestWithRetries to throw IOException
            doAnswer(invocation -> {throw new IOException("Network error");})
                    .when(serviceTrustService).executeRevokeTokenRequestWithRetries(anyString(), anyString());

            // Execute
            serviceTrustService.unpairToken(ServiceTrustUseCase.FEDERATED_REPO, FEDERATED_REPO_TEST_PATH, BASE_URL,
                    true);

            // Verify that token is deleted even if exception occurs
            verify(tokenSpecInboundService).deleteAuthenticationToken(BASE_URL);
        }
    }

    @Test
    @SneakyThrows
    void testRegularUnpairTokenWithSuccessfulRevocation() {
        // Setup
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        doReturn(tokenSpecInboundService).when(serviceTrustService)
                .getInboundSpecService(any(ServiceTrustUseCase.class));
        when(tokenSpecInboundService.getAuthenticationToken(anyString())).thenReturn(Optional.of(ENCRYPTED_TOKEN));
        when(tokenSpecInboundService.isForcedUnpairingAllowed()).thenReturn(false);

        try (
                MockedStatic<CryptoHelper> cryptoHelperMock = mockStatic(CryptoHelper.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
                MockedStatic<JwtAccessTokenImpl> jwtAccessTokenImplMock = mockStatic(JwtAccessTokenImpl.class)
        ) {

            // Mock static calls
            ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
            cryptoHelperMock.when(() -> CryptoHelper.decryptIfNeeded(any(), eq(ENCRYPTED_TOKEN)))
                    .thenReturn(DECRYPTED_TOKEN);

            JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
            when(jwtAccessToken.getExtension()).thenReturn(TOKEN_EXTENSION);
            jwtAccessTokenImplMock.when(() -> JwtAccessTokenImpl.parseTokenValue(DECRYPTED_TOKEN))
                    .thenReturn(jwtAccessToken);

            // Mock executeRevokeTokenRequest to do nothing (successful)
            doAnswer(invocation -> null).when(serviceTrustService)
                    .executeRevokeTokenRequest(anyString(), anyString());

            // Execute
            serviceTrustService.unpairToken(ServiceTrustUseCase.FEDERATED_REPO, FEDERATED_REPO_TEST_PATH, BASE_URL,
                    false);

            // Verify
            verify(serviceTrustService).executeRevokeTokenRequest(
                    eq(REVOKE_URL),
                    eq(DECRYPTED_TOKEN));
            verify(tokenSpecInboundService).deleteAuthenticationToken(BASE_URL);
        }
    }

    @Test
    @SneakyThrows
    void testRegularUnpairTokenWithFailedRevocation() {
        // Setup
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        doReturn(tokenSpecInboundService).when(serviceTrustService)
                .getInboundSpecService(any(ServiceTrustUseCase.class));
        when(tokenSpecInboundService.getAuthenticationToken(anyString())).thenReturn(Optional.of(ENCRYPTED_TOKEN));
        when(tokenSpecInboundService.isForcedUnpairingAllowed()).thenReturn(false);

        try (
                MockedStatic<CryptoHelper> cryptoHelperMock = mockStatic(CryptoHelper.class);
                MockedStatic<ArtifactoryHome> artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
                MockedStatic<JwtAccessTokenImpl> jwtAccessTokenImplMock = mockStatic(JwtAccessTokenImpl.class)
        ) {

            // Mock static calls
            ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
            artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
            cryptoHelperMock.when(() -> CryptoHelper.decryptIfNeeded(any(), eq(ENCRYPTED_TOKEN)))
                    .thenReturn(DECRYPTED_TOKEN);

            JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
            when(jwtAccessToken.getExtension()).thenReturn(TOKEN_EXTENSION);
            jwtAccessTokenImplMock.when(() -> JwtAccessTokenImpl.parseTokenValue(DECRYPTED_TOKEN))
                    .thenReturn(jwtAccessToken);

            // Mock executeRevokeTokenRequest to throw exception
            doAnswer(invocation -> {throw new IOException("Network error");})
                    .when(serviceTrustService).executeRevokeTokenRequest(anyString(), anyString());

            // Execute & Verify exception
            ServiceTrustException exception = assertThrows(ServiceTrustException.class, () ->
                    serviceTrustService.unpairToken(ServiceTrustUseCase.FEDERATED_REPO, FEDERATED_REPO_TEST_PATH,
                            BASE_URL, false));

            assertEquals("Failed calling revoke token endpoint", exception.getMessage());

            // Verify authentication token is NOT deleted in this case
            verify(tokenSpecInboundService, times(0)).deleteAuthenticationToken(BASE_URL);
        }
    }

    /**
     * Test the initHttpClientWithRetry method by verifying it correctly configures and sets the HTTP client
     * with retry capability.
     */
    @Test
    @SuppressWarnings({"java:S3011"})
    // Suppress warnings about reflection
    void testInitHttpClientWithRetry() {
        // Create a spy of ServiceTrustServiceImpl that will be used to verify interactions
        serviceTrustService = spy(new ServiceTrustServiceImpl());
        serviceTrustService.setCentralConfigService(centralConfigService);

        // Set up mock behavior for configuration
        when(centralConfigService.getDescriptor()).thenReturn(newDescriptor);
        when(newDescriptor.getDefaultProxy()).thenReturn(proxyDescriptor);

        // Mock the HttpClientConfigurator class using MockedConstruction
        try (MockedConstruction<HttpClientConfigurator> mockedConfigurator = Mockito.mockConstruction(
                HttpClientConfigurator.class, (mock, mockContext) -> {
                    // Set up chained method call behavior
                    when(mock.proxy(proxyDescriptor)).thenReturn(mock);
                    when(mock.socketTimeout(anyInt())).thenReturn(mock);
                    when(mock.connectionTimeout(anyInt())).thenReturn(mock);
                    when(mock.retry(anyInt())).thenReturn(mock);
                    when(mock.build()).thenReturn(httpClient);
                })) {

            // Call the method we're testing
            serviceTrustService.initHttpClientWithRetry();

            // Verify the mocked central config service was called
            verify(centralConfigService).getDescriptor();
            verify(newDescriptor).getDefaultProxy();

            // Verify that a HttpClientConfigurator was constructed
            assertEquals(1, mockedConfigurator.constructed().size());

            // Verify the HttpClientConfigurator was configured correctly
            HttpClientConfigurator configurator = mockedConfigurator.constructed().get(0);
            verify(configurator).proxy(proxyDescriptor);
            verify(configurator).socketTimeout(anyInt());
            verify(configurator).connectionTimeout(anyInt());
            verify(configurator).retry(anyInt());
            verify(configurator).build();

            // Verify that the httpClientWithRetry field was set correctly
            Object returnedClient = getField(serviceTrustService, HTTP_CLIENT_WITH_RETRY_FIELD);
            assertEquals(httpClient, returnedClient);
        } catch (Exception e) {
            throw new AssertionError("Failed to test initHttpClientWithRetry: " + e.getMessage(), e);
        }
    }

    /**
     * Test the executeMethod functionality which is used for HTTP request execution with retry capability.
     * This method verifies that HTTP requests can be properly executed using the configured client.
     */
    @Test
    @SuppressWarnings({"java:S3011"})
    // Suppress warnings about reflection
    void testExecuteRevokeTokenRequestWithRetries() throws IOException {
        // Create service that's configured for testing without needing ArtifactoryHome
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Set up HTTP response mocking
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(
                new org.apache.http.message.BasicStatusLine(new org.apache.http.ProtocolVersion("HTTP", 1, 1),
                        org.apache.http.HttpStatus.SC_OK, "OK"));

        try {
            // Mock the getClosableHttpClient method to return our test client
            // This avoids calling the real initialization which needs ArtifactoryHome
            setField(serviceTrustService, HTTP_CLIENT_FIELD, httpClient);

            // Call executeMethod which relies on the HTTP client
            CloseableHttpResponse response = serviceTrustService.executeMethod(httpMethod);

            // Verify the HTTP client executed the request
            verify(httpClient).execute(httpMethod);
            assertNotNull(response);
        } catch (Exception e) {
            throw new AssertionError("Failed to test HTTP client execution: " + e.getMessage(), e);
        }
    }

    /**
     * Test the tearDownHttpClient method which closes the HTTP clients and sets them to null.
     * This test verifies that the method properly closes both httpClient and httpClientWithRetry,
     * and sets them back to null.
     */
    @Test
    @SuppressWarnings({"java:S3011", "java:S5977"})
    // Suppress warnings about reflection access and accessibility updates
    void testTearDownHttpClient() throws Exception {
        // Create a spy of ServiceTrustServiceImpl for testing
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Create mocks for both HTTP clients
        CloseableHttpClient mockHttpClient = mock(CloseableHttpClient.class);
        CloseableHttpClient mockHttpClientWithRetry = mock(CloseableHttpClient.class);

        // Set the HTTP clients via reflection using our helper methods
        setField(serviceTrustService, HTTP_CLIENT_FIELD, mockHttpClient);
        setField(serviceTrustService, HTTP_CLIENT_WITH_RETRY_FIELD, mockHttpClientWithRetry);

        // Call the method we're testing
        serviceTrustService.destroy();

        // Verify that both HTTP clients were closed
        verify(mockHttpClient).close();
        verify(mockHttpClientWithRetry).close();

        // Verify that both HTTP client fields are now null using our helper method
        assertNull(getField(serviceTrustService, HTTP_CLIENT_FIELD));
        assertNull(getField(serviceTrustService, HTTP_CLIENT_WITH_RETRY_FIELD));
    }

    /**
     * Test for executeRevokeTokenRequestWithHttpClient method to verify successful token revocation.
     * This tests the HTTP request handling when the server returns a successful response.
     */
    @Test
    void testExecuteRevokeTokenRequestWithHttpClientSuccess() throws IOException, ServiceTrustException {
        // Create service that's configured for testing
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Set up HTTP response mocking for success case
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(
                new org.apache.http.message.BasicStatusLine(new org.apache.http.ProtocolVersion("HTTP", 1, 1),
                        org.apache.http.HttpStatus.SC_OK, "OK"));

        // Execute the method under test
        serviceTrustService.executeRevokeTokenRequestWithHttpClient(REVOKE_URL, TOKEN, httpClient);

        // Verify the HTTP client was called and the request had the expected structure
        verify(httpClient).execute(any(HttpUriRequest.class));
    }

    /**
     * Test for executeRevokeTokenRequestWithHttpClient method to verify error handling.
     * This tests the HTTP request handling when the server returns an error response.
     */
    @Test
    void testExecuteRevokeTokenRequestWithHttpClientError() throws IOException {
        // Create service that's configured for testing
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Set up HTTP response mocking for error case
        when(httpClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(
                new org.apache.http.message.BasicStatusLine(new org.apache.http.ProtocolVersion("HTTP", 1, 1),
                        org.apache.http.HttpStatus.SC_BAD_REQUEST, "Bad Request"));
        when(httpResponse.getEntity()).thenReturn(mock(org.apache.http.HttpEntity.class));

        // Execute the method under test and expect an exception
        ServiceTrustException exception = assertThrows(ServiceTrustException.class, () ->
                serviceTrustService.executeRevokeTokenRequestWithHttpClient(REVOKE_URL, TOKEN, httpClient));

        // Verify the exception message contains the expected error
        assertTrue(exception.getMessage().contains("Error unpairing token"));
        verify(httpClient).execute(any(HttpUriRequest.class));
    }

    /**
     * Test for executeRevokeTokenRequest method to verify it uses the regular HTTP client.
     */
    @Test
    void testExecuteRevokeTokenRequest() throws IOException {
        // Create service that's configured for testing
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Set up normal HTTP client and response
        CloseableHttpClient normalClient = mock(CloseableHttpClient.class);
        when(normalClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(
                new org.apache.http.message.BasicStatusLine(new org.apache.http.ProtocolVersion("HTTP", 1, 1),
                        org.apache.http.HttpStatus.SC_OK, "OK"));

        // Mock getClosableHttpClient to return our test client
        doReturn(normalClient).when(serviceTrustService).getClosableHttpClient();

        // Call the method under test
        serviceTrustService.executeRevokeTokenRequest(REVOKE_URL, TOKEN);

        // Verify correct HTTP client was used
        verify(serviceTrustService).getClosableHttpClient();
        verify(normalClient).execute(any(HttpUriRequest.class));
    }

    /**
     * Comprehensive test for executeRevokeTokenRequestWithRetries method to verify it uses the
     * retry-enabled HTTP client.
     */
    @Test
    void testExecuteRevokeTokenRequestWithRetriesComprehensive() throws IOException, ServiceTrustException {
        // Create service that's configured for testing
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Set up retry HTTP client and response
        CloseableHttpClient retryClient = mock(CloseableHttpClient.class);
        when(retryClient.execute(any(HttpUriRequest.class))).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(
                new org.apache.http.message.BasicStatusLine(new org.apache.http.ProtocolVersion("HTTP", 1, 1),
                        org.apache.http.HttpStatus.SC_OK, "OK"));

        // Mock getClosableRetryHttpClient to return our test client
        doReturn(retryClient).when(serviceTrustService).getClosableRetryHttpClient();

        // Call the method under test
        boolean result = serviceTrustService.executeRevokeTokenRequestWithRetries(REVOKE_URL, TOKEN);

        // Verify correct HTTP client was used and result is true
        assertTrue(result);
        verify(serviceTrustService).getClosableRetryHttpClient();
        verify(retryClient).execute(any(HttpUriRequest.class));
    }

    /**
     * Test executeRevokeTokenRequestWithRetries handles exceptions properly.
     */
    @Test
    void testExecuteRevokeTokenRequestWithRetriesHandlesErrors() throws IOException {
        // Create service that's configured for testing
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Set up retry HTTP client to throw an exception
        CloseableHttpClient retryClient = mock(CloseableHttpClient.class);
        when(retryClient.execute(any(HttpUriRequest.class))).thenThrow(new IOException("Network failure"));

        // Mock getClosableRetryHttpClient to return our test client
        doReturn(retryClient).when(serviceTrustService).getClosableRetryHttpClient();

        // Call the method under test and expect an exception
        assertThrows(IOException.class, () ->
                serviceTrustService.executeRevokeTokenRequestWithRetries(REVOKE_URL, TOKEN));

        // Verify correct HTTP client was used
        verify(serviceTrustService).getClosableRetryHttpClient();
        verify(retryClient).execute(any(HttpUriRequest.class));
    }

    /**
     * Test for the getClosableRetryHttpClient method.
     * This test verifies that:
     * 1. The method initializes the retry HTTP client if it's null
     * 2. The method returns the configured client
     * 3. The method correctly uses the lock mechanism
     */
    @Test
    @SuppressWarnings({"java:S3011"})
    void testGetClosableRetryHttpClient() throws Exception {
        // Create service spy for testing
        serviceTrustService = spy(new ServiceTrustServiceImpl());

        // Verify initialization case - when httpClientWithRetry is null
        doAnswer(invocation -> {
            // Simulate initialization by setting the field directly
            setField(serviceTrustService, HTTP_CLIENT_WITH_RETRY_FIELD, httpClient);
            return null;
        }).when(serviceTrustService).initHttpClientWithRetry();

        // Call the method and verify it returned the correct client
        CloseableHttpClient result = serviceTrustService.getClosableRetryHttpClient();
        assertNotNull(result);
        assertEquals(httpClient, result);

        // Verify initHttpClientWithRetry was called exactly once
        verify(serviceTrustService, times(1)).initHttpClientWithRetry();

        // Reset the mock to test the cached client path (when httpClientWithRetry is not null)
        Mockito.reset(serviceTrustService);

        // Call the method again and verify initHttpClient is not called again
        CloseableHttpClient secondResult = serviceTrustService.getClosableRetryHttpClient();
        assertNotNull(secondResult);
        assertEquals(httpClient, secondResult);

        // Verify initHttpClient was not called
        verify(serviceTrustService, times(0)).initHttpClientWithRetry();
    }
}