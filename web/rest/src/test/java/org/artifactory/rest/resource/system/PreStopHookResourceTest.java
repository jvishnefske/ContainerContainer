package org.artifactory.rest.resource.system;

import org.artifactory.shutdown.GracefulShutdownService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class PreStopHookResourceTest {

    private GracefulShutdownService service;
    private PreStopHookResource resource;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(GracefulShutdownService.class);
        resource = new PreStopHookResource(service);
        request = Mockito.mock(HttpServletRequest.class);
        when(request.getLocalAddr()).thenReturn("127.0.0.1");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void whenRequestCameNotFromLocalMachineThenItForbidden() {
        when(request.getRemoteAddr()).thenReturn("192.168.0.100");

        Response response = resource.preStop(request);

        assertEquals(response.getStatus(), Response.Status.FORBIDDEN.getStatusCode());
    }

    @ParameterizedTest
    @CsvSource({
            "COMPLETED, OK, All Downloads and Uploads are Successfully Done. Terminating Artifactory",
            "TIMEOUT, REQUEST_TIMEOUT, [Warning] There were Some Downloads or Uploads that Haven't been Finished. " +
                    "Terminating Artifactory",
            "ERROR, INTERNAL_SERVER_ERROR, [Error] Server error while shutdown. Terminating Artifactory",
    })
    void resultMappingTest(GracefulShutdownService.Result result, Response.Status status, String expectedMessage) {
        when(service.waitProcessesCompletion()).thenReturn(result);

        Response response = resource.preStop(request);
        String message = (String) response.getEntity();

        assertEquals(status.getStatusCode(), response.getStatus());
        assertEquals(expectedMessage + "\n", message);
    }
}