package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.views;

import com.jfrog.ph.acl.exception.PackageException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.profile.PackageProfile;
import org.artifactory.ph.PackageResourceService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.BaseArtifactInfo;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Arrays;

/**
 * @author Natalie Berni
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Path("views/swift")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ViewSwiftResource {
    private boolean legacyProfile;
    private final PackageResourceService packageResourceService;
    private final BrowseServiceFactory browseFactory;
    private final ViewsResource viewsResource;

    @Autowired
    public ViewSwiftResource(BrowseServiceFactory browseServiceFactory, ViewsResource viewsResource,
            ConfigurableEnvironment env, PackageResourceService packageResourceService) {
        this.browseFactory = browseServiceFactory;
        this.viewsResource = viewsResource;
        this.legacyProfile = !Arrays.asList(env.getActiveProfiles()).contains(PackageProfile.NEW_SWIFT_IMPL);
        this.packageResourceService = packageResourceService;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response viewSwift(BaseArtifactInfo swiftArtifactInfo, @Context HttpServletRequest servletRequest,
            @Context HttpServletResponse servletResponse, @Context Request request, @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders) throws PackageException {
        if (legacyProfile) {
            return viewsResource.runService(browseFactory.swiftViewService(), swiftArtifactInfo, servletRequest,
                    servletResponse, request, uriInfo, httpHeaders);
        } else {
            return packageResourceService.getPackageInfo(swiftArtifactInfo.getRepoKey(), swiftArtifactInfo.getPath());
        }
    }
}
