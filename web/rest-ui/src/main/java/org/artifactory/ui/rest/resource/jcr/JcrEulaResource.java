package org.artifactory.ui.rest.resource.jcr;

import org.apache.http.HttpStatus;
import org.artifactory.addon.eula.EulaService;
import org.artifactory.api.security.AuthorizationService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Omri Ziv
 */
@Path("jcr/eula/")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class JcrEulaResource {

    private EulaService jcrEulaService;

    public JcrEulaResource(EulaService jcrEulaService) {
        this.jcrEulaService = jcrEulaService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getEula() {
        byte[] eula = jcrEulaService.getEulaFile();
        String content = new String(eula);
        return Response.status(HttpStatus.SC_OK)
                .entity(new EulaFile(content)).build();
    }

    @GET
    @Path("required")
    @Produces(MediaType.APPLICATION_JSON)
    public Response isRequired() {
        boolean required = jcrEulaService.isRequired();
        return Response.ok().entity(new EulaRequired(required)).build();
    }

    @POST
    @Path("accept")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response accept() {
        jcrEulaService.accept();
        return Response.ok().build();
    }
}

