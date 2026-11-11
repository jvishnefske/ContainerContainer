package org.artifactory.rest.resource.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.shutdown.GracefulShutdownService;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author igoru
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Path("v1/" + SystemRestConstants.PATH_ROOT)
public class PreStopHookResource {

    private static final Map<GracefulShutdownService.Result, ResourceResponse> RESPONSES;
    static {
        RESPONSES = new EnumMap<>(GracefulShutdownService.Result.class);
        RESPONSES.put(GracefulShutdownService.Result.COMPLETED,
                new ResourceResponse(
                        Response.Status.OK,
                        "All Downloads and Uploads are Successfully Done."));
        RESPONSES.put(GracefulShutdownService.Result.TIMEOUT,
                new ResourceResponse(
                        Response.Status.REQUEST_TIMEOUT,
                        "[Warning] There were Some Downloads or Uploads that Haven't been Finished."));
        RESPONSES.put(GracefulShutdownService.Result.ERROR,
                new ResourceResponse(
                        Response.Status.INTERNAL_SERVER_ERROR,
                        "[Error] Server error while shutdown."));
    }

    private final GracefulShutdownService gracefulShutdownService;

    @GET
    @Path(SystemRestConstants.PATH_PRESTOP)
    @Produces(MediaType.APPLICATION_JSON)
    public Response preStop(@Context HttpServletRequest httpRequest) {
        val localAddr = httpRequest.getLocalAddr();
        val remoteAddr = httpRequest.getRemoteAddr();
        log.info("Remote address {}, local address {}", remoteAddr, localAddr);
        val requestFromLocalHost = localAddr == null || localAddr.equals(remoteAddr);
        if (!requestFromLocalHost) {
            log.warn("preStop hook endpoint accessed from wrong host");
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        val result = gracefulShutdownService.waitProcessesCompletion();
        val response = RESPONSES.get(result);
        return Response.status(response.status()).entity(response.entity() + " Terminating Artifactory\n").build();
    }

    private record ResourceResponse(Response.Status status, String entity){}
}
