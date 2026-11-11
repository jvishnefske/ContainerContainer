package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.views;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.alpine.AlpineArtifactInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Aiswarya NJ
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Path("views/alpine")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ViewAlpineResource {

    private final PackageResourceService packageResourceService;

    @Autowired
    public ViewAlpineResource(PackageResourceService packageResourceService) {
        this.packageResourceService = packageResourceService;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewAlpine(AlpineArtifactInfo alpineArtifactInfo) throws PackageException {
        return packageResourceService.getPackageInfo(alpineArtifactInfo.getRepoKey(), alpineArtifactInfo.getPath());
    }

}
