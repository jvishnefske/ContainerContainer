package org.artifactory.rest.resource.federation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.event.migration.EventTableRepoKeyMigrationImpl;
import org.artifactory.federation.FederatedArtifactsStatusInfo;
import org.artifactory.federation.FederatedMirrorEventsStatusInfo;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.FederatedRepoStatusInfo;
import org.artifactory.federation.FederatedStatusService;
import org.artifactory.federation.JpdFederationStatus;
import org.artifactory.federation.MirrorLagInfo;
import org.artifactory.federation.UnavailableMirrorInfo;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ConnectionStatesRtfsResponse;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.StateListRtfsResponse;
import org.artifactory.repo.StateSummaryRtfsResponse;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.jfrog.common.StringList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_JPD_STATUS;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("federation/status")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Slf4j
public class FederatedStatusResource {
    private static final String THRESHOLD_TIME_IN_MS = "thresholdTimeInMs";
    protected FederatedStatusService federatedStatusService;
    protected InternalRepositoryService internalRepositoryService;
    protected EventTableRepoKeyMigrationImpl eventTableRepoKeyMigrationImpl;
    protected FederatedRepoService federatedRepoService;
    protected SecurityService securityService;

    static final String MIRRORS = "mirrors";
    static final String BINARIES = "binaries";

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setService(FederatedStatusService federatedStatusService) {
        this.federatedStatusService = federatedStatusService;
    }

    @Autowired
    public void setInternalRepositoryService(InternalRepositoryService internalRepositoryService) {
        this.internalRepositoryService = internalRepositoryService;
    }

    @Autowired
    public void setEventTableRepoKeyMigrationImpl(EventTableRepoKeyMigrationImpl eventTableRepoKeyMigrationImpl) {
        this.eventTableRepoKeyMigrationImpl = eventTableRepoKeyMigrationImpl;
    }

    @Autowired
    public void setFederatedRepoService(FederatedRepoService federatedRepoService) {
        this.federatedRepoService = federatedRepoService;
    }

    @GET
    @Path("mirrorsLag")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMirrorsLag(@QueryParam(THRESHOLD_TIME_IN_MS) Long thresholdTimeInMs) {
        if(federatedRepoService.isMigrationOver()) {
            return getBlockedResponseWhenRtfsIsEnabled("get mirrors lag");
        }
        if (!eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()) {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Monitoring is unavailable, migration in progress").build();
        }
        List<MirrorLagInfo> mirrorLagInfos =
                federatedStatusService.getMirrorsLagWithThreshold(thresholdTimeInMs);
        log.debug("Returning lag of #{}", mirrorLagInfos.size());
        return Response.status(SC_OK).entity(mirrorLagInfos).build();
    }

    @GET
    @Path("unavailableMirrors")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUnavailableMirrors() {
        // Completed
        if(federatedRepoService.isMigrationOver()) {
            return getBlockedResponseWhenRtfsIsEnabled("get unavailable mirrors");
        }
        if (!eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()) {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Monitoring is unavailable, migration in progress").build();
        }
        List<UnavailableMirrorInfo> notSyncMirrors =
                federatedStatusService.getUnavailableMirrorsByTypeAndDifferentProvidedStatus();
        return Response.status(SC_OK).entity(notSyncMirrors).build();
    }

    @GET
    @Path("repo/{repoKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFederatedRepoStatus(@PathParam("repoKey") String repoKey) {
        return getFederatedRepoStatus(repoKey, false);
    }
    
    @GET
    @Path("repo/{repoKey}/{federatedKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFederatedRepoStatusByFedKey(
            @PathParam("repoKey") String repoKey, @PathParam("federatedKey") String federatedKey) {
        return getFederatedRepoStatusByFedKey(repoKey, federatedKey, false);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_JPD_STATUS)
    public Response getJpdFederationStatus() {
        if(federatedRepoService.isMigrationOver()) {
            return getBlockedResponseWhenRtfsIsEnabled("get jpd federation status");
        }
        if (!eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()) {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Monitoring is unavailable, migration in progress").build();
        }
        JpdFederationStatus jpdFederationStatus = federatedStatusService.getCurrentJpdFederationStatus();
        return Response.status(SC_OK).entity(jpdFederationStatus).build();
    }

    @GET
    @Path("stateList")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStateList(@QueryParam("status") StringList statuses){
        try {
            if(!federatedRepoService.isMigrationOver()) {
                log.error("Could not get state list as RTFS is not enabled");
                return Response.status(SC_METHOD_NOT_ALLOWED).entity("Could not get state list as RTFS is not enabled")
                        .build();
            }

            if (isBaseUrlConfigured()) {
                return Response.status(SC_OK).entity(federatedStatusService.getFederationStateList(statuses)).build();
            }

            log.debug("Skipping stateList: base URL is not configured");
            return Response.status(SC_BAD_REQUEST)
                    .entity(StateListRtfsResponse.builder().federations(List.of()).build()).build();
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed to get federation state list due to: %s", e.getMessage());
            log.error(errorMessage, e.getMessage());
            log.debug("Failed to get federation state list due to:", e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
    }


    @GET
    @Path("stateSummary")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStateSummary(){
        try {
            if(!federatedRepoService.isMigrationOver()) {
                log.error("Could not get state summary as RTFS is not enabled");
                return Response.status(SC_METHOD_NOT_ALLOWED)
                        .entity("Could not get state summary as RTFS is not enabled")
                        .build();
            }

            if (isBaseUrlConfigured()) {
                return Response.status(SC_OK).entity(federatedStatusService.getFederationSummary()).build();

            }

            log.debug("Skipping stateSummary: base URL is not configured");
            return Response.status(SC_BAD_REQUEST)
                    .entity(StateSummaryRtfsResponse.builder().federationStates(
                                    new StateSummaryRtfsResponse.FederationStates(
                                            0, 0, 0, 0, 0, 0, 0, 0))
                            .build()).build();
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed to get federation state summary due to: %s", e.getMessage());
            log.error(errorMessage, e.getMessage());
            log.debug("Failed to get federation state summary due to:", e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
    }


    @GET
    @Path("connectionStates/{repoKey}")
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConnectionStates(@PathParam("repoKey") String repoKey) {
        try {
            if (!securityService.isProjectAdmin(RepoPathFactory.create(repoKey))) {
                throw new AuthorizationRestException();
            }

            if(!federatedRepoService.isMigrationOver()) {
                log.error("Could not get connection states for {} as RTFS is not enabled", repoKey);
                return Response.status(SC_METHOD_NOT_ALLOWED)
                        .entity("Could not get connection states for" + repoKey + " as RTFS is not enabled")
                        .build();
            }

            if (!isFederatedRepo(repoKey)) {
                log.debug("Skipping connectionStates for {}: repo is not federated", repoKey);
                return Response.status(SC_OK).entity(new ConnectionStatesRtfsResponse(List.of())).build();
            }

            if (isBaseUrlConfigured()) {
                return Response.status(SC_OK).entity(federatedStatusService.getConnectionStates(repoKey)).build();
            }

            log.debug("Skipping connectionStates for {}: base URL is not configured", repoKey);
            return Response.status(SC_BAD_REQUEST)
                    .entity(new ConnectionStatesRtfsResponse(List.of())).build();
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed to get connection states for %s due to: %s", repoKey, e.getMessage());
            log.error(errorMessage, e.getMessage());
            log.debug("Failed to get connection states for {} due to:", repoKey, e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
    }

    @GET
    @Path("repoState/{repoKey}/binaries/")
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRepoBinariesState(@PathParam("repoKey") String repoKey,
            @QueryParam("url") String remoteUrl) {
        try {
            if (!securityService.isProjectAdmin(RepoPathFactory.create(repoKey))) {
                throw new AuthorizationRestException();
            }

            if (!federatedRepoService.isMigrationOver()) {
                log.error("Could not get repo binaries state for {} as RTFS is not enabled", repoKey);
                return Response.status(SC_METHOD_NOT_ALLOWED)
                        .entity("Could not get repo binaries state for " + repoKey + " as RTFS is not enabled")
                        .build();
            }
            return Response.status(SC_OK).entity(federatedStatusService.getRepoBinariesState(repoKey, remoteUrl))
                    .build();
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Could not get repo binaries state for %s due to: %s", repoKey, e.getMessage());
            log.error(errorMessage, e.getMessage());
            log.debug("Could not get repo binaries state for {} due to:", repoKey, e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
    }

    private static Response getBlockedResponseWhenRtfsIsEnabled(String apiName) {
        log.warn("RTFS is enabled therefore " + apiName + " is not allowed");
        return Response.status(SC_METHOD_NOT_ALLOWED)
                .entity("RTFS is enabled therefore " + apiName + " is not allowed")
                .build();
    }

    private boolean isBaseUrlConfigured() {
        return StringUtils.isNotBlank(ContextHelper.get().getCentralConfig().getDescriptor()
                .getFederatedRepoUrlBaseOrDefault());
    }

    private boolean isFederatedRepo(String repoKey) {
        RepoConfig repoConfig = internalRepositoryService.getRepoConfigByKey(repoKey);
        return repoConfig != null && repoConfig.isFederated();
    }
    
    private boolean repoExistsForFederationStatus(String repoKey) {
        return isFederatedRepo(repoKey)
                || internalRepositoryService.localFederatedOrCachedRepoDescriptorByKey(repoKey) != null;
    }

    protected Response getFederatedRepoStatus(String repoKey, boolean v2) {

        if (federatedRepoService.isMigrationOver()) {
            return getBlockedResponseWhenRtfsIsEnabled("get repo status");
        }
        long currentTime = System.currentTimeMillis();
        if (!repoExistsForFederationStatus(repoKey)) {
            throw new NotFoundException(String.format("Repo key: %s not found", repoKey));
        }
        if (!eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()) {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Monitoring is unavailable, migration in progress").build();
        }
        FederatedRepoStatusInfo federatedRepoStatusInfo =
                federatedStatusService.getFederatedRepoStatus(repoKey, currentTime, v2);
        return Response.status(SC_OK).entity(federatedRepoStatusInfo).build();
    }

    protected Response getFederatedRepoStatusByFedKey(String repoKey, String federatedKey, boolean isV2) {
        if (federatedRepoService.isMigrationOver()) {
            return getBlockedResponseWhenRtfsIsEnabled("get federated repo status by federated key");
        }
        long currentTime = System.currentTimeMillis();
        if (!repoExistsForFederationStatus(repoKey)) {
            throw new NotFoundException(String.format("Repo key: %s not found", repoKey));
        }
        if (!eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()) {
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .entity("Monitoring is unavailable, migration in progress").build();
        }
        if (federatedKey.equals(MIRRORS)) {
            FederatedMirrorEventsStatusInfo federatedMirrorEventsStatusInfo =
                    federatedStatusService.getFederatedMirrorEventsStatus(repoKey, currentTime, isV2);
            return Response.status(SC_OK).entity(federatedMirrorEventsStatusInfo).build();
        } else if (federatedKey.equals(BINARIES)) {
            FederatedArtifactsStatusInfo federatedArtifactsRepoStatus =
                    federatedStatusService.getFederatedArtifactsRepoStatus(repoKey);
            return Response.status(SC_OK).entity(federatedArtifactsRepoStatus).build();
        }
        return Response.status(HttpStatus.SC_BAD_REQUEST)
                .entity("Could not get repo status invalid url").build();
    }
}

