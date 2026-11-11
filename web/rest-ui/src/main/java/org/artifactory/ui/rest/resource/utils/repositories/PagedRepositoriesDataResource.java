package org.artifactory.ui.rest.resource.utils.repositories;

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.service.utils.UtilsServiceFactory;
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

import static org.artifactory.ui.rest.resource.utils.UtilsResourcesConstants.PATH_PAGED_REPODATA;

/**
 * @author Dvir Twina
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@Component
@Path(PATH_PAGED_REPODATA)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class PagedRepositoriesDataResource extends BaseResource {

    private UtilsServiceFactory utilsFactory;
    private SecurityService securityService;

    @Autowired
    public void setUtilsFactory(UtilsServiceFactory utilsFactory) {
        this.utilsFactory = utilsFactory;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * Gets the list of local, remote and federated (according to params) repos for the Permission Repositories page.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPagedRepoData() {
        if (!securityService.isAdmin() && !securityService.isResourceManager()) {
            throw new AuthorizationRestException();
        }
        return runService(utilsFactory.getAllRepositoriesPagedService());
    }

    @Override
    @VisibleForTesting
    protected <Y> Response runService(RestService<Y> service) {
        return super.runService(service);
    }
}
