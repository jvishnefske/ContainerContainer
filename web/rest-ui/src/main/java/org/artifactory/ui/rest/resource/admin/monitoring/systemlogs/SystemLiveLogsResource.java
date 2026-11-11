package org.artifactory.ui.rest.resource.admin.monitoring.systemlogs;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.admin.advanced.AdvancedServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Hezi Cohen
 */

@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("v1/system/logs")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SystemLiveLogsResource extends BaseResource {
    @Autowired
    protected AdvancedServiceFactory advanceFactory;

    @GET
    @Path("data")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLogData() {
        return runService(advanceFactory.getSystemLogData());
    }

    @GET
    @Path("config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLogConfig() {
        return runService(advanceFactory.getSystemLogConfig());
    }
}
