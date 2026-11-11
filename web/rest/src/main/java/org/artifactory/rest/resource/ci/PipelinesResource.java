package org.artifactory.rest.resource.ci;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.pipelines.InternalPipelinesService;
import org.artifactory.projects.ProjectsService;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

import static org.apache.http.HttpStatus.SC_OK;

/**
 * @author Alexei Vainshtein
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("pipelines")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class PipelinesResource {
    @Context
    private HttpServletRequest httpRequest;

    @PUT
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    @Path("{pathToFile: .+}")
    public Response addFile(@PathParam(("pathToFile")) String fileName, @QueryParam("pipeRepo") String pipeRepo,
            @QueryParam(ProjectsService.PROJECT_KEY) String projectKey) throws IOException, RepoRejectException {
        InternalPipelinesService pipelinesService =
                ContextHelper.get().beanForType(InternalPipelinesService.class);
        pipelinesService.saveContent(fileName, pipeRepo, projectKey, httpRequest.getInputStream());
        return Response.status(SC_OK).build();
    }
}
