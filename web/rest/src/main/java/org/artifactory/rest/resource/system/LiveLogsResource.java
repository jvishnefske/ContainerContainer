package org.artifactory.rest.resource.system;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.livelogs.LiveLogsService;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Andrey Tashlitsky
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(SystemRestConstants.PATH_LOGS)
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@AllowedPermissionScope("system:livelogs:r")
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class LiveLogsResource {

    private final LiveLogsService liveLogsService;

    @Autowired
    public LiveLogsResource(LiveLogsService liveLogsService) {
        this.liveLogsService = liveLogsService;
    }

    @GET
    @Path(SystemRestConstants.PATH_CONFIG)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLogConfig() {
        liveLogsService.assertLiveLogsFeatureIsEnabled();
        return Response.ok().entity(liveLogsService.getLiveLogsConfig()).build();
    }

    @GET
    @Path(SystemRestConstants.PATH_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLogData(@QueryParam("id") String selectedLog, @QueryParam("file_size") long lastPointer) {
        liveLogsService.assertCanGetLogData(selectedLog);
        return Response.ok().entity(liveLogsService.getLiveLogsData(selectedLog, lastPointer)).build();
    }
}
