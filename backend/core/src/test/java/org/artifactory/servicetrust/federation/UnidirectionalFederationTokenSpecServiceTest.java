package org.artifactory.servicetrust.federation;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.federated.FederatedRepoAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
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
import org.jfrog.common.JsonUtils;
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
import static org.artifactory.servicetrust.federation.UnidirectionalFederationTokenSpecService.PAIRING_TOKEN_UNI_FEDERATION_SCOPE_MISSING_ERROR;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class UnidirectionalFederationTokenSpecServiceTest extends ArtifactoryHomeBoundTest {
    private static final String BASE_URL = "http://acme.com:8082";
    private static final String EXCHANGE_URL = "http://acme.com:8082/api/v1/service_trust/exchange";
    private static final String PAIRING_URL = "http://acme.com:8082/api/v1/service_trust/pairing";
    private static final String SUBJECT = "a@b/users/username";
    private static final ServiceId SERVICE_ID = new ServiceId("artifactory", "serviceId");
    private static final String INTERNAL_FULL_FED_SCOPE_PATTERN = "internal:service-trust/pairing/federated-repo/%s:x";
    private static final String INTERNAL_FULL_FED_SCOPE_READ = String.format(INTERNAL_FULL_FED_SCOPE_PATTERN, "r");
    private static final String UNIDIRECTIONAL_USE_CASE = "federated-repo/r";

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

        TokenSpec<GenericTokenSpec> tokenSpec = UnidirectionalFederationTokenSpecService
                .UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(
                        new ServiceTrustTokenData(SERVICE_ID, SUBJECT, INTERNAL_FULL_FED_SCOPE_READ),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(1, scopeList.size());
        assertTrue(scopeList.contains(INTERNAL_FULL_FED_SCOPE_READ));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        String expectedExtension = buildExtensionJson();
        assertEquals(expectedExtension, tokenSpec.getExtension());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = PAIRING_TOKEN_UNI_FEDERATION_SCOPE_MISSING_ERROR)
    public void testCreatePairingTokenSpecEmptyError() {
        UnidirectionalFederationTokenSpecService
                .UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(new ServiceTrustTokenData(SERVICE_ID, SUBJECT, ""),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
            PAIRING_TOKEN_UNI_FEDERATION_SCOPE_MISSING_ERROR)
    public void testCreatePairingTokenSpecForNonFederatedRepository() {
        String nonFedScope = "internal:service-trust/pairing/artifactory-cold:x";
        UnidirectionalFederationTokenSpecService.UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(new ServiceTrustTokenData(SERVICE_ID, SUBJECT, nonFedScope),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));
    }

    @Test(dataProvider = "tokenScopes")
    public void testCreateFederatedRepoMasterTokenSpec(String scope, Optional<Exception> optionalException) {

        String pairingScope = String.format(INTERNAL_FULL_FED_SCOPE_PATTERN, scope);
        String requestedScope = INTERNAL_SCOPE_PREFIX + scope;
        String adminScope = SERVICE_ID + ":admin";

        TokenSpec<GenericTokenSpec> tokenSpec;
        try {
            tokenSpec = UnidirectionalFederationTokenSpecService.UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
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

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(2, scopeList.size());
        assertFalse(scopeList.contains(adminScope));
        assertTrue(scopeList.contains(pairingScope));
        assertTrue(scopeList.contains(requestedScope));

        assertTrue(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildExtensionJson(), tokenSpec.getExtension());
    }

    @DataProvider(name = "tokenScopes")
    public Object[][] tokenScopesProvider() {
        return new Object[][] {
                {UNIDIRECTIONAL_USE_CASE, Optional.empty()},
                {"federated-repo/$", Optional.of(IllegalArgumentException.class)},
                {"artifactory-cold/*", Optional.of(IllegalArgumentException.class)}
        };
    }

    @Test
    public void testExchangeFederatedRepoToken() {
        TokenSpec<GenericTokenSpec> tokenSpec = UnidirectionalFederationTokenSpecService
                .UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                .createExchangeTokenSpec(SERVICE_ID, UNIDIRECTIONAL_USE_CASE);

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        String adminScope = SERVICE_ID + ":admin";
        String requestedScope = "r";

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(2, scopeList.size());
        assertFalse(scopeList.contains(adminScope));
        assertTrue(scopeList.contains(requestedScope));
        assertTrue(scopeList.contains("internal:" + UNIDIRECTIONAL_USE_CASE));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildOnlyRevocableExtensionJson(), tokenSpec.getExtension());
    }

    @Test
    public void testExchangeFederatedRepoTokenRequest() {
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(INTERNAL_FULL_FED_SCOPE_READ));

        ExchangeTokenRequest tokenRequest = UnidirectionalFederationTokenSpecService
                .UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                .createExchangeTokenRequest(SUBJECT, jwtAccessToken);

        assertEquals(SUBJECT, tokenRequest.subject());
        assertEquals(ServiceTrustUseCase.FEDERATED_REPO.getName() + "/r", tokenRequest.target());
    }

    @Test
    public void testScopedExchangeFederatedRepoTokenRequest() {
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(INTERNAL_FULL_FED_SCOPE_READ));

        ExchangeTokenRequest tokenRequest = UnidirectionalFederationTokenSpecService
                .UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                .createScopedTokenExchangeRequest(SUBJECT, "mirror-sync");

        assertEquals(SUBJECT, tokenRequest.subject());
        assertEquals("internal:" + ServiceTrustUseCase.FEDERATED_REPO_READ.getName() +
                "/mirror-sync:r", tokenRequest.target());
    }

    @Test
    public void testCreateRequiredArtifactScope() {
        String scopeForUseCase = UnidirectionalFederationTokenSpecService
                .UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                .createRequiredInternalScope(UNIDIRECTIONAL_USE_CASE);
        assertEquals("internal:" + UNIDIRECTIONAL_USE_CASE, scopeForUseCase);
    }

    @Test
    public void testCreateMasterTokenExchangeRequestWhenNamespace() {
        ExchangeTokenRequest exchangeTokenRequest =
                UnidirectionalFederationTokenSpecService
                        .UNIDIRECTIONAL_FEDERATION_TOKEN_SPEC_SERVICE
                        .createMasterTokenExchangeRefreshRequest(
                                SUBJECT);
        assertEquals(SUBJECT, exchangeTokenRequest.subject());
        assertEquals(UNIDIRECTIONAL_USE_CASE, exchangeTokenRequest.target());
    }
}
