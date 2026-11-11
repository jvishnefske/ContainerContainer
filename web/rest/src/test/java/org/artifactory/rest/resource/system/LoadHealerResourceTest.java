package org.artifactory.rest.resource.system;

import jakarta.ws.rs.core.Response;
import org.artifactory.aol.AolService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.throttling.lh.mode.LoadHealerMode;
import org.artifactory.throttling.lh.mode.ModeUpdateResult;
import org.artifactory.throttling.lh.mode.ModeUpdateStatus;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class LoadHealerResourceTest {

    @Mock
    private AolService aolService;

    @Mock
    private LoadHealerService loadHealerService;

    @InjectMocks
    private LoadHealerResource resource;

    @Test
    void testSetModeSuccess() {
        when(aolService.isDashboardUser()).thenReturn(true);
        Instant revertAt = Instant.now().plus(Duration.ofDays(1));
        when(loadHealerService.updateMode(eq("PROACTIVE"), any(Instant.class)))
                .thenReturn(new ModeUpdateResult(ModeUpdateStatus.UPDATED, revertAt));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 1440);

        Response response = resource.setMode(request);

        assertEquals(200, response.getStatus());
        LoadHealerResource.ModeUpdateResponse entity = (LoadHealerResource.ModeUpdateResponse) response.getEntity();
        assertEquals(ModeUpdateStatus.UPDATED, entity.status());
        assertEquals("PROACTIVE", entity.mode());
        assertEquals(revertAt.toString(), entity.revertAt());
    }

    @Test
    void testSetModeWithDifferentDuration() {
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.updateMode(eq("SIMULATION"), any(Instant.class)))
                .thenReturn(
                        new ModeUpdateResult(ModeUpdateStatus.UPDATED, Instant.now().plus(Duration.ofMinutes(720))));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.SIMULATION, 720);

        Response response = resource.setMode(request);

        assertEquals(200, response.getStatus());
        verify(loadHealerService).updateMode(eq("SIMULATION"), any(Instant.class));
    }

    @Test
    void testSetModeForbiddenForNonDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(false);
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 1440);

        assertThrows(ForbiddenException.class, () -> resource.setMode(request));
    }

    @Test
    void testSetModeNullRequest() {
        when(aolService.isDashboardUser()).thenReturn(true);

        assertThrows(BadRequestException.class, () -> resource.setMode(null));
    }

    @Test
    void testSetModeNullMode() {
        when(aolService.isDashboardUser()).thenReturn(true);
        LoadHealerModeRequest request = new LoadHealerModeRequest(null, 1440);

        assertThrows(BadRequestException.class, () -> resource.setMode(request));
    }

    @Test
    void testSetModeWithMaxDuration() {
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.updateMode(eq("PROACTIVE"), any(Instant.class)))
                .thenReturn(
                        new ModeUpdateResult(ModeUpdateStatus.UPDATED, Instant.now().plus(Duration.ofMinutes(4320))));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 4320);

        Response response = resource.setMode(request);

        assertEquals(200, response.getStatus());
        verify(loadHealerService).updateMode(eq("PROACTIVE"), any(Instant.class));
    }

    @Test
    void testSetModeWithZeroDurationUsesDefault() {
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.updateMode(eq("REACTIVE"), any(Instant.class)))
                .thenReturn(new ModeUpdateResult(ModeUpdateStatus.UPDATED, Instant.now().plus(Duration.ofDays(1))));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.REACTIVE, 0);

        Response response = resource.setMode(request);

        assertEquals(200, response.getStatus());
        verify(loadHealerService).updateMode(eq("REACTIVE"), any(Instant.class));
    }

    @Test
    void testSetModeExceedsMaxDurationCapsAt4320() {
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.updateMode(eq("PROACTIVE"), any(Instant.class)))
                .thenReturn(
                        new ModeUpdateResult(ModeUpdateStatus.UPDATED, Instant.now().plus(Duration.ofMinutes(4320))));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 5000);

        Response response = resource.setMode(request);

        assertEquals(200, response.getStatus());
        verify(loadHealerService).updateMode(eq("PROACTIVE"), any(Instant.class));
    }

    @Test
    void testGetModeSuccess() {
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.getCurrentMode()).thenReturn("REACTIVE");

        Response response = resource.getMode();

        assertEquals(200, response.getStatus());
        LoadHealerResource.LoadHealerModeResponse modeResponse =
                (LoadHealerResource.LoadHealerModeResponse) response.getEntity();
        assertEquals("REACTIVE", modeResponse.mode());
        verify(loadHealerService).getCurrentMode();
    }

    @Test
    void testGetModeForbiddenForNonDashboardUser() {
        when(aolService.isDashboardUser()).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> resource.getMode());
    }

    @Test
    void testGetModeReturnsProactiveMode() {
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.getCurrentMode()).thenReturn("PROACTIVE");

        Response response = resource.getMode();

        assertEquals(200, response.getStatus());
        LoadHealerResource.LoadHealerModeResponse modeResponse =
                (LoadHealerResource.LoadHealerModeResponse) response.getEntity();
        assertEquals("PROACTIVE", modeResponse.mode());
    }

    @Test
    void testGetModeReturnsSimulationMode() {
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.getCurrentMode()).thenReturn("SIMULATION");

        Response response = resource.getMode();

        assertEquals(200, response.getStatus());
        LoadHealerResource.LoadHealerModeResponse modeResponse =
                (LoadHealerResource.LoadHealerModeResponse) response.getEntity();
        assertEquals("SIMULATION", modeResponse.mode());
    }

    @Test
    void testModeRequestRecordValidatesMaxDuration() {
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 5000);
        assertEquals(4320, request.durationMinutes());
    }

    @Test
    void testModeRequestRecordValidatesMinDuration() {
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, -5);
        assertEquals(1440, request.durationMinutes());
    }

    @Test
    void testModeRequestRecordPreservesValidDuration() {
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 2880);
        assertEquals(2880, request.durationMinutes());
    }

    @Test
    void testSetModeReturnsModeAlreadyActiveStatus() {
        when(aolService.isDashboardUser()).thenReturn(true);
        Instant existingRevertAt = Instant.now().plus(Duration.ofMinutes(30));
        when(loadHealerService.updateMode(eq("PROACTIVE"), any(Instant.class)))
                .thenReturn(new ModeUpdateResult(ModeUpdateStatus.ALREADY_ACTIVE, existingRevertAt));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 1440);

        Response response = resource.setMode(request);

        assertEquals(200, response.getStatus());
        LoadHealerResource.ModeUpdateResponse entity = (LoadHealerResource.ModeUpdateResponse) response.getEntity();
        assertEquals(ModeUpdateStatus.ALREADY_ACTIVE, entity.status());
        assertEquals("PROACTIVE", entity.mode());
        assertEquals(existingRevertAt.toString(), entity.revertAt());
    }

    @Test
    void testSetModeWithLaterRevertAtReturnsExtendedStatus() {
        when(aolService.isDashboardUser()).thenReturn(true);
        Instant extendedRevertAt = Instant.now().plus(Duration.ofDays(3));
        when(loadHealerService.updateMode(eq("PROACTIVE"), any(Instant.class)))
                .thenReturn(new ModeUpdateResult(ModeUpdateStatus.EXTENDED, extendedRevertAt));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 4320);

        Response response = resource.setMode(request);

        assertEquals(200, response.getStatus());
        LoadHealerResource.ModeUpdateResponse entity = (LoadHealerResource.ModeUpdateResponse) response.getEntity();
        assertEquals(ModeUpdateStatus.EXTENDED, entity.status());
        assertEquals("PROACTIVE", entity.mode());
        assertEquals(extendedRevertAt.toString(), entity.revertAt());
    }

    @Test
    void testSetModeReturns400OnServiceFailure() {
        // Service-layer failures (e.g. DB write errors) return 400 with a structured error body;
        // the warn log carries the mode + durationMinutes context that the body intentionally
        // does not expose to the caller.
        when(aolService.isDashboardUser()).thenReturn(true);
        when(loadHealerService.updateMode(eq("PROACTIVE"), any(Instant.class)))
                .thenThrow(new RuntimeException("DB write failed"));
        LoadHealerModeRequest request = new LoadHealerModeRequest(LoadHealerMode.PROACTIVE, 1440);

        Response response = resource.setMode(request);

        assertEquals(400, response.getStatus());
        LoadHealerResource.ErrorResponse entity = (LoadHealerResource.ErrorResponse) response.getEntity();
        assertEquals("Failed to update Load Healer mode", entity.error());
    }
}
