package org.artifactory.rest.resource.cleanup;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicyDto;
import org.artifactory.rest.services.cleanup.CleanupPackagePolicyServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.artifactory.rest.cleanup.policies.PolicyRequestValidationUtils.validatePolicyKeys;

/**
 * @author Oleksandr Legenkiy
 */
@Path("cleanup-ui/packages/policies")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Validated
public class CleanupPackagePolicyUIResource extends BaseResource {

    private CleanupPackagePolicyServiceFactory factory;

    public CleanupPackagePolicyUIResource() {
    }

    @Autowired
    public CleanupPackagePolicyUIResource(CleanupPackagePolicyServiceFactory factory) {
        this.factory = factory;
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getPackageCleanupPolicies() {
        return runService(factory.getAllCleanupPackagePolicyService());
    }

    @GET
    @Path("{policyKey}")
    @Produces(APPLICATION_JSON)
    public Response getPackageCleanupPolicy(@PathParam("policyKey") String policyKey) {
        return runService(factory.getCleanupPackagePolicyService(), policyKey);
    }

    @POST
    @Path("{policyKey}")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response createPackageCleanupPolicy(@PathParam("policyKey") String policyKey,
            @Valid PackageCleanupPolicyDto policy) {
        validatePolicyKeys(policyKey, policy.key());
        return runService(factory.createCleanupPackagePolicyService(), policy);
    }

    @PUT
    @Path("{policyKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePackageCleanupPolicy(@PathParam("policyKey") String policyKey,
            @Valid PackageCleanupPolicyDto policy) {
        validatePolicyKeys(policyKey, policy.key());
        return runService(factory.updateCleanupPackagePolicyService(), policy);
    }

    @DELETE
    @Path("{policyKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deletePackageCleanupPolicy(@PathParam("policyKey") String policyKey) {
        return runService(factory.deleteCleanupPackagePolicyService(), policyKey);
    }

}
