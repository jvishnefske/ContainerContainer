package org.artifactory.servicetrust;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.retention.ArtifactRetentionAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.access.TokenSpec;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.spring.InternalArtifactoryContext;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.jfrog.app.service.trust.error.ServiceTrustException;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ServiceTrustServiceExchangeTest extends ArtifactoryHomeBoundTest {

    AccessService accessService;

    @BeforeMethod
    private void beforeMethod() {
        InternalArtifactoryContext context = mock(InternalArtifactoryContext.class);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactRetentionAddon artifactRetentionAddon = mock(ArtifactRetentionAddon.class);
        when(artifactRetentionAddon.isColdInstanceV1orV2()).thenReturn(true);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(ArtifactRetentionAddon.class)).thenReturn(artifactRetentionAddon);
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    @AfterMethod
    public void afterMethod() {
        ArtifactoryContextThreadBinder.unbind();
        SecurityContextHolder.clearContext();
    }

    @Test
    public void happyFlow() {
        ExchangeTokenRequest request = new ExchangeTokenRequest("subject", "artifactory-cold");
        ServiceTrustServiceImpl service = createService();
        setupTokenAuthentication("internal:service-trust/pairing/artifactory-cold:x");
        ExchangeTokenResponse response = service.exchangePairingToken(request);
        assertEquals(response.getTokenId(), "tokenId");
        assertEquals(response.getAccessToken(), "accessToken");
        assertEquals(response.getExpiresIn(), Long.valueOf(42));
        assertEquals(response.getScope(), "scope");
    }

    @Test
    public void whenItIsRegistrationTokenThenItIsRevoked() {
        ExchangeTokenRequest request = new ExchangeTokenRequest("subject", "artifactory-cold");
        ServiceTrustServiceImpl service = createService();
        setupTokenAuthentication("internal:service-trust/pairing/artifactory-cold:x");
        service.exchangePairingToken(request);
        verify(accessService, times(1)).revokeToken(any());
    }

    @Test(expectedExceptions = ServiceTrustException.class)
    public void whenScopeAndTargetMismatchThenExceptionThrown() {
        ExchangeTokenRequest request = new ExchangeTokenRequest("subject", "artifactory-cold");
        ServiceTrustServiceImpl service = createService();
        setupTokenAuthentication("internal:service-trust/pairing/artifactory-warm:x");
        service.exchangePairingToken(request);
    }

    private ServiceTrustServiceImpl createService() {
        ServiceId serviceId = new ServiceId("a", "b");
        ServiceTrustServiceImpl service = new ServiceTrustServiceForTest();
        accessService = Mockito.mock(AccessService.class);
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        service.setAccessService(accessService);
        return service;
    }

    private void setupTokenAuthentication(String scope) {
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken token = mock(JwtAccessToken.class);
        when(token.getSubject()).thenReturn("a@b/subject");
        when(token.getScope()).thenReturn(Collections.singletonList(scope));
        when(token.getTokenValue()).thenReturn("jwtToken");
        when(token.getExtension()).thenReturn("{\"base_url\":\"http://localhost:8082\"," +
                "\"exchange_url\":\"http://localhost:8081/artifactory/api/v1/service_trust/exchange\"," +
                "\"revocable\":true," +
                "\"pairing_url\":\"http://localhost\"}\"");
        when(authentication.getAccessToken()).thenReturn(token);
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    private static class ServiceTrustServiceForTest extends ServiceTrustServiceImpl {
        @Override
        ExchangeTokenResponse createToken(TokenSpec tokenSpec) {
            return new ExchangeTokenResponse("tokenId", "accessToken", 42L, "scope");
        }
    }
}
