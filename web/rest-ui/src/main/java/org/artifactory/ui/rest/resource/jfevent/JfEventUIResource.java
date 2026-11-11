package org.artifactory.ui.rest.resource.jfevent;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.jfevent.JfEventServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Yevdo Abramov
 * Created on 24/05/2020
 */
@Path("events")
@Component
@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
public class JfEventUIResource extends BaseResource {

    private final JfEventServiceFactory jfEventServiceFactory;

    @Autowired
    public JfEventUIResource(JfEventServiceFactory jfEventServiceFactory) {
        this.jfEventServiceFactory = jfEventServiceFactory;
    }

    @GET
    @Path("/domains")
    public Response getDomains() {
        return runService(jfEventServiceFactory.getDomainsList());
    }
}
