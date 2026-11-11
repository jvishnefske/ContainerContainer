package org.artifactory.rest.resource.federation;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.federation.FederatedBinaryTasksService;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.FederationServiceProxy;
import org.artifactory.exception.http.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.jfrog.common.ExceptionLogUtils.debugOrError;

/**
 * @author annakv
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("federation/binaries")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class FederatedBinaryTasksResource {

    @Context
    HttpServletRequest httpRequest;

    private static final String REPO_KEY_ERR_MESSAGE = "Parameter repoKey is not provided";

    private final FederationServiceProxy<FederatedBinaryTasksService> federatedBinariesTasksServiceProxy;
    private final FederatedRepoService federatedRepoService;

    @Autowired
    public FederatedBinaryTasksResource(
            @Qualifier("federatedBinariesTasksServiceProxy")
            FederationServiceProxy<FederatedBinaryTasksService> serviceProxy,
            FederatedRepoService federatedRepoService) {
        this.federatedBinariesTasksServiceProxy = serviceProxy;
        this.federatedRepoService = federatedRepoService;
    }

    private FederatedBinaryTasksService getService() {
        return federatedBinariesTasksServiceProxy.getService();
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("failures/list")
    public Response list(
            @QueryParam("repoKey") String repoKey,
            @QueryParam("limit") Integer limit,
            @QueryParam("page") Integer page) {
        if (StringUtils.isBlank(repoKey)) {
            return Response.status(SC_BAD_REQUEST).entity(REPO_KEY_ERR_MESSAGE).build();
        }
        try {
            return Response.status(SC_OK).entity(
                    getService().getFailingTasks(repoKey, limit, page)).build();
        } catch (Exception e) {
            debugOrError(log,
                    String.format("Retrieval of failed tasks for federated repo %s has failed", repoKey), e);
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format("Retrieval of failed tasks for federated repo %s has failed", repoKey))
                    .build();
        }
    }

    @PUT
    @Produces({MediaType.APPLICATION_JSON})
    @Path("failures/replay")
    public Response replayTasks(@QueryParam("repoKey") String repoKey,
            @QueryParam("sha1") String sha1) {
        if (StringUtils.isBlank(repoKey)) {
            return Response.status(SC_BAD_REQUEST).entity(REPO_KEY_ERR_MESSAGE).build();
        }
        try {
            int count = getService().replayTasks(repoKey, sha1);
            return Response.status(OK).entity(new FetchFailuresReplayResult(count)).build();
        } catch (BadRequestException e) {
            log.warn("Couldn't replay failed tasks for federated repo {}: {}", repoKey, e.getMessage());
            return Response.status(SC_BAD_REQUEST).entity("Unable to replay tasks: " + e.getMessage()).build();
        } catch (Exception e) {
            debugOrError(log,
                    String.format("Replaying of failed tasks for federated repo %s has failed", repoKey), e);
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format("Replaying of failed tasks for federated repo %s has failed", repoKey))
                    .build();
        }
    }

    @DELETE
    @Produces({MediaType.APPLICATION_JSON})
    @Path("failures/delete")
    public Response deleteTasks(@QueryParam("repoKey") String repoKey, @QueryParam("sha1") String sha1) {
        if (StringUtils.isBlank(repoKey)) {
            return Response.status(SC_BAD_REQUEST).entity(REPO_KEY_ERR_MESSAGE).build();
        }
        try {
            int count = getService().deleteRepoFailedTasksByBinary(repoKey, sha1);
            return Response.status(OK).entity(new FetchFailuresDeleteResult(count)).build();
        } catch (Exception e) {
            debugOrError(log,
                    String.format("Deletion of failed tasks for federated repo %s has failed", repoKey), e);
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format("Deletion of failed tasks for federated repo %s has failed", repoKey))
                    .build();
        }
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON})
    @Path("tasks/{repoKey}")
    public Response tasksInfo(@PathParam("repoKey") String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return Response.status(SC_BAD_REQUEST).entity(REPO_KEY_ERR_MESSAGE).build();
        }
        try {
            return Response.status(SC_OK).entity(getService().getBinariesTasksInfo(repoKey)).build();
        } catch (BadRequestException e) {
            log.warn("Couldn't retrieve tasks info for federated repo {}: {}", repoKey, e.getMessage());
            return Response.status(SC_BAD_REQUEST).entity("Unable to get tasks count: " + e.getMessage()).build();
        }  catch (Exception e) {
            debugOrError(log,
                    String.format("Retrieval of tasks info for federated repo %s has failed", repoKey), e);
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format("Retrieval of tasks info for federated repo %s has failed", repoKey))
                    .build();
        }
    }

    @PUT
    @Produces({APPLICATION_JSON})
    @Path("rtfs")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response switchRtfsBinaryTasks(@QueryParam("enable") boolean enable) {
        log.debug("Received a request to {} binary tasks using RTFS...", enable ? "enable" : "disable");
        try {
            if(enable && !federatedRepoService.isMigrationOver()) {
                log.error("An attempt to enable Binary Tasks v2 failed since migration to RTFS is not over yet");
                var error = new ErrorResult("Failed enabling Binary Tasks v2", "Migration to RTFS is not over yet");
                return Response.status(SC_BAD_REQUEST).entity(error).build();
            }
            federatedRepoService.updateBinaryTasksFeatureStatus(enable);
            log.info("Binary tasks is {}set to use RTFS", enable ? "" : "no longer ");
            boolean active = federatedRepoService.isFederatedBinaryProviderV2Active();
            String inactiveMsg = "inactive".transform(s -> enable ? s + ", restart to activate!" : s);
            String activeMsg = "active".transform(s -> !enable ? s + ", restart to deactivate!" : s);
            log.info("Federated binary tasks provider V2 is {}", active ? activeMsg : inactiveMsg);
            return Response.ok().entity(new FeatureStateResponse(enable, active)).build();
        } catch (Exception e) {
            String errorMsg = "Failed processing request to %s binary tasks using RTFS"
                    .formatted(enable ? "enable" : "disable");
            debugOrError(log, errorMsg, e);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("rtfs")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response isRtfsBinaryTasksEnabled() {
        log.debug("Received request to get binary tasks feature status from: {}", httpRequest.getRequestURI());
        try {
            boolean enabled = ConstantValues.federatedForceLoadFederatedProviderV2ForTestDefault.getBoolean() ||
                    federatedRepoService.isBinaryTasksEnabled();
            boolean active = federatedRepoService.isFederatedBinaryProviderV2Active();
            return Response.ok().entity(new FeatureStateResponse(enabled, active)).build();
        } catch (Exception e) {
            String errorMsg = "Failed to get binary tasks feature status";
            debugOrError(log, errorMsg, e);
            return Response.serverError().build();
        }
    }

    @VisibleForTesting
    public record FeatureStateResponse(boolean enabled, boolean active) {
    }

    @VisibleForTesting
    public record FetchFailuresDeleteResult(int deletedTasks) {
    }

    @VisibleForTesting
    public record FetchFailuresReplayResult(int replayTasks) {
    }

    @VisibleForTesting
    public record ErrorResult(String message, String reason) {
    }

}
