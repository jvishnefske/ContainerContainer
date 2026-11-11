package org.artifactory.ui.rest.resource.onboarding;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.model.onboarding.OnBoardingParams;
import org.artifactory.ui.rest.model.onboarding.SupportedRequestModel;
import org.artifactory.ui.rest.service.onboarding.OnboardingServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Resource for handling UI package type specific quickstart tutorials.
 *
 * @author BarakH
 */
@Path("onboarding/quickstart")
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
public class ArtifactoryQuickStartResource extends BaseResource {

    @Autowired
    private OnboardingServiceFactory onboardingServiceFactory;

    @POST
    @Path("start")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response startSession(OnBoardingParams params) {
        return runService(onboardingServiceFactory.getStartQuickstartSessionService(), params);
    }

    @GET
    @Path("status")
    @Produces(APPLICATION_JSON)
    public Response isAuthenticated(@QueryParam("repoKey") String repoKey) {
        return runService(onboardingServiceFactory.getQuickstartStatusService(), repoKey);
    }

    @GET
    @Path("supported")
    @Produces(APPLICATION_JSON)
    public Response isSupported(@QueryParam("repoKey") String repoKey) {
        return runService(onboardingServiceFactory.getQuickstartSupportedService(), repoKey);
    }

    @POST
    @Path("supported")
    @Produces(APPLICATION_JSON)
    public Response isSupported(SupportedRequestModel repoKeys) {
        return runService(onboardingServiceFactory.getQuickstartSupportedBatchService(), repoKeys);
    }
}
