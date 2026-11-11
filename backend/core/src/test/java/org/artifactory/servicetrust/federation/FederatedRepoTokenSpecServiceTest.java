package org.artifactory.servicetrust.federation;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.servicetrust.ServiceTrust;
import org.artifactory.servicetrust.ServiceTrustTokenData;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.TokenExtension;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.common.JsonUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SCOPE_PREFIX;
import static org.artifactory.servicetrust.federation.FederatedRepositoryTokenSpecService.PAIRING_TOKEN_FEDERATION_SCOPE_MISSING_ERROR;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class FederatedRepoTokenSpecServiceTest extends ArtifactoryHomeBoundTest {
    private static final String BASE_URL = "http://acme.com:8082";
    private static final String EXCHANGE_URL = "http://acme.com:8082/api/v1/service_trust/exchange";
    private static final String PAIRING_URL = "http://acme.com:8082/api/v1/service_trust/pairing";
    private static final String SUBJECT = "a@b/users/username";
    private static final ServiceId SERVICE_ID = new ServiceId("artifactory", "serviceId");
    private static final String TOKEN_ID = "tokenId";
    private static final String ACCESS_TOKEN = "ACCESSTOKEN";
    private static final long EXPIRES_IN = 10000;
    private static final String INTERNAL_FULL_FED_SCOPE_PATTERN = "internal:service-trust/pairing/federated-repo/%s:x";
    private static final String INTERNAL_FULL_FED_SCOPE_ALL = String.format(INTERNAL_FULL_FED_SCOPE_PATTERN, "*");
    private static final String NON_FED_SCOPE = "internal:service-trust/pairing/artifactory-cold:x";

    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private FederatedRepoAddon federatedRepoAddon;

    private AutoCloseable closeableMock;

    @BeforeMethod
    private void setup() {
        closeableMock = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(addonsManager.addonByType(FederatedRepoAddon.class)).thenReturn(federatedRepoAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        SecurityContextHolder.clearContext();
        closeableMock.close();
    }

    private String buildExtensionJson() {
        return JsonUtils.getInstance().writeValueAsString(
                new TokenExtension(BASE_URL, EXCHANGE_URL, PAIRING_URL, true));
    }

    private String buildOnlyRevocableExtensionJson() {
        return JsonUtils.getInstance().writeValueAsString(new TokenExtension(null, null, null, true));
    }

    @Test
    public void testCreateFederatedRepoPairingScope() {

        TokenSpec<GenericTokenSpec> tokenSpec = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(
                        new ServiceTrustTokenData(SERVICE_ID, SUBJECT, INTERNAL_FULL_FED_SCOPE_ALL),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(1, scopeList.size());
        assertTrue(scopeList.contains(INTERNAL_FULL_FED_SCOPE_ALL));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        String expectedExtension = buildExtensionJson();
        assertEquals(expectedExtension, tokenSpec.getExtension());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = PAIRING_TOKEN_FEDERATION_SCOPE_MISSING_ERROR)
    public void testCreatePairingTokenSpecEmptyError() {
        FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(new ServiceTrustTokenData(SERVICE_ID, SUBJECT, ""),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
            PAIRING_TOKEN_FEDERATION_SCOPE_MISSING_ERROR)
    public void testCreatePairingTokenSpecForNonFederatedRepository() {
        FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(new ServiceTrustTokenData(SERVICE_ID, SUBJECT, NON_FED_SCOPE),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));
    }

    @Test(dataProvider = "tokenScopes")
    public void testCreateFederatedRepoMasterTokenSpec(String scope, Optional<Exception> optionalException) {

        String pairingScope = String.format(INTERNAL_FULL_FED_SCOPE_PATTERN, scope);

        TokenSpec<GenericTokenSpec> tokenSpec;
        try {
            tokenSpec = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                    .createMasterTokenSpec(
                            new ServiceTrustTokenData(SERVICE_ID, SERVICE_ID.getFormattedName(), pairingScope),
                            scope,
                            new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));
        } catch (Exception actualException) {
            assertTrue(actualException.getClass().equals(optionalException.get()));
            return;
        }

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        String adminScope = SERVICE_ID + ":admin";
        String requestedScope = INTERNAL_SCOPE_PREFIX + scope;

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(3, scopeList.size());
        assertTrue(scopeList.contains(pairingScope));
        assertTrue(scopeList.contains(adminScope));
        assertTrue(scopeList.contains(requestedScope));

        assertTrue(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildExtensionJson(), tokenSpec.getExtension());
    }

    @DataProvider(name = "tokenScopes")
    public Object[][] tokenScopesProvider() {
        return new Object[][] {
                {"federated-repo/fed-repo-1:x", Optional.empty()},
                {"federated-repo/fed-repo-1:w", Optional.empty()},
                {"federated-repo/*", Optional.empty()},
                {"federated-repo/$", Optional.of(IllegalArgumentException.class)},
                {"artifactory-cold/*", Optional.of(IllegalArgumentException.class)}
        };
    }

    @Test
    public void testExchangeFederatedRepoToken() {
        String usecase = "federated-repo/validate-mirror:x";

        TokenSpec<GenericTokenSpec> tokenSpec = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createExchangeTokenSpec(SERVICE_ID, usecase);

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        String adminScope = SERVICE_ID + ":admin";
        String requestedScope = INTERNAL_SCOPE_PREFIX + usecase;

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(1, scopeList.size());
        assertFalse(scopeList.contains(adminScope));
        assertTrue(scopeList.contains(requestedScope));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildOnlyRevocableExtensionJson(), tokenSpec.getExtension());
    }

    @DataProvider(name = "expirationTimes")
    public Object[][] expirationTimesProvider() {
        return new Object[][] { {-10L}, {5L}, {0L} };
    }

    @Test(dataProvider = "expirationTimes")
    public void testExchangeTokenExpirationTime(long expirationTime) {
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.federatedExchangeTokenExpirationTimeHours.getPropertyName(),
                        String.valueOf(expirationTime));

        TokenSpec<GenericTokenSpec> tokenSpec = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createExchangeTokenSpec(SERVICE_ID, "federated-repo/validate-mirror:x");

        long expectedExpirationTime = expirationTime > 0 ? expirationTime
                : DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;

        assertEquals(expectedExpirationTime, tokenSpec.getExpiresIn().longValue());
    }

    @Test
    public void testExchangeFederatedRepoTokenRequest() {
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(INTERNAL_FULL_FED_SCOPE_ALL));

        ExchangeTokenRequest tokenRequest = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createExchangeTokenRequest(SUBJECT, jwtAccessToken);

        assertEquals(SUBJECT, tokenRequest.subject());
        assertEquals(ServiceTrustUseCase.FEDERATED_REPO.getName() + "/*", tokenRequest.target());
    }

    @Test
    public void testStoreMasterTokenData() {
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, INTERNAL_FULL_FED_SCOPE_ALL);

        TokenExtension tokenExtension = new TokenExtension(BASE_URL, EXCHANGE_URL, null, true);

        FederatedRepositoryTokenSpecService.
                FEDERATED_REPO_TOKEN_SPEC_SERVICE.storeMasterTokenData(exchangeTokenResponse, tokenExtension);

        ArgumentCaptor<String> baseUrlCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> exchangeUrlCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenIdCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> accessTokenCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenTypeCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> expireInCapture = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> scopeCapture = ArgumentCaptor.forClass(String.class);

        verify(federatedRepoAddon, times(1))
                .saveAuthenticationMasterToken(tokenIdCapture.capture(),
                        tokenTypeCapture.capture(),
                        scopeCapture.capture(),
                        baseUrlCapture.capture(),
                        exchangeUrlCapture.capture(),
                        accessTokenCapture.capture(),
                        expireInCapture.capture());
        assertEquals(tokenIdCapture.getValue(), TOKEN_ID);
        assertEquals(tokenTypeCapture.getValue(), "access_token");
        assertEquals(scopeCapture.getValue(), INTERNAL_FULL_FED_SCOPE_ALL);
        assertEquals(baseUrlCapture.getValue(), BASE_URL);
        assertEquals(exchangeUrlCapture.getValue(), EXCHANGE_URL);
        assertEquals(accessTokenCapture.getValue(), ACCESS_TOKEN);
        assertEquals(expireInCapture.getValue().longValue(), EXPIRES_IN);
    }

    @Test
    public void testCreateRequiredArtifactScope() {
        String useCase = "artifact:*:r";
        String scopeForUseCase = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createRequiredInternalScope(useCase);
        assertEquals(useCase, scopeForUseCase);
    }

    @Test
    public void testExchangeFederatedRepoTokenForArtifactResource() {
        String usecase = "artifact:*:r";

        TokenSpec<GenericTokenSpec> tokenSpec = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .createExchangeTokenSpec(SERVICE_ID, usecase);

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        String adminScope = SERVICE_ID + ":admin";
        String requestedScope = usecase;

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(1, scopeList.size());
        assertFalse(scopeList.contains(adminScope));
        assertTrue(scopeList.contains(requestedScope));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildOnlyRevocableExtensionJson(), tokenSpec.getExtension());
    }

    @Test
    public void isForcedUnpairingAllowed_shouldReturnTrue_forFederatedRepo() {
        // Arrange & Act
        boolean result = FederatedRepositoryTokenSpecService.FEDERATED_REPO_TOKEN_SPEC_SERVICE
                .isForcedUnpairingAllowed();

        // Assert - Federated repos support force unpairing when remote instance is unreachable
        assertTrue(result);
    }

}
