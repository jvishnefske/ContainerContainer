package org.artifactory.rest.resource.metrics;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.metrics.framework.MetricsFacade;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Path("/v1/metrics")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@ActivityDetails(group = RestApiGroup.SYSTEM_INFO_API)
public class MetricsAPIResource {

    @Autowired
    private MetricsFacade metricsFacade;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @AllowedPermissionScope("system:metrics:r")
    public Response getOpenMetrics() {
        return Response.ok().entity(metricsFacade.getAllOpenMetrics()).build();
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("application")
    @AllowedPermissionScope("system:metrics:r")
    public Response getAppOpenMetrics() {
        return Response.ok().entity(metricsFacade.getApplicationOpenMetrics()).build();
    }
}
