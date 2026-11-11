package org.artifactory.rest.resource.config;

import lombok.AllArgsConstructor;
import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.aol.AolService;
import org.artifactory.api.rest.constant.ConfigsRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationModel;
import org.artifactory.storage.checksum.replication.StorageChecksumReplicationService;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.ConfigsRestConstants.STORAGE_CHECKSUM_REPLICATION;

/**
 * @author Avishay Halpren
 */
@Component
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@AllArgsConstructor
@Path(ConfigsRestConstants.PATH_ROOT)
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class ConfigsServiceResource {

    private final AolService aolService;

    private final StorageChecksumReplicationService storageChecksumReplicationService;

    @GET
    @Path(STORAGE_CHECKSUM_REPLICATION)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public StorageChecksumReplicationModel getStorageChecksumReplicationConfig() {
        return storageChecksumReplicationService.getModel();
    }

    @PUT
    @Path(STORAGE_CHECKSUM_REPLICATION)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateStorageChecksumReplicationConfig(StorageChecksumReplicationModel model) {
        if (aolService.isAol() && !aolService.isDashboardUser()) {
            String message = "This API endpoint is not supported for this user, " +
                    "please contact support@jfrog.com for further assistance if required.";
            return Response.status(HttpStatus.SC_FORBIDDEN).entity(message).build();
        }
        storageChecksumReplicationService.updateModel(model);
        return Response.ok().entity("storage checksumReplication updated successfully").build();
    }

}
