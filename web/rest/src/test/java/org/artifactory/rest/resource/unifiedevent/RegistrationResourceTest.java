package org.artifactory.rest.resource.unifiedevent;

import jakarta.ws.rs.core.Response;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.resource.unifiedevent.dto.ConsumerRegistrationRequest;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.registry.RegistrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationResourceTest {
    @Mock
    RegistrationService registrationService;
    private RegistrationResource registrationResource;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        registrationResource = new RegistrationResource(registrationService);
    }

    @AfterEach
    void unbind() throws Exception {
        mocks.close();
    }

    @Test
    void testSetRegistration() {
        ConsumerRegistrationRequest registrationRequest = mock(ConsumerRegistrationRequest.class);
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String url = "http://test-consumer-url";
        when(registrationRequest.name()).thenReturn(consumerName);
        when(registrationRequest.url()).thenReturn(url);
        when(registrationRequest.event()).thenReturn(null);
        doNothing().when(registrationService)
                .registerConsumer(eq(consumerName), eq(url), eq(true), any());
        Response response = registrationResource.setRegistration(registrationRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Successfully updated consumer registration", response.getEntity());
        verify(registrationService).registerConsumer(eq(consumerName), eq(url), eq(true), any());
    }

    @Test
    void testSetRegistration_BadRequestForWorkerClient() {
        ConsumerRegistrationRequest registrationRequest = mock(ConsumerRegistrationRequest.class);
        SupportedConsumers consumerName = SupportedConsumers.WORKER_CLIENT;
        String url = "http://test-consumer-url";
        when(registrationRequest.name()).thenReturn(consumerName);
        when(registrationRequest.url()).thenReturn(url);
        when(registrationRequest.event()).thenReturn(null);
        assertThrows(BadRequestException.class, () -> registrationResource.setRegistration(registrationRequest));
    }

    @Test
    void testUpdateRegistration() {
        ConsumerRegistrationRequest registrationRequest = mock(ConsumerRegistrationRequest.class);
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        String url = "http://test-consumer-url";
        when(registrationRequest.name()).thenReturn(consumerName);
        when(registrationRequest.url()).thenReturn(url);
        when(registrationRequest.event()).thenReturn(null);
        doNothing().when(registrationService)
                .registerConsumer(eq(consumerName), eq(url), eq(false), any());
        Response response = registrationResource.updateRegistration(registrationRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Successfully updated consumer registration", response.getEntity());
        verify(registrationService).registerConsumer(eq(consumerName), eq(url), eq(false), any());
    }

    @Test
    void testUpdateRegistration_BadRequestForWorkerClient() {
        SupportedConsumers consumerName = SupportedConsumers.WORKER_CLIENT;
        String url = "http://test-consumer-url";
        ConsumerRegistrationRequest registrationRequest = ConsumerRegistrationRequest.builder()
                .name(consumerName)
                .url(url)
                .build();
        assertThrows(BadRequestException.class, () -> registrationResource.updateRegistration(registrationRequest));
    }

    @Test
    void testDeleteRegistration() {
        SupportedUnifiedEvents event = SupportedUnifiedEvents.BEFORE_DOWNLOAD;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        doNothing().when(registrationService)
                .unregisterConsumer(eq(event), eq(consumerName));
        Response response = registrationResource.deleteRegistration(event, consumerName);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());
        verify(registrationService).unregisterConsumer(eq(event), eq(consumerName));
    }

    @Test
    void testDeleteRegistration_BadRequestForWorkerClient() {
        SupportedUnifiedEvents event = SupportedUnifiedEvents.BEFORE_DOWNLOAD;
        SupportedConsumers consumerName = SupportedConsumers.WORKER_CLIENT;
        assertThrows(BadRequestException.class, () -> registrationResource.deleteRegistration(event, consumerName));
    }

    @Test
    void testGetRegistration() {
        SupportedUnifiedEvents event = SupportedUnifiedEvents.BEFORE_DOWNLOAD;
        SupportedConsumers consumerName = SupportedConsumers.WORKER;
        Response response = registrationResource.getRegistration(event, consumerName);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }
}
