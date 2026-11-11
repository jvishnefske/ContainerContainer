package org.artifactory.rest.resource.system;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aol.AolService;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.throttling.lh.mode.ModeUpdateResult;
import org.artifactory.throttling.lh.mode.ModeUpdateStatus;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static java.time.temporal.ChronoUnit.MINUTES;

/**
 * @author Amir Avidan
 */
@Slf4j
@Component
@Path(SystemRestConstants.PATH_ROOT + "/loadhealer")
@AllArgsConstructor
public class LoadHealerResource {

    private final AolService aolService;
    private final LoadHealerService loadHealerService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setMode(LoadHealerModeRequest request) {
        if (!aolService.isDashboardUser()) {
            throw new ForbiddenException("Only dashboard users can modify Load Healer mode");
        }
        if (request == null || request.mode() == null) {
            throw new BadRequestException("Mode is required");
        }
        String requestedMode = request.mode().name();
        int durationMinutes = request.durationMinutes();
        try {
            Instant revertAt = Instant.now().plus(durationMinutes, MINUTES);
            ModeUpdateResult result = loadHealerService.updateMode(requestedMode, revertAt);
            String revertAtStr = result.revertAt() != null ? result.revertAt().toString() : null;
            ModeUpdateResponse response = new ModeUpdateResponse(result.status(), requestedMode, revertAtStr);
            return Response.ok().entity(response).build();
        } catch (Exception e) {
            log.warn("Failed to update Load Healer mode: mode={}, durationMinutes={}", requestedMode, durationMinutes,
                    e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Failed to update Load Healer mode"))
                    .build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMode() {
        if (!aolService.isDashboardUser()) {
            throw new ForbiddenException("Only dashboard users can view Load Healer mode");
        }
        String currentMode = loadHealerService.getCurrentMode();
        return Response.ok().entity(new LoadHealerModeResponse(currentMode)).build();
    }

    record ModeUpdateResponse(ModeUpdateStatus status, String mode, String revertAt) {
    }

    record LoadHealerModeResponse(String mode) {
    }

    record ErrorResponse(String error) {
    }
}
