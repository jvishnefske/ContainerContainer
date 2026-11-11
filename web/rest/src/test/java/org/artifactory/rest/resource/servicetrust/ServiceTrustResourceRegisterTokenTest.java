package org.artifactory.rest.resource.servicetrust;

import org.artifactory.servicetrust.ServiceTrustService;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.jfrog.app.service.trust.dto.MessageResponse;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ServiceTrustResourceRegisterTokenTest {

    private ServiceTrustService service;

    @Test
    public void whenUseCaseIsWrongThen404() {
        ServiceTrustResource resource = createResuource();
        PairingToken pairingToken = new PairingToken("token");
        when(service.findUseCase(any())).thenReturn(Optional.empty());

        Response response = resource.registerToken("wrong-use-case", pairingToken);

        assertResponse(response, 404, "Unknown use case");
    }

    @Test
    public void whenOkThen200() {
        ServiceTrustResource resource = createResuource();
        PairingToken pairingToken = new PairingToken("token");
        when(service.findUseCase(any())).thenReturn(Optional.of(ServiceTrustUseCase.ARTIFACTORY_COLD));
        Response response = resource.registerToken("artifactory-cold", pairingToken);

        assertResponse(response, 200, "Trust with service artifactory-cold was established successfully");
    }

    @Test
    public void whenFailedThen500() throws IOException {
        ServiceTrustResource resource = createResuource();
        PairingToken pairingToken = new PairingToken("token");
        when(service.findUseCase(any())).thenReturn(Optional.of(ServiceTrustUseCase.ARTIFACTORY_COLD));
        doThrow(new RuntimeException("oops")).when(service).pairToken(any(), any());

        Response response = resource.registerToken("artifactory-cold", pairingToken);

        assertResponse(response, 500, "Error while token pairing. oops");
    }

    private ServiceTrustResource createResuource() {
        ServiceTrustResource resource = new ServiceTrustResource();
        service = Mockito.mock(ServiceTrustService.class);
        resource.setServiceTrustService(service);
        return resource;
    }

    static void assertResponse(Response response, int expectedStatus, String expectedMessage) {
        int status = response.getStatus();
        String message = ((MessageResponse) response.getEntity()).message();
        assertEquals(status, expectedStatus);
        assertEquals(message, expectedMessage);
    }

}
