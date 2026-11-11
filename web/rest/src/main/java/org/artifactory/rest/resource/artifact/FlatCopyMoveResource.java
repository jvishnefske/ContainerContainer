package org.artifactory.rest.resource.artifact;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_COPY;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_FLAT;
import static org.artifactory.api.rest.constant.ArtifactRestConstants.PATH_MOVE;

/**
 * REST API used to copy an artifact from one path to another.
 *
 * @author Tomer Cohen
 */
@Component
@Path(PATH_FLAT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.ARTIFACT_API)
public class FlatCopyMoveResource {

    /**
     * Move an item (file or folder) from one path to another, using Flat Move method.
     *
     * @param path     The source path.
     * @param target   The target path.
     * @param dryRun   Flag whether to perform a dry run before executing the actual move.
     * @param failFast Flag to indicate whether the operation should fail upon encountering an error.
     * @return The operation result
     * @throws Exception in case of error during move operation
     */
    @POST
    @Path(PATH_MOVE + "/{path: .+}")
    @Produces({ArtifactRestConstants.MT_COPY_MOVE_RESULT, MediaType.APPLICATION_JSON})
    public Response move(
            // The path of the source item to be moved/copied
            @PathParam("path") String path,
            // The target path to to move/copy the item.
            @QueryParam(ArtifactRestConstants.PARAM_TARGET) String target,
            // Flag to indicate whether to perform a dry run first. default false
            @QueryParam(ArtifactRestConstants.PARAM_DRY_RUN) int dryRun,
            //Flag to indicate whether the operation should fail upon encountering an error. default false
            @QueryParam(ArtifactRestConstants.PARAM_FAIL_FAST) int failFast) throws Exception {
        if (ConstantValues.flatCopyMoveRestApiDisabled.getBoolean()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.addonByType(RestAddon.class).moveFlat(path, target, dryRun, failFast);
    }

    /**
     * Copy an item (file or folder) from one path to another, using Flat Copy method.
     *
     * @param path     The source path.
     * @param target   The target path.
     * @param dryRun   Flag whether to perform a dry run before executing the actual copy.
     * @param failFast Flag to indicate whether the operation should fail upon encountering an error.
     * @return The operation result
     * @throws Exception in case of error during copy operation
     */
    @POST
    @Path(PATH_COPY + "/{path: .+}")
    @Produces({ArtifactRestConstants.MT_COPY_MOVE_RESULT, MediaType.APPLICATION_JSON})
    public Response copy(
            // The path of the source item to be moved/copied
            @PathParam("path") String path,
            // The target repository to to move/copy the item.
            @QueryParam(ArtifactRestConstants.PARAM_TARGET) String target,
            // Flag to indicate whether to perform a dry run first. default false
            @QueryParam(ArtifactRestConstants.PARAM_DRY_RUN) int dryRun,
            //Flag to indicate whether the operation should fail upon encountering an error. default false
            @QueryParam(ArtifactRestConstants.PARAM_FAIL_FAST) int failFast) throws Exception {
        if (ConstantValues.flatCopyMoveRestApiDisabled.getBoolean()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.addonByType(RestAddon.class).copyFlat(path, target, dryRun, failFast);
    }
}
