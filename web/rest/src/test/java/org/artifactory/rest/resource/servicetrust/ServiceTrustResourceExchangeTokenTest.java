package org.artifactory.rest.resource.servicetrust;

import org.artifactory.servicetrust.ServiceTrustService;
import org.jfrog.app.service.trust.dto.ExchangeTokenRequest;
import org.jfrog.app.service.trust.dto.ExchangeTokenResponse;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;

import static org.artifactory.rest.resource.servicetrust.ServiceTrustResourceRegisterTokenTest.assertResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * @author igoru
 */
public class ServiceTrustResourceExchangeTokenTest {

    private ServiceTrustService service;

    @Test
    public void whenOkThen200() {
        ServiceTrustResource resource = createResource();
        ExchangeTokenRequest exchangeTokenRequest = new ExchangeTokenRequest("subject", "target");
        ExchangeTokenResponse exchangeTokenResponse = new ExchangeTokenResponse("tokenId", "accessToken", 42L, "scope");
        when(service.exchangePairingToken(any())).thenReturn(exchangeTokenResponse);

        Response response = resource.exchangeToken(exchangeTokenRequest);

        int status = response.getStatus();
        ExchangeTokenResponse actualResponse = ((ExchangeTokenResponse) response.getEntity());
        assertEquals(status, 200);
        assertEquals(actualResponse, exchangeTokenResponse);
    }

    @Test
    public void whenFailedThen500() {
        ServiceTrustResource resource = createResource();
        ExchangeTokenRequest exchangeTokenRequest = new ExchangeTokenRequest("subject", "target");
        doThrow(new RuntimeException("oops")).when(service).exchangePairingToken(any());

        Response response = resource.exchangeToken(exchangeTokenRequest);

        assertResponse(response, 500, "Error while token exchange. oops");
    }

    private ServiceTrustResource createResource() {
        ServiceTrustResource resource = new ServiceTrustResource();
        service = Mockito.mock(ServiceTrustService.class);
        resource.setServiceTrustService(service);
        return resource;
    }

}
