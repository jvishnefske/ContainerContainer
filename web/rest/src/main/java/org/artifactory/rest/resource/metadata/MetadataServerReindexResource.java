package org.artifactory.rest.resource.metadata;

import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.metadata.service.MetadataEventService;
import org.artifactory.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_METADATA_SERVER;

/**
 * Re-indexes artifact metadata in the metadata server
 *
 * @author Uriah Levy
 */
@Component
@Path(PATH_METADATA_SERVER)
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
public class MetadataServerReindexResource {
    private static final Logger log = LoggerFactory.getLogger(MetadataServerReindexResource.class);

    @Path("reindex")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @POST
    public Response reindexMetadataByPaths(MetadataServerReindexModel reindexModel,
            @DefaultValue("false") @QueryParam("delete_first") Boolean deleteFirst,
            @DefaultValue("true") @QueryParam("async") Boolean async) {
        if (!ConstantValues.metadataServerEventsEnabled.getBoolean()) {
            return Response.status(Response.Status.NOT_FOUND).entity("Metadata Server Integration is disabled").build();
        }
        if (reindexModel == null || CollectionUtils.isNullOrEmpty(reindexModel.getPaths())) {
            String message = "Reindex paths are missing in the request. No reindexing will be done.";
            log.error(message);
            return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
        }
        MetadataEventService metadataEventService = ContextHelper.get().beanForType(MetadataEventService.class);
        if (Boolean.TRUE.equals(async)) {
            log.info(
                    "Received asynchronous Metadata Server re-indexing request on paths {}. Re-indexing will run in " +
                            "the background",
                    reindexModel.getPaths());
            metadataEventService.reindexAsync(reindexModel.getPaths(), deleteFirst);
            return Response.accepted().build();
        } else {
            log.info("Received synchronous Metadata Server re-indexing request on paths {}.",
                    reindexModel.getPaths());
            return metadataEventService.reindexSync(reindexModel.getPaths(), deleteFirst);
        }
    }

    @Path("stats/recreate")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @POST
    public Response recreateStatsInMetadataByPaths(MetadataServerReindexModel reindexModel,
            @DefaultValue("true") @QueryParam("async") Boolean async) {
        if (!ConstantValues.metadataServerEventsEnabled.getBoolean()) {
            return Response.status(Response.Status.NOT_FOUND).entity("Metadata Server Integration is disabled").build();
        }
        if (reindexModel == null || CollectionUtils.isNullOrEmpty(reindexModel.getPaths())) {
            String message = "Reindex paths are missing in the request. No reindexing will be done.";
            log.error(message);
            return Response.status(Response.Status.BAD_REQUEST).entity(message).build();
        }
        MetadataEventService metadataEventService = ContextHelper.get().beanForType(MetadataEventService.class);
        if (Boolean.TRUE.equals(async)) {
            log.info(
                    "Received asynchronous Metadata Server re-indexing request on paths {}. Re-indexing will run in " +
                            "the background",
                    reindexModel.getPaths());
            metadataEventService.statsRecreateAsync(reindexModel.getPaths());
            return Response.accepted().build();
        } else {
            log.info("Received synchronous Metadata Server re-indexing request on paths {}.",
                    reindexModel.getPaths());
            return metadataEventService.statsRecreateSync(reindexModel.getPaths());
        }
    }
}