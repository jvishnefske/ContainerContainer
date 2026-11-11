package org.artifactory.rest.resource.retention;

import org.artifactory.api.rest.retention.ArtifactRetentionRestoreRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.services.retention.ArtifactRetentionArchiveServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.artifactory.api.rest.constant.ArtifactRetentionRestConstants.ARCHIVE_STORAGE_SUMMARY;
import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_IN_RANGE_FROM;
import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_IN_RANGE_TO;
import static org.artifactory.api.rest.constant.SearchRestConstants.PARAM_SEARCH_STRING;

/**
 * @author Omri Ziv
 */
@Path("artifactRetention")
@Component
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ArtifactRetentionUIResource extends BaseResource {

    @Autowired
    private ArtifactRetentionArchiveServiceFactory artifactRetentionArchiveServiceFactory;

    @POST
    @Path("restore")
    @Consumes(APPLICATION_JSON)
    public Response restore(ArtifactRetentionRestoreRequest restoreRequest) {
        return runService(artifactRetentionArchiveServiceFactory.retentionRestoreService(), restoreRequest);
    }

    @GET
    @Path("restore/executions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response monitorRestore(@QueryParam(PARAM_IN_RANGE_FROM) @Nullable String fromDate,
            @QueryParam(PARAM_IN_RANGE_TO) @Nullable String toDate) {
        return runService(artifactRetentionArchiveServiceFactory.getRetentionRestorePoliciesExecutionsService());
    }

    @GET
    @Path("archive/executions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response monitorArchives(@QueryParam(PARAM_IN_RANGE_FROM) @Nullable String fromDate,
            @QueryParam(PARAM_IN_RANGE_TO) @Nullable String toDate,
            @QueryParam(PARAM_SEARCH_STRING) @Nullable String searchStr) {
        return runService(artifactRetentionArchiveServiceFactory.getRetentionArchivePoliciesExecutionsService());
    }

    @POST
    @Path("search")
    public Response search(String aqlStr) {
        return runService(artifactRetentionArchiveServiceFactory.getArtifactRetentionSearchService(), aqlStr);
    }

    @GET
    @Path(ARCHIVE_STORAGE_SUMMARY)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getArchiveStorageSummary() {
        return runService(artifactRetentionArchiveServiceFactory.getArtifactRetentionArchiveStorageSummaryService());
    }

    @GET
    @Path("link")
    @Produces(APPLICATION_JSON)
    public Response getArchiveStorageLink() {
        return runService(artifactRetentionArchiveServiceFactory.getArtifactRetentionStorageLinkService());
    }

    @GET
    @Path("repositories")
    @Produces(APPLICATION_JSON)
    public Response getArchiveRepositories() {
        return runService(artifactRetentionArchiveServiceFactory.getArtifactRetentionArchiveRepositoriesService());
    }
}
