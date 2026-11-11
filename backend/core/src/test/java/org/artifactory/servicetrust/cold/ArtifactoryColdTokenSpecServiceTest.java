package org.artifactory.servicetrust.cold;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.addon.retention.ArtifactRetentionPolicyModel;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.access.GenericTokenSpec;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.descriptor.authentication.AuthenticationToken;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.servicetrust.ServiceTrust;
import org.artifactory.servicetrust.ServiceTrustTokenData;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.TokenExtension;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.artifactory.storage.db.policy.retention.service.packages.PackagePolicyStorageService;
import org.artifactory.storage.db.policy.service.PolicyRunSummaryService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.app.service.trust.error.ServiceTrustException;
import org.jfrog.common.JsonUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS;
import static org.artifactory.servicetrust.ServiceTrustService.INTERNAL_SCOPE_PREFIX;
import static org.artifactory.servicetrust.TokenSpecServiceInboundBase.PAIRING_TOKEN_SCOPES_NULL_OR_EMPTY_ERROR;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

public class ArtifactoryColdTokenSpecServiceTest extends ArtifactoryHomeBoundTest {
    private static final String BASE_URL = "baseUrl";
    private static final String EXCHANGE_URL = "exchangeUrl";
    private static final String PAIRING_URL = "pairingUrl";
    private static final String SUBJECT = "a@b/users/username";
    private static final ServiceId SERVICE_ID = new ServiceId("artifactory", "serviceId");
    private static final String TOKEN_ID = "tokenId";
    private static final String ACCESS_TOKEN = "bearer_access_token";
    private static final String NAMESPACE = "namespace";
    private static final long EXPIRES_IN = 10000;
    private static final String SCOPE_WITH_NAMESPACE =
            "internal:service-trust/pairing/artifactory-cold/" + NAMESPACE + ":x";
    private static final String SCOPE_WITHOUT_NAMESPACE = "internal:service-trust/pairing/artifactory-cold:x";
    private static final String NON_COLD_SCOPE = "internal:service-trust/pairing/artifactory-federation:x";
    private static final String COLD_USE_CASE = "artifactory-cold/namespace";
    private static final String LIMITED_COLD_USE_CASE = "artifactory-cold/namespace:scoped";

    @Mock
    private InternalArtifactoryContext context;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private ArtifactRetentionAddon artifactRetentionAddon;
    @Mock
    private PolicyRunSummaryService policyRunSummaryService;
    @Mock
    private PackagePolicyStorageService packagePolicyStorageService;

    private AutoCloseable closeableMock;

    @BeforeMethod
    private void setup() {
        closeableMock = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(PolicyRunSummaryService.class)).thenReturn(policyRunSummaryService);
        when(context.beanForType(PackagePolicyStorageService.class)).thenReturn(packagePolicyStorageService);
    }

    @AfterMethod
    private void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        SecurityContextHolder.clearContext();
        closeableMock.close();
    }

    @Test
    public void testCreatePairingTokenSpecWithoutNamespace() {
        when(artifactRetentionAddon.generateJFrogColdStorageNamespace()).thenReturn(NAMESPACE);
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        TokenSpec<GenericTokenSpec> tokenSpec = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(new ServiceTrustTokenData(SERVICE_ID, SUBJECT, SCOPE_WITHOUT_NAMESPACE),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(1, scopeList.size());
        assertTrue(scopeList.contains(SCOPE_WITH_NAMESPACE));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildExtensionJson(), tokenSpec.getExtension());
    }

    @Test
    public void testCreatePairingTokenSpecWithNamespace() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);

        TokenSpec<GenericTokenSpec> tokenSpec = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createPairingTokenSpec(new ServiceTrustTokenData(SERVICE_ID, SUBJECT, SCOPE_WITH_NAMESPACE),
                        ServiceTrust.ONE_WAY,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(1, scopeList.size());
        assertTrue(scopeList.contains(SCOPE_WITH_NAMESPACE));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_PAIRING_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildExtensionJson(), tokenSpec.getExtension());
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp =
            "Cold Storage mode is disabled")
    public void testCreatePairingTokenSpecForNonColdInstance() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.createPairingTokenSpec(
                new ServiceTrustTokenData(SERVICE_ID, SUBJECT, "scope"), ServiceTrust.ONE_WAY,
                new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));
    }

    @Test
    public void testCreateMasterTokenSpec() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);

        String pairingScope = "internal:service-trust/pairing/" + COLD_USE_CASE + ":x";
        TokenSpec<GenericTokenSpec> tokenSpec = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createMasterTokenSpec(
                        new ServiceTrustTokenData(SERVICE_ID, SERVICE_ID.getFormattedName(), pairingScope),
                        COLD_USE_CASE,
                        new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        String adminScope = SERVICE_ID + ":admin";
        String requestedScope = INTERNAL_SCOPE_PREFIX + COLD_USE_CASE;

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(3, scopeList.size());
        assertTrue(scopeList.contains(pairingScope));
        assertTrue(scopeList.contains(adminScope));
        assertTrue(scopeList.contains(requestedScope));

        assertTrue(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_MASTER_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildExtensionJson(), tokenSpec.getExtension());
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp =
            "Cold Storage mode is disabled")
    public void testCreateMasterTokenSpecForNonColdInstance() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.createMasterTokenSpec(
                new ServiceTrustTokenData(SERVICE_ID, SERVICE_ID.getFormattedName(), "pairingScope"),
                "useCase",
                new ServiceTrustUrls(BASE_URL, PAIRING_URL, EXCHANGE_URL));
    }

    @Test
    public void testExchangeTokenSpec() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        String useCase = "artifactory-cold/namespace:scoped " + SERVICE_ID + ":admin";

        TokenSpec<GenericTokenSpec> tokenSpec = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createExchangeTokenSpec(SERVICE_ID, useCase);

        assertEquals(1, tokenSpec.getAudience().size());
        assertTrue(tokenSpec.getAudience().contains(SERVICE_ID.toString()));

        String adminScope = SERVICE_ID + ":admin";
        String requestedScope = INTERNAL_SCOPE_PREFIX + useCase;

        List<String> scopeList = tokenSpec.getScope();
        assertEquals(2, scopeList.size());
        assertTrue(scopeList.contains(adminScope));
        assertTrue(scopeList.contains(requestedScope));

        assertFalse(tokenSpec.getRefreshable());
        assertEquals(DEFAULT_EXCHANGE_TOKEN_EXPIRATION_TIME_IN_SECS, tokenSpec.getExpiresIn().longValue());
        assertEquals(buildOnlyRevocableExtensionJson(), tokenSpec.getExtension());
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp =
            "Cold Storage mode is disabled")
    public void testExchangeTokenSpecForNonColdInstance() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.createExchangeTokenSpec(SERVICE_ID,
                "useCase");
    }

    @Test
    public void testCreateExchangeTokenRequestWithNamespace() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(SCOPE_WITH_NAMESPACE));
        ExchangeTokenRequest exchangeTokenRequest = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createExchangeTokenRequest(SUBJECT, jwtAccessToken);
        assertEquals(SUBJECT, exchangeTokenRequest.subject());
        assertEquals(COLD_USE_CASE, exchangeTokenRequest.target());
    }

    @Test
    public void testCreateExchangeTokenRequestWithoutNamespace() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(artifactRetentionAddon.getJfrogColdStorageArchivingNamespace()).thenReturn(NAMESPACE);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(SCOPE_WITHOUT_NAMESPACE));
        ExchangeTokenRequest exchangeTokenRequest = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createExchangeTokenRequest(SUBJECT, jwtAccessToken);
        assertEquals(SUBJECT, exchangeTokenRequest.subject());
        assertEquals(ServiceTrustUseCase.ARTIFACTORY_COLD.getName(), exchangeTokenRequest.target());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp =
            PAIRING_TOKEN_SCOPES_NULL_OR_EMPTY_ERROR)
    public void testCreateExchangeTokenRequestWhenJwtScopeNull() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.createExchangeTokenRequest(SUBJECT,
                jwtAccessToken);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Pair's token does " +
            "not have retention related scope")
    public void testCreateExchangeTokenRequestWithNonColdJwtScope() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(NON_COLD_SCOPE));
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.createExchangeTokenRequest(SUBJECT,
                jwtAccessToken);
    }

    @Test
    public void testStoreMasterTokenData() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, SCOPE_WITH_NAMESPACE);
        TokenExtension tokenExtension = new TokenExtension(BASE_URL, EXCHANGE_URL, null, true);

        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.storeMasterTokenData(exchangeTokenResponse,
                tokenExtension);

        ArgumentCaptor<String> baseUrlCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> exchangeUrlCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> namespaceUrlCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenIdCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> accessTokenCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenTypeCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> expireInCapture = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> scopeCapture = ArgumentCaptor.forClass(String.class);

        verify(artifactRetentionAddon, times(1))
                .saveNamespaceAndMasterToken(baseUrlCapture.capture(),
                        exchangeUrlCapture.capture(),
                        namespaceUrlCapture.capture(),
                        tokenIdCapture.capture(),
                        accessTokenCapture.capture(),
                        tokenTypeCapture.capture(),
                        expireInCapture.capture(),
                        scopeCapture.capture());
        assertEquals(baseUrlCapture.getValue(), BASE_URL);
        assertEquals(exchangeUrlCapture.getValue(), EXCHANGE_URL);
        assertEquals(namespaceUrlCapture.getValue(), NAMESPACE);
        assertEquals(tokenIdCapture.getValue(), TOKEN_ID);
        assertEquals(accessTokenCapture.getValue(), ACCESS_TOKEN);
        assertEquals(tokenTypeCapture.getValue(), "access_token");
        assertEquals(expireInCapture.getValue().longValue(), EXPIRES_IN);
        assertEquals(scopeCapture.getValue(), SCOPE_WITH_NAMESPACE);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Master token does not have scope")
    public void testStoreMasterTokenDataWhenScopeIsNull() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, null);
        TokenExtension tokenExtension = new TokenExtension(BASE_URL, EXCHANGE_URL, null, true);

        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.storeMasterTokenData(exchangeTokenResponse,
                tokenExtension);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Master token scope with the 'artifactory-cold' scope does not exist")
    public void testStoreMasterTokenDataWithNonColdScope() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, NON_COLD_SCOPE);
        TokenExtension tokenExtension = new TokenExtension(BASE_URL, EXCHANGE_URL, null, true);

        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.storeMasterTokenData(exchangeTokenResponse,
                tokenExtension);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Can not parse the master token scope to get a cold namespace: " +
                    "namespace is empty")
    public void testStoreMasterTokenDataWithoutNamespace() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, SCOPE_WITHOUT_NAMESPACE);
        TokenExtension tokenExtension = new TokenExtension(BASE_URL, EXCHANGE_URL, null, true);

        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.storeMasterTokenData(exchangeTokenResponse,
                tokenExtension);
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Instance running in the Cold Storage mode")
    public void testStoreMasterTokenDataForNonWarmInstance() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, SCOPE_WITHOUT_NAMESPACE);
        TokenExtension tokenExtension = new TokenExtension(BASE_URL, EXCHANGE_URL, null, true);

        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.storeMasterTokenData(exchangeTokenResponse,
                tokenExtension);
    }

    @Test(expectedExceptions = ArtifactRetentionBindException.class,
            expectedExceptionsMessageRegExp = "Failed to pair warm and cold instances, please unpair first")
    public void testStoreMasterTokenDataWhenWarmAlreadyPaired() {
        AuthenticationToken authenticationToken = mock(AuthenticationToken.class);
        List<AuthenticationToken> authenticationTokenList = new ArrayList<>();
        authenticationTokenList.add(authenticationToken);
        when(artifactRetentionAddon.getColdStoragePairAuthenticationTokens()).thenReturn(authenticationTokenList);
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, SCOPE_WITHOUT_NAMESPACE);
        TokenExtension tokenExtension = new TokenExtension(BASE_URL, EXCHANGE_URL, null, true);

        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.storeMasterTokenData(exchangeTokenResponse,
                tokenExtension);
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Instance running in the Cold Storage mode")
    public void testUpdateMasterTokenDataForNonWarmInstance() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, SCOPE_WITHOUT_NAMESPACE);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.updateMasterTokenData(
                exchangeTokenResponse);
    }

    @Test
    public void testUpdateMasterTokenData() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ExchangeTokenResponse exchangeTokenResponse =
                new ExchangeTokenResponse(TOKEN_ID, ACCESS_TOKEN, EXPIRES_IN, SCOPE_WITH_NAMESPACE);

        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.updateMasterTokenData(
                exchangeTokenResponse);

        ArgumentCaptor<String> tokenIdCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> accessTokenCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tokenTypeCapture = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> expireInCapture = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> scopeCapture = ArgumentCaptor.forClass(String.class);

        verify(artifactRetentionAddon, times(1))
                .updateMasterToken(tokenIdCapture.capture(),
                        accessTokenCapture.capture(),
                        tokenTypeCapture.capture(),
                        expireInCapture.capture(),
                        scopeCapture.capture());
        assertEquals(TOKEN_ID, tokenIdCapture.getValue());
        assertEquals(ACCESS_TOKEN, accessTokenCapture.getValue());
        assertEquals("access_token", tokenTypeCapture.getValue());
        assertEquals(EXPIRES_IN, expireInCapture.getValue().longValue());
        assertEquals(SCOPE_WITH_NAMESPACE, scopeCapture.getValue());
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Instance running in the Cold Storage mode")
    public void testCreateMasterTokenExchangeRequestForNonWarmInstance() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createMasterTokenExchangeRefreshRequest(SUBJECT);
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Can not create exchange request to refresh master token: namespace " +
                    "does not exist")
    public void testCreateMasterTokenExchangeRequestWhenNamespaceIsEmpty() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(artifactRetentionAddon.getJfrogColdStorageArchivingNamespace()).thenReturn("");
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createMasterTokenExchangeRefreshRequest(SUBJECT);
    }

    @Test
    public void testCreateMasterTokenExchangeRequestWhenNamespace() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(artifactRetentionAddon.getJfrogColdStorageArchivingNamespace()).thenReturn(NAMESPACE);
        ExchangeTokenRequest exchangeTokenRequest =
                ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                        .createMasterTokenExchangeRefreshRequest(
                                SUBJECT);
        assertEquals(SUBJECT, exchangeTokenRequest.subject());
        assertEquals(COLD_USE_CASE, exchangeTokenRequest.target());
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Instance running in the Cold Storage mode")
    public void testCreateScopedTokenExchangeRequestForNonWarmInstance() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.createScopedTokenExchangeRequest(SUBJECT);
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Can not create exchange request to refresh master token: namespace " +
                    "does not exist")
    public void testCreateScopedTokenExchangeRequestWhenNamespaceIsEmpty() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(artifactRetentionAddon.getJfrogColdStorageArchivingNamespace()).thenReturn("");
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE
                .createMasterTokenExchangeRefreshRequest(SUBJECT);
    }

    @Test
    public void testCreateScopedTokenExchangeRequestWhenNamespace() {
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(artifactRetentionAddon.getJfrogColdStorageArchivingNamespace()).thenReturn(NAMESPACE);
        ExchangeTokenRequest exchangeTokenRequest =
                ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.createScopedTokenExchangeRequest(
                        SUBJECT);
        assertEquals(SUBJECT, exchangeTokenRequest.subject());
        assertEquals(LIMITED_COLD_USE_CASE, exchangeTokenRequest.target());
    }

    @Test
    public void testGetAuthenticationToken() {
        String baseUrl = "https://cold.instance.url:8082";
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        AuthenticationToken masterToken = mock(AuthenticationToken.class);
        when(artifactRetentionAddon.getJfrogColdStorageAuthenticationToken(baseUrl)).thenReturn(
                Optional.of(masterToken));
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.getAuthenticationToken(baseUrl);
        verify(artifactRetentionAddon, times(1)).getJfrogColdStorageAuthenticationToken(baseUrl);
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Instance running in the Cold Storage mode")
    public void testGetAuthenticationTokenOnCold() {
        String baseUrl = "https://cold.instance.url:8082";
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.getAuthenticationToken(baseUrl);
    }

    @Test
    public void testDeleteAuthenticationToken() {
        String baseUrl = "https://cold.instance.url:8082";
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.deleteAuthenticationToken(baseUrl);
        verify(artifactRetentionAddon, times(1)).deleteJfrogColdStorageMasterToken(baseUrl);
    }

    @Test(expectedExceptions = IllegalStateException.class,
            expectedExceptionsMessageRegExp = "Instance running in the Cold Storage mode")
    public void testDeleteAuthenticationTokenOnCold() {
        String baseUrl = "https://cold.instance.url:8082";
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.deleteAuthenticationToken(baseUrl);
    }

    @Test
    public void testIsAnyRunningPolicyWithArchivePoliciesRunning() {
        // Set up mock for warm instance with running archive policies
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        Set<RetentionRunSummary> runningPolicies = new HashSet<>();
        RetentionRunSummary summary = mock(RetentionRunSummary.class);
        runningPolicies.add(summary);

        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(runningPolicies);

        // Use try-catch to verify the exception
        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (ServiceTrustException e) {
            // Assert that the exception message matches the expected pattern
            assertTrue(e.getMessage()
                    .startsWith("Revoking a binding is currently not allowed because an archive policy is running"));
        }
    }


    @Test
    public void testIsAnyPolicyRunningWithEnabledScheduledPolicies() {
        // Set up mock for warm instance with no running policies but enabled scheduled policies
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());
        when(policyRunSummaryService.getRunningPolicies(PolicyType.CLEANUP)).thenReturn(Collections.emptySet());

        // Mock the package policy with enabled cron schedule
        List<DbPolicy> policies = new ArrayList<>();
        DbPolicy policy = mock(DbPolicy.class);
        when(policy.enabled()).thenReturn(true);
        when(policy.cronExp()).thenReturn("0 0 * * * ?");  // Some valid cron expression
        policies.add(policy);

        // Mock packagePolicyStorage.getAll() to return the enabled policy
        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenReturn(policies);

        // Use try-catch to verify the exception
        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (ServiceTrustException e) {
            // Assert that the exception message matches the expected pattern
            assertTrue(e.getMessage()
                    .startsWith("Revoking a binding is currently not allowed, as there are active archive policies"));
        }
    }

    @Test
    public void testIsAnyRunningPolicyWithNoOrEnabledPoliciesRunning() throws ServiceTrustException {
        // Set up mock for warm instance with no running or enabled policies
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(
                Collections.emptySet());
        when(policyRunSummaryService.getRunningPolicies(PolicyType.CLEANUP)).thenReturn(Collections.emptySet());

        // No enabled policies
        when(artifactRetentionAddon.getPolicies()).thenReturn(Collections.emptyList());

        // Should return false indicating no running policies
        boolean result = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
        assertTrue(result);
    }

    @Test
    public void testIsAnyPolicyRunningWithDisabledScheduledPolicies() throws ServiceTrustException {
        // Set up mock for warm instance with disabled scheduled policies
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());

        List<ArtifactRetentionPolicyModel> policies = new ArrayList<>();
        ArtifactRetentionPolicyModel policy = mock(ArtifactRetentionPolicyModel.class);
        when(policy.isEnabled()).thenReturn(false);  // Policy is disabled
        when(policy.getCronExp()).thenReturn("0 0 * * * ?");  // Some valid cron expression
        policies.add(policy);

        when(artifactRetentionAddon.getPolicies()).thenReturn(policies);

        // Should return true as policies are disabled
        boolean result = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
        assertTrue(result);
    }

    @Test
    public void testIsAnyPolicyRunningWithEnabledPoliciesButNoCron() throws ServiceTrustException {
        // Set up mock for warm instance with enabled policies but no cron expression
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());
        when(policyRunSummaryService.getRunningPolicies(PolicyType.CLEANUP)).thenReturn(Collections.emptySet());

        List<ArtifactRetentionPolicyModel> policies = new ArrayList<>();
        ArtifactRetentionPolicyModel policy = mock(ArtifactRetentionPolicyModel.class);
        when(policy.isEnabled()).thenReturn(true);  // Policy is enabled
        when(policy.getCronExp()).thenReturn("");  // Empty cron expression
        policies.add(policy);

        when(artifactRetentionAddon.getPolicies()).thenReturn(policies);

        // Should return true as policies have no cron expression
        boolean result = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
        assertTrue(result);
    }

    @Test
    public void testIsAnyPolicyRunningWithMultipleArchivePolicies() {
        // Test with multiple running archive policies
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        Set<RetentionRunSummary> runningPolicies = new HashSet<>();
        RetentionRunSummary summary1 = mock(RetentionRunSummary.class);
        RetentionRunSummary summary2 = mock(RetentionRunSummary.class);
        runningPolicies.add(summary1);
        runningPolicies.add(summary2);

        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(runningPolicies);

        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (ServiceTrustException e) {
            assertTrue(e.getMessage()
                    .startsWith("Revoking a binding is currently not allowed because an archive policy is running"));
        }
    }


    @Test
    public void testIsAnyPolicyRunningWithMultipleEnabledScheduledPolicies() {
        // Test with multiple enabled scheduled policies
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());
        when(policyRunSummaryService.getRunningPolicies(PolicyType.CLEANUP)).thenReturn(Collections.emptySet());

        List<DbPolicy> policies = new ArrayList<>();
        DbPolicy policy1 = mock(DbPolicy.class);
        DbPolicy policy2 = mock(DbPolicy.class);
        when(policy1.enabled()).thenReturn(true);
        when(policy2.enabled()).thenReturn(true);
        when(policy1.cronExp()).thenReturn("0 0 * * * ?");
        when(policy2.cronExp()).thenReturn("0 0 0 * * ?");
        policies.add(policy1);
        policies.add(policy2);

        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenReturn(policies);

        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (ServiceTrustException e) {
            assertTrue(e.getMessage()
                    .startsWith("Revoking a binding is currently not allowed, as there are active archive policies"));
        }
    }

    @Test
    public void testIsAnyPolicyRunningWithMixedPolicyStates() throws ServiceTrustException {
        // Test with mix of enabled/disabled policies with/without cron expressions
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());
        when(policyRunSummaryService.getRunningPolicies(PolicyType.CLEANUP)).thenReturn(Collections.emptySet());

        List<DbPolicy> policies = new ArrayList<>();

        // Enabled policy with cron
        DbPolicy enabledWithCron = mock(DbPolicy.class);
        when(enabledWithCron.enabled()).thenReturn(true);
        when(enabledWithCron.cronExp()).thenReturn("0 0 * * * ?");

        // Enabled policy without cron
        DbPolicy enabledWithoutCron = mock(DbPolicy.class);
        when(enabledWithoutCron.enabled()).thenReturn(true);
        when(enabledWithoutCron.cronExp()).thenReturn("");

        // Disabled policy with cron
        DbPolicy disabledWithCron = mock(DbPolicy.class);
        when(disabledWithCron.enabled()).thenReturn(false);
        when(disabledWithCron.cronExp()).thenReturn("0 0 * * * ?");

        // Disabled policy without cron
        DbPolicy disabledWithoutCron = mock(DbPolicy.class);
        when(disabledWithoutCron.enabled()).thenReturn(false);
        when(disabledWithoutCron.cronExp()).thenReturn("");

        policies.add(enabledWithCron);
        policies.add(enabledWithoutCron);
        policies.add(disabledWithCron);
        policies.add(disabledWithoutCron);

        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenReturn(policies);

        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (ServiceTrustException e) {
            assertTrue(e.getMessage()
                    .startsWith("Revoking a binding is currently not allowed, as there are active archive policies"));
        }
    }

    @Test
    public void testIsForcedUnpairingAllowedNoRunningNoPolicyWithCron() throws ServiceTrustException {
        // Test the success path - no running policies, no enabled policies with cron
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        // No running policies
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());

        // No enabled policies with cron
        List<DbPolicy> policies = new ArrayList<>();
        // Add a disabled policy
        DbPolicy disabledPolicy = mock(DbPolicy.class);
        when(disabledPolicy.enabled()).thenReturn(false);
        when(disabledPolicy.cronExp()).thenReturn("0 0 * * * ?");
        policies.add(disabledPolicy);

        // Add an enabled policy without cron
        DbPolicy enabledNoSchedulePolicy = mock(DbPolicy.class);
        when(enabledNoSchedulePolicy.enabled()).thenReturn(true);
        when(enabledNoSchedulePolicy.cronExp()).thenReturn("");
        policies.add(enabledNoSchedulePolicy);

        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenReturn(policies);

        // Should return true as there are no running policies and no enabled policies with cron
        boolean result = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
        assertTrue(result);

        // Verify the methods were called
        verify(policyRunSummaryService).getRunningPolicies(PolicyType.ARCHIVE);
        verify(packagePolicyStorageService).getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE);
    }

    @Test
    public void testCheckRunningPoliciesThrowsStorageException() {
        // Test when storage exception is thrown during checkRunningPolicies
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        // Simulate a StorageException when getting running policies
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenThrow(
                new org.artifactory.storage.StorageException("Database error"));

        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (ServiceTrustException e) {
            assertEquals("Failed to check running policies", e.getMessage());
        }
    }

    @Test
    public void testCheckEnabledPoliciesWithCronExpressionThrowsStorageException() {
        // Test when storage exception is thrown during checkEnabledPoliciesWithCronExpression
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        // No running policies
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());

        // Simulate StorageException when getting policies
        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenThrow(
                new org.artifactory.storage.StorageException("Database error"));

        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (ServiceTrustException e) {
            assertEquals("Failed to check for active policies with cron schedules", e.getMessage());
        }
    }

    @Test
    public void testIsForcedUnpairingAllowedWithNullCronExpression() throws ServiceTrustException {
        // Test with null cron expression (should be treated as not having a cron expression)
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        // No running policies
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());

        List<DbPolicy> policies = new ArrayList<>();
        // Add an enabled policy with null cron expression
        DbPolicy enabledNullCronPolicy = mock(DbPolicy.class);
        when(enabledNullCronPolicy.enabled()).thenReturn(true);
        when(enabledNullCronPolicy.cronExp()).thenReturn(null); // null cron
        policies.add(enabledNullCronPolicy);

        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenReturn(policies);

        // Should return true since the policy has a null cron expression (considered as not having a cron)
        boolean result = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
        assertTrue(result);
    }

    @Test
    public void testIsForcedUnpairingAllowedWithBlankCronExpression() throws ServiceTrustException {
        // Test with blank cron expression
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        // No running policies
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());

        List<DbPolicy> policies = new ArrayList<>();
        // Add an enabled policy with blank cron expression
        DbPolicy enabledBlankCronPolicy = mock(DbPolicy.class);
        when(enabledBlankCronPolicy.enabled()).thenReturn(true);
        when(enabledBlankCronPolicy.cronExp()).thenReturn("   "); // blank cron (with spaces)
        policies.add(enabledBlankCronPolicy);

        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenReturn(policies);

        // Should return true since the policy has a blank cron expression
        boolean result = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
        assertTrue(result);
    }

    @Test
    public void testIsForcedUnpairingAllowedWithEmptyPolicyList() throws ServiceTrustException {
        // Test with empty policy list
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        // No running policies
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());

        // Empty policy list
        List<DbPolicy> policies = Collections.emptyList();
        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenReturn(policies);

        // Should return true since there are no policies
        boolean result = ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
        assertTrue(result);
    }

    @Test
    public void testIsForcedUnpairingAllowedUncaughtRuntimeException() {
        // Test handling of unexpected runtime exceptions 
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(false);

        // No running policies
        when(policyRunSummaryService.getRunningPolicies(PolicyType.ARCHIVE)).thenReturn(Collections.emptySet());

        // Simulate unexpected runtime exception
        when(packagePolicyStorageService.getAll(PolicyType.ARCHIVE, PolicyItemType.PACKAGE)).thenThrow(
                new RuntimeException("Unexpected error"));

        try {
            ArtifactoryColdTokenSpecService.ARTIFACTORY_COLD_TOKEN_SPEC_SERVICE.isForcedUnpairingAllowed();
            fail("Expected ServiceTrustException to be thrown");
        } catch (RuntimeException e) {
            // The runtime exception should be wrapped in a ServiceTrustException
            assertEquals("Unexpected error", e.getMessage());
        }
    }

    private String buildExtensionJson() {
        return JsonUtils.getInstance().writeValueAsString(
                new TokenExtension(BASE_URL, EXCHANGE_URL, PAIRING_URL, true));
    }

    private String buildOnlyRevocableExtensionJson() {
        return JsonUtils.getInstance().writeValueAsString(new TokenExtension(null, null, null, true));
    }
}