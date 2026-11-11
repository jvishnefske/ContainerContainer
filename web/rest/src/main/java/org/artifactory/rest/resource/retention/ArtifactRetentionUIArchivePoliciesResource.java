package org.artifactory.rest.resource.retention;

import org.artifactory.addon.retention.ArtifactRetentionCandidateCriteria;
import org.artifactory.addon.retention.ArtifactRetentionPolicyBaseModel;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.services.retention.ArtifactRetentionArchiveServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_POLICY_KEYS;

/**
 * @author Omri Ziv
 */
@Path("artifactRetention/archive/policies")
@Component
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionUIArchivePoliciesResource extends BaseResource {

    @Autowired
    private ArtifactRetentionArchiveServiceFactory artifactRetentionArchiveServiceFactory;

    @GET
    @Produces(APPLICATION_JSON)
    public Response getAllPolicies() {
        return runService(artifactRetentionArchiveServiceFactory.getAllRetentionPolicyService());
    }

    @GET
    @Path("{policyKey}")
    @Produces(APPLICATION_JSON)
    public Response getPolicy() {
        return runService(artifactRetentionArchiveServiceFactory.getRetentionPolicyService());
    }

    @POST
    @Path("{policyKey}")
    @Consumes(APPLICATION_JSON)
    public Response createPolicy(ArtifactRetentionPolicyBaseModel policy) {
        return runService(artifactRetentionArchiveServiceFactory.createRetentionPolicyService(), policy);
    }

    @DELETE
    @Path("{policyKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deletePolicy() {
        return runService(artifactRetentionArchiveServiceFactory.deleteRetentionPolicyService());
    }

    @DELETE
    public Response deletePolicies(@QueryParam(PARAM_POLICY_KEYS) String policyKeys) {
        return runService(artifactRetentionArchiveServiceFactory.deleteRetentionPoliciesService(), policyKeys);
    }

    @PUT
    @Path("{policyKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePolicy(ArtifactRetentionPolicyBaseModel policy) {
        return runService(artifactRetentionArchiveServiceFactory.updateRetentionPolicyService(), policy);
    }

    @POST
    @Path("preview")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response preview(ArtifactRetentionPolicyBaseModel policy) {
        return runService(artifactRetentionArchiveServiceFactory.previewRetentionPolicyService(), policy);
    }

    @POST
    @Path("convertToAql")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response convertSearchCriteriaFormToAql(ArtifactRetentionCandidateCriteria searchCriteriaModel) {
        return runService(artifactRetentionArchiveServiceFactory.convertPolicySearchCriteriaToAqlService(),
                searchCriteriaModel);
    }

    @POST
    @Path("{policyKey}/trigger")
    public Response triggerPolicy(@PathParam("policyKey") String policyKey) {
        return runService(artifactRetentionArchiveServiceFactory.getArtifactRetentionTriggerPolicyService());
    }

    @POST
    @Path("{policyKey}/preview")
    @Produces(APPLICATION_JSON)
    public Response previewByPolicyKey(@PathParam("policyKey") String policyKey) {
        return runService(artifactRetentionArchiveServiceFactory.getArtifactRetentionPreviewByPolicyKeyService());
    }

    @POST
    @Path("{policyKey}/stop")
    public Response stopPolicy(@PathParam("policyKey") String policyKey) {
        return runService(artifactRetentionArchiveServiceFactory.getArtifactRetentionStopPolicyService());
    }
}