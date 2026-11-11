package org.artifactory.rest.resource.servicetrust;

import lombok.NoArgsConstructor;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.servicetrust.ServiceTrustService;
import org.artifactory.servicetrust.ServiceTrustServiceImpl;
import org.artifactory.servicetrust.ServiceTrustUrls;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.artifactory.servicetrust.ServiceTrustUseCaseData;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.common.ServiceId;
import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import java.util.Optional;

import static org.artifactory.rest.resource.servicetrust.ServiceTrustResourceRegisterTokenTest.assertResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class ServiceTrustResourceCreateTokenTest extends ArtifactoryHomeBoundTest {

    @Mock
    AccessServiceTestImpl accessService;

    @Mock
    private AccessClient accessClient;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void whenUseCaseIsUnknownThen404() {
        ServiceTrustResource resource = new ServiceTrustResourceForTest();
        ServiceTrustService service = mock(ServiceTrustService.class);
        when(service.findUseCase(any())).thenReturn(Optional.empty());
        resource.setServiceTrustService(service);

        Response response = resource.createToken("unknown");

        assertResponse(response, 404, "Unknown use case");
    }

    @Test
    public void whenUseCaseIsOkThen200() {
        ServiceTrustResource resource = new ServiceTrustResourceForTest();
        ServiceTrustService service = mock(ServiceTrustService.class);
        when(service.findUseCase(any())).thenReturn(Optional.of(ServiceTrustUseCase.ARTIFACTORY_COLD));
        when(service.getPairingToken(any(), any())).thenReturn("token");
        resource.setServiceTrustService(service);

        Response response = resource.createToken("artifactory-cold");

        int status = response.getStatus();
        String token = ((PairingToken) response.getEntity()).getPairingToken();
        assertEquals(status, 200);
        assertEquals(token, "token");
    }

    @Ignore("the test case is flaky")
    public void whenUseCaseFedRepoGivenArtifactoryPrefixInHostnameThen200() {

        ServiceTrustResource realResource = new ServiceTrustResourceForTest();
        ServiceTrustResource resource = spy(realResource);
        ServiceTrustServiceImpl service = spy(ServiceTrustServiceImpl.class);

        when(accessService.getArtifactoryServiceId()).thenReturn(new ServiceId("jfrt", "test"));
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessService.createToken(any())).thenCallRealMethod();
        service.setAccessService(accessService);
        doReturn("https://artifactory.mycompany.com/artifactory").when(resource).getFederatedRepoUrlBase();

        doReturn(Optional.of(ServiceTrustUseCase.FEDERATED_REPO)).when(service).findUseCase(anyString());
        doCallRealMethod().when(service)
                .getPairingToken(any(ServiceTrustUseCaseData.class), any(ServiceTrustUrls.class));
        resource.setServiceTrustService(service);

        Response response = resource.createTokenResponse("federated-repo/test", ServiceTrustUseCase.FEDERATED_REPO);

        int status = response.getStatus();
        String token = ((PairingToken) response.getEntity()).getPairingToken();
        assertEquals(status, 200);
        assertTrue(token.contains("https://artifactory.mycompany.com"));
        assertFalse(token.contains("https://artifactory.mycompany.com/artifactory"));

        doReturn("https://artifactory.mycompany.com").when(resource).getFederatedRepoUrlBase();

        Response response2 = resource.createTokenResponse("federated-repo/test2", ServiceTrustUseCase.FEDERATED_REPO);

        String token2 = ((PairingToken) response2.getEntity()).getPairingToken();
        assertTrue(token2.contains("https://artifactory.mycompany.com"));
    }

    @Test(enabled = false)
    public void testCreateTokenWithFederatedBaseUrl() throws JSONException {
        ServiceTrustResource realResource = new ServiceTrustResourceForTest(true);
        ServiceTrustResource resource = spy(realResource);

        ServiceTrustServiceImpl service = getServiceTrustService();
        prepareRequest(resource);
        doReturn("https://artifactory.mycompany.com/artifactory").when(resource).getFederatedRepoUrlBase();
        resource.setServiceTrustService(service);

        prepareConfigs();
        try (Response tokenResponse =
                     resource.createTokenResponse("artifactory/api/v1/service_trust/pairing/federated-repo/*",
                             ServiceTrustUseCase.FEDERATED_REPO)) {
            int status = tokenResponse.getStatus();
            String token = ((PairingToken) tokenResponse.getEntity()).getPairingToken();
            assertEquals(status, 200);
            JSONObject jsonObject = new JSONObject(token);
            assertEquals(jsonObject.get("base_url"), "https://artifactory.mycompany.com");
            assertEquals(jsonObject.get("exchange_url"),
                    "https://artifactory.mycompany.com/artifactory/api/v1/service_trust/exchange");
            assertEquals(jsonObject.get("pairing_url"),
                    "https://artifactory.mycompany.com/artifactory/api/v1/service_trust/pairing");
        } finally {
            ArtifactoryContextThreadBinder.unbind();
        }
    }

    private ServiceTrustServiceImpl getServiceTrustService() {
        ServiceTrustServiceImpl service = spy(new ServiceTrustServiceImpl());
        when(accessService.getArtifactoryServiceId()).thenReturn(new ServiceId("jfrt", "test"));
        when(accessService.createToken(any())).thenCallRealMethod();
        service.setAccessService(accessService);
        return service;
    }

    private static void prepareRequest(ServiceTrustResource resource) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRequestURI())
                .thenReturn("http://artifactory.mycompany.com/artifactory/api/v1/service_trust/pairing/federated-repo/*");
        when(servletRequest.getScheme()).thenReturn("https://");
        resource.request = servletRequest;
    }

    private static void prepareConfigs() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        CentralConfigService centralConfigService = mock(CentralConfigService.class);
        when(context.getCentralConfig()).thenReturn(centralConfigService);
        CentralConfigDescriptor descriptor = mock(CentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(descriptor);
        when(descriptor.getFederatedRepoUrlBaseOrDefault()).thenReturn("https://artifactory.mycompany.com");
        ArtifactoryContextThreadBinder.bind(context);
    }


    @NoArgsConstructor
    private static class ServiceTrustResourceForTest extends ServiceTrustResource {

        private boolean shouldRunRealMethod = false;

        public ServiceTrustResourceForTest(boolean shouldRunRealMethod) {
            this.shouldRunRealMethod = shouldRunRealMethod;
        }

        @Override
        String getRestApiUrl() {
            if (shouldRunRealMethod) {
                return super.getRestApiUrl();
            }
            return "";
        }

        @Override
        String getBaseUrl() {
            return "";
        }
    }
}