package org.artifactory.rest.resource.debug;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.debug.DebugLevelConfig;
import org.artifactory.debug.DebugService;
import org.artifactory.debug.threads.ThreadDumpResponse;
import org.artifactory.util.AolUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_DEBUG;
import static org.artifactory.util.AolUtils.assertDashboardUser;

/**
 * @author Amir Avidan
 */
@Component
@Path(PATH_DEBUG)
@RequiredArgsConstructor
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@Slf4j
public class DebugResource {

    private final DebugService debugService;
    static final String UPDATE_LOG_LEVEL_MESSAGE = "Logger levels configured successfully";
    static final String DELETE_LOG_LEVEL_MESSAGE = "Logger levels were reset to default settings successfully";

    @GET
    @Path("loggers/{className: .+}")
    @Produces(APPLICATION_JSON)
    public Response getDebugLevel(@PathParam("className") String className) {
        AolUtils.assertDashboardUser();
        return Response.ok().entity(debugService.getLogLevelFromClass(className)).build();
    }

    @POST
    @Path("loggers")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response setDebugLevels(DebugLevelConfig debugLevelConfig) {
        AolUtils.assertDashboardUser();
        debugService.changeLogLevel(debugLevelConfig);
        return Response.ok().entity(UPDATE_LOG_LEVEL_MESSAGE).build();
    }

    @DELETE
    @Path("loggers")
    @Produces(APPLICATION_JSON)
    public Response deleteDebugLevel() {
        AolUtils.assertDashboardUser();
        debugService.restoreDebugSettings();
        return Response.ok().entity(DELETE_LOG_LEVEL_MESSAGE).build();
    }

    @GET
    @Path(SystemRestConstants.THREAD_DUMP)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getThreadDumps(@QueryParam("propagate") boolean propagate) throws JsonProcessingException {
        log.debug("Get thread dump. Include all pods: " + propagate);
        assertDashboardUser();
        ThreadDumpResponse threadDumpResponse = debugService.generateThreadDump(propagate);
        return Response.ok()
                .entity(threadDumpResponse.convertToJsonWithLineBreaks())
                .build();
    }

}