package org.artifactory.rest.resource.federation;

import jakarta.ws.rs.core.Response;
import org.artifactory.api.security.SecurityService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.federation.FederatedReplaceURLService;
import org.artifactory.model.ReplaceUrlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
class FederatedReplaceUrlResourceTest {
    private FederatedReplaceUrlResource resource;
    @Mock
    private FederatedReplaceURLService service;
    @Mock
    private SecurityService securityService;
    AutoCloseable closeable;

    public static Stream<Arguments> exceptions() {
        return Stream.of(Arguments.of(new ConfigurationException("error"), SC_BAD_REQUEST, "Urls incorrect"),
                Arguments.of(new IllegalStateException("error"), SC_BAD_REQUEST, "Replace URL is unavailable"));
    }

    public static Stream<Arguments> replaceUrlExceptions() {
        return Stream.of(
                Arguments.of(new ConfigurationException("error"), SC_BAD_REQUEST, "Urls incorrect"),
                Arguments.of(new IllegalStateException("error"), SC_BAD_REQUEST, "Replace URL is unavailable"),
                Arguments.of(new RuntimeException("generic error"), SC_INTERNAL_SERVER_ERROR, "Failed to replace URL")
        );
    }

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        resource = new FederatedReplaceUrlResource();
        resource.setFederatedReplaceURLService(service);
        resource.setSecurityService(securityService);
    }

    @AfterEach
    void teardown() throws Exception {
        closeable.close();
    }

    @Test
    void testReplaceUrl() throws Exception {
        when(securityService.callAsSystem(any())).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            return callable.call();
        });

        resource.replaceUrl(mock(ReplaceUrlConfig.class));
        verify(service).replaceUrlAndQueues(any(), any(), eq(true));
    }

    @Test
    void testUpdateMemberUrl() {
        resource.updateMemberUrl(mock(ReplaceUrlConfig.class));
        verify(service).replaceUrlAndQueues(any(), any(), eq(false));
    }

    @Test
    void testReplaceUrlAsAdmin() throws Exception {
        when(securityService.isAdmin()).thenReturn(true);
        ReplaceUrlConfig config = mock(ReplaceUrlConfig.class);
        Response response = resource.replaceUrl(config);
        verify(service).replaceUrlAndQueues(any(), any(), eq(true));
        verify(securityService).isAdmin();
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void updateMemberUrlExceptions(Exception e, int status, String expected) {
        doThrow(e).when(service).replaceUrlAndQueues(any(), any(), anyBoolean());
        Response res = resource.updateMemberUrl(mock(ReplaceUrlConfig.class));
        assertTrue(res.getEntity().toString().contains(expected));
        assertEquals(status, res.getStatus());
    }

    @ParameterizedTest
    @MethodSource("replaceUrlExceptions")
    void replaceUrlElevatedExceptions(Exception e, int status, String expected) throws Exception {
        when(securityService.callAsSystem(any())).thenAnswer(invocation -> {
            Callable<?> callable = invocation.getArgument(0);
            return callable.call();
        });

        doThrow(e).when(service).replaceUrlAndQueues(any(), any(), anyBoolean());
        Response res = resource.replaceUrl(mock(ReplaceUrlConfig.class));
        assertTrue(res.getEntity().toString().contains(expected));
        assertEquals(status, res.getStatus());
    }
}