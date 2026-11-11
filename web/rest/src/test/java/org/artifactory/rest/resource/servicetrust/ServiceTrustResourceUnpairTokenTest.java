package org.artifactory.rest.resource.servicetrust;

import org.artifactory.servicetrust.ServiceTrustService;
import org.artifactory.servicetrust.ServiceTrustUseCase;
import org.jfrog.app.service.trust.dto.MessageResponse;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import jakarta.ws.rs.core.Response;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class ServiceTrustResourceUnpairTokenTest {
    private ServiceTrustService service;

    @Test
    public void whenUseCaseIsWrongThen404() {
        ServiceTrustResource resource = createResuource();
        when(service.findUseCase(any())).thenReturn(Optional.empty());
        Response response =
                resource.unregisterToken("wrong-use-case", new BaseUrlModel("http://coldinstanceurl"), false);
        assertResponse(response, 404, "Unknown use case");
    }

    @Test
    public void whenOkThen200() {
        ServiceTrustResource resource = createResuource();
        when(service.findUseCase(any())).thenReturn(Optional.of(ServiceTrustUseCase.ARTIFACTORY_COLD));
        Response response =
                resource.unregisterToken("artifactory-cold", new BaseUrlModel("http://coldinstanceurl"), false);
        assertResponse(response, 200, "Trust with service artifactory-cold was canceled successfully");
    }

    @Test
    public void whenBaseUrlIsSetToNullThenFailed400() {
        ServiceTrustResource resource = createResuource();
        when(service.findUseCase(any())).thenReturn(Optional.of(ServiceTrustUseCase.ARTIFACTORY_COLD));
        Response response = resource.unregisterToken("artifactory-cold", new BaseUrlModel(null), false);
        assertResponse(response, 400, "Error while cancelling token pairing. Base url is not provided");
    }

    @Test
    public void whenBaseUrlModelIsSetToNullThenFailed400() {
        ServiceTrustResource resource = createResuource();
        when(service.findUseCase(any())).thenReturn(Optional.of(ServiceTrustUseCase.ARTIFACTORY_COLD));
        Response response = resource.unregisterToken("artifactory-cold", null, false);
        assertResponse(response, 400, "Error while cancelling token pairing. Base url is not provided");
    }

    @Test
    public void whenForceDeleteThen200EvenIfException() {
        ServiceTrustResource resource = createResuource();
        when(service.findUseCase(any())).thenReturn(Optional.of(ServiceTrustUseCase.ARTIFACTORY_COLD));
        // Simulate exception thrown by service, but force=true should still succeed
        Mockito.doThrow(new RuntimeException("Failed calling revoke token endpoint")).when(service)
                .unpairToken(any(), any(), any(), Mockito.eq(false));
        Mockito.doNothing().when(service).unpairToken(any(), any(), any(), Mockito.eq(true));
        Response response =
                resource.unregisterToken("artifactory-cold", new BaseUrlModel("http://coldinstanceurl"), true);
        // Should still return 200 because force=true
        assertEquals(response.getStatus(), 200);
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