package org.artifactory.rest.resource.admin;

import jakarta.ws.rs.core.Response;
import org.artifactory.aol.AolService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.request.guard.InboundRequestGuardService;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static jakarta.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
@ExtendWith(MockitoExtension.class)
class InboundRequestGuardTriggersResourceTest {

    @Mock
    private AolService aolService;
    @Mock
    private InboundRequestGuardService guardService;

    private InboundRequestGuardTriggersResource resource;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        resource = new InboundRequestGuardTriggersResource(aolService, guardService);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private static InboundRequestGuardTriggersConfig triggers() {
        InboundRequestGuardTriggersConfig config = new InboundRequestGuardTriggersConfig();
        config.setMode(InboundRequestGuardTriggersConfig.Mode.PFW3_ACTIVE);
        config.setHeaderNames(Set.of("X-Bad"));
        config.setUserAgentSubstrings(Set.of("BadTool"));
        return config;
    }

    @Test
    void get_returnsTriggersForDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(true);
        InboundRequestGuardTriggersConfig stored = triggers();
        when(guardService.getInboundRequestGuardTriggersConfig()).thenReturn(stored);

        Response response = resource.get();

        assertEquals(OK.getStatusCode(), response.getStatus());
        assertSame(stored, response.getEntity());
    }

    @Test
    void get_rejectsNonDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> resource.get());

        verify(guardService, never()).getInboundRequestGuardTriggersConfig();
    }

    @Test
    void update_persistsAndReturnsForDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(true);
        InboundRequestGuardTriggersConfig submitted = triggers();
        InboundRequestGuardTriggersConfig stored = triggers();
        when(guardService.getInboundRequestGuardTriggersConfig()).thenReturn(stored);

        Response response = resource.update(submitted);

        verify(guardService).updateTriggers(submitted);
        assertEquals(OK.getStatusCode(), response.getStatus());
        assertSame(stored, response.getEntity());
    }

    @Test
    void update_rejectsNonDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(false);
        InboundRequestGuardTriggersConfig submitted = triggers();

        assertThrows(ForbiddenException.class, () -> resource.update(submitted));

        verify(guardService, never()).updateTriggers(submitted);
    }

    @Test
    void update_translatesIllegalArgumentExceptionFromServiceTo400() {
        InboundRequestGuardTriggersConfig submitted = triggers();
        when(aolService.isDashboardUser()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Invalid IP address or CIDR range: 'nope'"))
                .when(guardService).updateTriggers(submitted);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> resource.update(submitted));

        assertEquals("Invalid IP address or CIDR range: 'nope'", ex.getMessage());
        assertEquals(400, ex.getErrorCode());
    }
}
