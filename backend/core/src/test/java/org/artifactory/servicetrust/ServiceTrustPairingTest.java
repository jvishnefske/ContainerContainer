package org.artifactory.servicetrust;

import lombok.Cleanup;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.metrics.FeatureAdoptionFeature;
import org.artifactory.metrics.framework.adoption.FeatureAdoptionService;
import org.artifactory.metrics.framework.adoption.federated.FederatedRepoLabels;
import org.artifactory.security.access.AccessService;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.app.service.trust.error.ServiceTrustException;
import org.jfrog.common.JsonUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.artifactory.servicetrust.ServiceTrustServiceImpl.ERROR_EXCHANGING_TOKEN_MEMBER_NOT_READY;
import static org.artifactory.servicetrust.ServiceTrustServiceImpl.ERROR_EXCHANGING_TOKEN_NETWORK_FAILURE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;


public class ServiceTrustPairingTest extends ArtifactoryHomeBoundTest {

    AccessService accessService;

    @BeforeMethod
    private void beforeMethod() {
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactRetentionAddon artifactRetentionAddon = mock(ArtifactRetentionAddon.class);
        when(artifactRetentionAddon.isColdInstance()).thenReturn(false);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @AfterMethod
    public void afterMethod() {
        ArtifactoryContextThreadBinder.unbind();
        SecurityContextHolder.clearContext();
    }

    @Test(dataProvider = "serviceTrustProviders")
    public void happyFlow(ServiceTrustUseCase useCase, FeatureAdoptionFeature features, String paringScope) {
        ServiceTrustServiceForTest service = createService("exchangeUrl", paringScope);
        setupUserAuthentication();
        FeatureAdoptionService featureAdoptionService = mock(FeatureAdoptionService.class);
        service.setFeatureAdoptionService(featureAdoptionService);
        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(accessService.verifyAndGetResult((JwtAccessToken) any())).thenReturn(verifyResult);

        service.pairToken(useCase, "token");
        verify(featureAdoptionService)
                .trackAdoption(features, FeatureAdoptionAction.REGISTERED, true, FederatedRepoLabels.TOKEN);
        assertEquals(service.storedUseCase, useCase);
        assertEquals(service.storedResponse.getAccessToken(), "accessToken");
        assertEquals(service.storedResponse.getAccessToken(), "accessToken");
        assertEquals(service.storedResponse.getExpiresIn(), Long.valueOf(42));
        assertEquals(service.storedResponse.getScope(), "scope");
        assertEquals(service.storedResponse.getTokenType(), "access_token");
    }

    @Test(expectedExceptions = ServiceTrustException.class, expectedExceptionsMessageRegExp =
            "Error exchanging token: unacceptable http status code from target server 500")
    public void whenExchangeFailedThenExceptionThrown() {
        ServiceTrustServiceForTest service = createService("exchangeUrl");
        service.expectedStatus = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        setupUserAuthentication();
        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(accessService.verifyAndGetResult((JwtAccessToken) any())).thenReturn(verifyResult);

        service.pairToken(ServiceTrustUseCase.ARTIFACTORY_COLD, "token");
    }

    @Test(expectedExceptions = ServiceTrustException.class, expectedExceptionsMessageRegExp =
            "Error exchanging token: unacceptable http status code from target server 500. " +
                    "WARNING: Custom url for a target server is not configured. It may cause pairing issues")
    public void whenExchangeFailedThenExceptionThrownForLocalhost() {
        ServiceTrustServiceForTest service = createService("http://localhost:8082");
        service.expectedStatus = HttpStatus.SC_INTERNAL_SERVER_ERROR;
        setupUserAuthentication();
        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(accessService.verifyAndGetResult((JwtAccessToken) any())).thenReturn(verifyResult);

        service.pairToken(ServiceTrustUseCase.ARTIFACTORY_COLD, "token");
    }

    @Test(expectedExceptions = ServiceTrustException.class, expectedExceptionsMessageRegExp =
            "Error exchanging token: unacceptable http status code from target server 500")
    public void whenExchangeFailedAndReturnsUnparsableResponseExceptionThrown() {
        ServiceTrustServiceForTest service = createService("http://remote.member:8082");
        service.expectedStatus = HttpStatus.SC_INTERNAL_SERVER_ERROR;

        @Cleanup var entityUtils = mockStatic(EntityUtils.class);
        entityUtils.when(() -> EntityUtils.toString(any())).thenThrow(new IOException());

        setupUserAuthentication();
        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(accessService.verifyAndGetResult((JwtAccessToken) any())).thenReturn(verifyResult);

        service.pairToken(ServiceTrustUseCase.ARTIFACTORY_COLD, "token");
    }

    @Test(expectedExceptions = ServiceTrustException.class,
            expectedExceptionsMessageRegExp = ERROR_EXCHANGING_TOKEN_MEMBER_NOT_READY)
    public void whenExchangeFailsDueToMemberReadinessThenSpecificExceptionThrown() {
        ServiceTrustServiceForTest service = createService("http://remote.member:8082");
        service.expectedStatus = HttpStatus.SC_SERVICE_UNAVAILABLE;
        setupUserAuthentication();
        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(accessService.verifyAndGetResult((JwtAccessToken) any())).thenReturn(verifyResult);

        service.pairToken(ServiceTrustUseCase.ARTIFACTORY_COLD, "token");
    }

    @Test(expectedExceptions = ServiceTrustException.class,
            expectedExceptionsMessageRegExp = ERROR_EXCHANGING_TOKEN_NETWORK_FAILURE)
    public void whenExchangeFailsThenInternalIOExceptionThrown() {
        ServiceTrustServiceForTest service = createService("http://remote.member:8082");
        service.failOnExchange = true;
        setupUserAuthentication();
        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(accessService.verifyAndGetResult((JwtAccessToken) any())).thenReturn(verifyResult);

        service.pairToken(ServiceTrustUseCase.ARTIFACTORY_COLD, "token");
    }



    @Test
    public void testExchangeRequestParameters() throws IOException {
        ServiceTrustServiceForTest service = createService("exchangeUrl");
        setupUserAuthentication();
        FeatureAdoptionService featureAdoptionService = mock(FeatureAdoptionService.class);
        service.setFeatureAdoptionService(featureAdoptionService);

        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(accessService.verifyAndGetResult((JwtAccessToken) any())).thenReturn(verifyResult);

        service.pairToken(ServiceTrustUseCase.ARTIFACTORY_COLD, "token");

        assertEquals(service.executedMethod.getURI().toString(), "exchangeUrl");
        String entity = EntityUtils.toString(service.executedMethod.getEntity());
        ExchangeTokenRequest request = JsonUtils.getInstance().readValue(entity, ExchangeTokenRequest.class);
        assertEquals(request.subject(), "a@b/users/username");
        assertEquals(request.target(), "artifactory-cold");
        assertEquals(Arrays.stream(service.executedMethod.getHeaders(HttpHeaders.AUTHORIZATION)).iterator().next()
                        .getValue(),
                "Bearer token");
    }

    private ServiceTrustServiceForTest createService(String exchangeUrl) {
        return createService(exchangeUrl, "artifactory-cold");
    }

    private ServiceTrustServiceForTest createService(String exchangeUrl, String paringScope) {
        ServiceId serviceId = new ServiceId("a", "b");
        ServiceTrustServiceForTest service = new ServiceTrustServiceForTest(exchangeUrl, paringScope);
        accessService = mock(AccessService.class);
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        service.setAccessService(accessService);
        return service;
    }

    private void setupUserAuthentication() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("username");
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @DataProvider
    public Object[][] serviceTrustProviders() {
        return new Object[][] {
                {ServiceTrustUseCase.ARTIFACTORY_COLD, FeatureAdoptionFeature.COLD_STORAGE,
                        "artifactory-cold"},
                {ServiceTrustUseCase.FEDERATED_REPO, FeatureAdoptionFeature.FEDERATED_REPO, "federated-repo"},
        };
    }

    private static class ServiceTrustServiceForTest extends ServiceTrustServiceImpl {

        private ServiceTrustUseCase storedUseCase;
        private ExchangeTokenResponse storedResponse;
        private HttpPost executedMethod;
        private int expectedStatus = HttpStatus.SC_OK;
        private boolean failOnExchange = false;
        private final String exchangeUrl;
        private final String paringScope;

        public ServiceTrustServiceForTest(String exchangeUrl, String paringScope) {
            this.exchangeUrl = exchangeUrl;
            this.paringScope = paringScope;
        }

        @Override
        protected JwtAccessToken parseTokenValue(String token) {
            TokenExtension extension = new TokenExtension("baseUrl", exchangeUrl, "pairingUrl", false);
            JwtAccessToken jwt = mock(JwtAccessToken.class);
            when(jwt.getExtension()).thenReturn(JsonUtils.getInstance().valueToString(extension));
            when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5));
            when(jwt.getAudience()).thenReturn(List.of("jfrt@50me0therjpd5erv1ce1d"));
            String scope = String.join("", INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_PREFIX, paringScope,
                    INTERNAL_SERVICE_TRUST_PAIRING_SCOPE_POSTFIX);
            when(jwt.getScope()).thenReturn(Arrays.asList(scope));
            return jwt;
        }

        @Override
        CloseableHttpResponse executeMethod(HttpUriRequest httpMethod) throws IOException {

            if(failOnExchange) {
                throw new IOException();
            }

            CloseableHttpResponse response = mock(CloseableHttpResponse.class);
            StatusLine statusLine = mock(StatusLine.class);
            HttpEntity responseEntity;
            switch (expectedStatus) {
                case HttpStatus.SC_INTERNAL_SERVER_ERROR -> {
                    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    responseEntity = new StringEntity("server error", ContentType.APPLICATION_JSON);
                }
                case HttpStatus.SC_SERVICE_UNAVAILABLE -> {
                    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_SERVICE_UNAVAILABLE);
                    responseEntity = new StringEntity("remote member not ready", ContentType.APPLICATION_JSON);
                }
                default -> {
                    executedMethod = (HttpPost) httpMethod;
                    when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
                    ExchangeTokenResponse exchangeTokenResponse =
                            new ExchangeTokenResponse("tokenId", "accessToken", 42L, "scope");
                    String exchangeTokenResponseJson = JsonUtils.getInstance().valueToString(exchangeTokenResponse);
                    responseEntity = new StringEntity(exchangeTokenResponseJson, ContentType.APPLICATION_JSON);
                }
            }
            when(response.getStatusLine()).thenReturn(statusLine);
            when(response.getEntity()).thenReturn(responseEntity);
            return response;
        }

        @Override
        void storeMasterTokenData(ServiceTrustUseCase useCase, ExchangeTokenResponse response,
                TokenExtension extension) {
            storedUseCase = useCase;
            storedResponse = response;
        }
    }
}
