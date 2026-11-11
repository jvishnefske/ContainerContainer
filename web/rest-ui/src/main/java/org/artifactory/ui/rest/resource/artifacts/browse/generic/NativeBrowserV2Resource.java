package org.artifactory.ui.rest.resource.artifacts.browse.generic;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Hussein Alshqerat
 */
@Path("v2/nativeBrowser")
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class NativeBrowserV2Resource extends BaseResource {

    public static final String REPO_PATH = "repoPath";

    @Autowired
    BrowseServiceFactory browseFactory;

    @GET
    @Path("{repoPath:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response nativeBrowserModel(@PathParam(REPO_PATH) String repoPath) {
        return runService(browseFactory.browseNativeModelV2());
    }
}