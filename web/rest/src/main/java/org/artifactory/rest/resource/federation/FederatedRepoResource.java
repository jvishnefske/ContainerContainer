package org.artifactory.rest.resource.federation;

import com.github.zafarkhaja.semver.Version;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.request.DownloadService;
import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.JerseyArtifactoryResponse;
import org.artifactory.api.rest.constant.FakeSystemPackageType;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.federation.FederatedRequestCustomContext;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.exceptions.HaFederationPropagationException;
import org.artifactory.federation.DryFullSyncException;
import org.artifactory.federation.FederatedFullSyncRepoService;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.FederationRoutingService;
import org.artifactory.federation.FullSyncException;
import org.artifactory.federation.GridTopologyChangeType;
import org.artifactory.federation.exceptions.BinariesTaskException;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.model.QueuePosition;
import org.artifactory.repo.ArtifactoryFederationServiceException;
import org.artifactory.repo.FederationMigrationStatus;
import org.artifactory.repo.FederationPriorityUpdateRtfsResponse;
import org.artifactory.repo.FullSyncRtfsResponse;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.RepoPriority;
import org.artifactory.repo.UrlReposDetails;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ConflictException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.util.EdgeUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.rtfs.dto.NetworkConstant;
import org.jfrog.rtfs.dto.member.FederatedConfigInfo;
import org.jfrog.rtfs.dto.member.MemberQueueState;
import org.jfrog.rtfs.dto.member.MirrorMemberInfo;
import org.jfrog.rtfs.dto.migration.MigrationVerboseStatus;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.jfrog.rtfs.dto.queue.QueueState;
import org.jfrog.rtfs.dto.sync.SaveFileResponse;
import org.jfrog.rtfs.dto.tasks.TaskRequest;
import org.jfrog.rtfs.module.member.MemberRemovalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.CREATED;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FAILED_DEPENDENCY;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.api.request.ArtifactoryHeaders.ORIGINAL_CLIENT_URI;
import static org.artifactory.api.request.InternalRequestFactory.createInternalRequestDisableRedirect;
import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_DRY_FULL_SYNC_REPO;
import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_FULL_SYNC_REPO;
import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_FULL_SYNC_SYSTEM_REPO;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_QUEUE_STATUS;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_CONFIG_SYNC;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_FED_REPOCONFIG_INFO;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_FED_REPOCONFIG_INFO_READ;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_FULL_SYNC;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_GRID_TOPOLOGY_CHANGE;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_METADATA;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_MIGRATION_STATUS;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_REPO_EXISTS;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_REPO_EXISTS_READ;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_REPOSITORY_LIST;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_RTFS;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_RTFS_READ;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_TENANT_MOVE_STATUS;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SCOPE_VALIDATE_MIRROR;
import static org.artifactory.rest.resource.federation.PermissionScopes.INTERNAL_SEND_BINARY_TASK_REQUEST;
import static org.artifactory.util.HttpUtils.deduplicateDoubleSlashesFromUri;
import static org.artifactory.util.HttpUtils.getArtifactoryBaseUrl;
import static org.artifactory.util.bundles.BundleConfigurationUtil.isReleaseBundlesV2RepoKey;

/**
 * @author Alexei Vainshtein
 */

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("federation")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API, customPackageType = FakeSystemPackageType.FEDERATION)
public class FederatedRepoResource {
    private static final Logger log = LoggerFactory.getLogger(FederatedRepoResource.class);
    private static final String DRY_FULL_SYNC_RESULT_API_PATH = "api/federation/compareResult";


    @VisibleForTesting
    public static final String MIGRATION_TOOL_USER_AGENT = "rtfs_migration_tool";
    @VisibleForTesting
    public static final Version MIN_SUPPORTED_MIGRATION_TOOL_VERSION = Version.of(2, 3);

    @Context
    HttpServletRequest httpRequest;

    private FederatedRepoService federatedRepoService;

    private FederationRoutingService federationRoutingService;

    private FileService fileService;

    private FederatedFullSyncRepoService federatedFullSyncRepoService;

    private SecurityService securityService;

    private DownloadService downloadService;

    private FederatedRequestCustomContext federatedRequestCustomContext;

    @Autowired
    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    @Autowired
    public void setFederatedRequestCustomContext(FederatedRequestCustomContext federatedRequestCustomContext) {
        this.federatedRequestCustomContext = federatedRequestCustomContext;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setService(FederatedRepoService federatedRepoService) {
        this.federatedRepoService = federatedRepoService;
    }

    @Autowired
    public void setFederationRoutingService(FederationRoutingService federationRoutingService) {
        this.federationRoutingService = federationRoutingService;
    }

    @Autowired
    public void setDownloadService(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @Autowired
    public void setFederatedFullSyncRepoService(FederatedFullSyncRepoService federatedFullSyncRepoService) {
        this.federatedFullSyncRepoService = federatedFullSyncRepoService;
    }

    /**
     * If the target member of Federation supports RB v2 repositories Federation,
     * the initiator of Federation must receive 201 instead of 200 status code,
     * which would be returned before the support of this feature.
     */
    @GET
    @Path("validate/{repoKey}")
    @Produces({APPLICATION_JSON})
    @AllowedPermissionScope(INTERNAL_SCOPE_VALIDATE_MIRROR)
    public Response validate(
            @PathParam("repoKey") String targetRepoKey,
            @QueryParam("time") long time,
            @QueryParam("source_repo_type") String repoType,
            @QueryParam("source_repo_key") String sourceRepoKey,
            @QueryParam("repos_import_in_progress") boolean reposImportInProgress,
            @QueryParam("project_key") String projectKey) {
        try {
            String baseUrl = httpRequest.getHeader(ORIGINAL_CLIENT_URI);
            boolean rtfsLocal = Boolean.parseBoolean(httpRequest.getHeader(NetworkConstant.LOCAL_HEADER_NAME));
            federatedRepoService.validate(targetRepoKey, baseUrl, time, repoType, sourceRepoKey, reposImportInProgress,
                    rtfsLocal, projectKey);
            int status = isReleaseBundlesV2RepoKey(targetRepoKey) ? SC_CREATED : SC_OK;
            return Response.status(status).build();
        } catch (BadRequestException e) {
            String message = "Validation of federated repository failed: " + e.getMessage();
            log.warn(message, e);
            return Response.status(SC_BAD_REQUEST).entity(message).build();
        } catch (ConflictException e) {
            String message = "Validation of federated repository failed: " + e.getMessage();
            log.warn(message, e);
            return Response.status(SC_CONFLICT).entity(message).build();
        }
    }

    @GET
    @Produces({APPLICATION_JSON})
    @Path("repos")
    @AllowedPermissionScope(INTERNAL_SCOPE_REPOSITORY_LIST)
    public Response repos() {
        // Will return all the federation repos:
        try {
            List<RepoDetails> repos = federatedRepoService.getRepos();
            return Response.status(SC_OK).entity(repos).build();
        } catch (BadRequestException e) {
            return Response.status(SC_BAD_REQUEST)
                    .entity("Retrieval of federated repositories failed: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Produces({APPLICATION_JSON})
    @Path("baseUrlAndRepos")
    @AllowedPermissionScope(INTERNAL_SCOPE_REPOSITORY_LIST)
    public Response baseUrlAndRepos() {
        // Will return all the federation repos:
        try {
            UrlReposDetails repos = federatedRepoService.getBaseUrlAndRepos();
            return Response.status(SC_OK).entity(repos).build();
        } catch (BadRequestException e) {
            return Response.status(SC_BAD_REQUEST)
                    .entity("Retrieval of federated repositories failed: " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Produces({APPLICATION_JSON})
    @Path("state")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response state() {
        if (federatedRepoService.shouldProcessEventsToRTFS()) {
            log.warn("RTFS is enabled therefore federation state is not allowed");
            return Response.status(SC_METHOD_NOT_ALLOWED)
                    .entity("RTFS is enabled therefore federation state is not allowed")
                    .build();
        }
        try {
            return Response.status(SC_OK).entity(federatedRepoService.getMirrorStates()).build();
        } catch (HaFederationPropagationException e) {
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity("Retrieval of federated repository states failed: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Consumes({APPLICATION_JSON})
    @Produces({APPLICATION_JSON})
    @Path("properties/{repoKey}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response properties(@RequestBody List<NodePath> repoPaths, @PathParam("repoKey") String repoKey) {
        try {
            return Response.status(SC_OK).entity(federatedRepoService.getProperties(repoPaths, repoKey)).build();
        } catch (StorageException e) {
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format("Retrieval of properties for federated repo %s has failed: %s",
                            repoKey, e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("property/{repoKey}/{fileName}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO)
    public Response fullSyncProperty(@PathParam("repoKey") String repoKey, @PathParam("fileName") String fileName) {
        try {
            return Response.status(SC_OK)
                    .entity(federatedFullSyncRepoService.getFullSyncFileProperty(repoKey, fileName)).build();
        } catch (FullSyncException | StorageException e) {
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity(String.format("Retrieval of properties for federated repo %s of artifact %s has failed: %s",
                            repoKey, fileName, e.getMessage())).build();
        }
    }

    @GET
    @Produces(APPLICATION_JSON)
    @Path("extendedInfo/{repoKey}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response extendedInfo(@PathParam("repoKey") String repoKey) {
        return Response.status(SC_OK).entity(federatedRepoService.getExtendedInfo(repoKey)).build();
    }

    @POST
    @Path("migrate/{repoKey}")
    @Produces(APPLICATION_JSON)
    public Response migrate(@PathParam("repoKey") String repoKey) {
        try {
            federatedRepoService.migrate(repoKey);
        } catch (BadRequestException e) {
            String errorMessage = String.format("Failed to migrate repository %s due to: %s", repoKey, e.getMessage());
            log.error(errorMessage, repoKey, e.getMessage());
            log.debug("Failed to migrate repository due to:", e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
        return Response.status(SC_OK).entity("Migration finished successfully").build();
    }

    @POST
    @Path("convertToLocal")
    @Produces(APPLICATION_JSON)
    public Response convertToLocal(@RequestBody List<String> repositories) {
        HashMap<String, String> failedToConvertReposToErrors = federatedRepoService
                .convertFederatedToLocal(repositories);

        if (!failedToConvertReposToErrors.isEmpty()) {
            int statusCode = failedToConvertReposToErrors.size() == repositories.size() ? SC_BAD_REQUEST : SC_OK;
            return Response.status(statusCode).entity(Map.of("failedConversionRepositories",
                    failedToConvertReposToErrors)).build();
        }

        return Response.status(SC_OK).entity("Successfully converted from federated to local repositories: %s"
                .formatted(repositories)).build();
    }

    @POST
    @Path("fullSync/{repoKey}")
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC)
    public Response fullSync(@PathParam("repoKey") String repoKey, @QueryParam("mirror") String mirrorUrl,
            @QueryParam("isDry") boolean isDry) {
        List<String> excludedList = ConstantValues.federatedFullSyncExcludeRepos.getList();
        if (excludedList.contains(repoKey)) {
            return Response.status(SC_METHOD_NOT_ALLOWED).entity("Full sync trigger attempt on excluded repo")
                    .build();
        }
        String clientUri = getArtifactoryBaseUrl(httpRequest);
        return triggerFullSync(repoKey, mirrorUrl, false, isDry, clientUri);
    }

    @POST
    @Path("forceFullSync/{repoKey}")
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC)
    public Response forceFullSync(@PathParam("repoKey") String repoKey, @QueryParam("mirror") String mirrorUrl) {
        return triggerFullSync(repoKey, mirrorUrl, true, false, "");
    }

    private Response triggerFullSync(String repoKey, String mirrorUrl, boolean forceRun,
            boolean isDry, String clientUri) {
        FullSyncRtfsResponse response;
        try {
            response = federationRoutingService.triggerFullSync(repoKey, mirrorUrl, forceRun, isDry);
            if (response.resultPaths() != null && !response.resultPaths().isEmpty()) {
                List<String> alteredPaths = new ArrayList<>();
                response.resultPaths().forEach(path -> {
                    String userVisiblePath = String.format("%s/%s/%s", clientUri, DRY_FULL_SYNC_RESULT_API_PATH, path);
                    alteredPaths.add(deduplicateDoubleSlashesFromUri(userVisiblePath));
                });
                response = new FullSyncRtfsResponse(response.message(), alteredPaths);
            }
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed trigger full sync for repository %s due to: %s", repoKey, e.getMessage());
            log.error(errorMessage, repoKey, e.getMessage());
            log.debug("Failed trigger full sync for repository due to:", e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
        return Response.status(SC_OK).entity(JsonUtils.getInstance().valueToString(response, true)).build();
    }

    @POST
    @Path("configSync/{repoKey}")
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_CONFIG_SYNC)
    public Response configSync(@PathParam("repoKey") String repoKey) {
        try {
            federationRoutingService.configSync(repoKey);
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed to execute configuration sync for repository %s due to: %s", repoKey,
                            e.getMessage());
            log.error(errorMessage, repoKey, e.getMessage());
            log.debug("Failed to execute configuration sync for repository due to:", e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        } catch (FederatedRemoteMembersUpdateException e) {
            log.error("Failed to execute configuration sync for repository due to: {}", e.getMessage());
            log.debug("Failed to execute configuration sync for repository due to: ", e);
            return Response.status(SC_BAD_REQUEST).entity(e.getMessage()).build();
        }
        return Response.status(SC_OK).entity("Configuration sync executed successfully").build();
    }

    @GET
    @Path("ready/{repoKey}")
    @Produces(APPLICATION_JSON)
    public Response isReady(@PathParam("repoKey") String repoKey) {
        try {
            return Response.status(SC_OK).entity(federatedRepoService.isActive(repoKey)).build();
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed to execute readiness request for repository %s due to: %s", repoKey,
                            e.getMessage());
            log.error(errorMessage);
            log.debug(errorMessage, e);
            return Response.status(SC_OK).entity(false).build();
        }
    }

    @GET
    @Path("repoExists/{repoKey}")
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(scopes = {INTERNAL_SCOPE_REPO_EXISTS, INTERNAL_SCOPE_REPO_EXISTS_READ})
    public Response repoExists(@PathParam("repoKey") String repoKey) {
        return Response.status(SC_OK).entity(federatedRepoService.repoExists(repoKey)).build();
    }

    @POST
    @Path("metadata")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_METADATA)
    public Response metadata(@RequestBody List<String> types) {
        try {
            String metadata = federatedRepoService.getMetadata(types);
            return Response.status(SC_OK).entity(metadata).build();
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed to execute metadata request due to: %s", e.getMessage());
            log.error(errorMessage);
            log.debug(errorMessage, e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
    }

    /**
     * This method is deprecated and kept only for backward compatibility with older federation members.
     */
    @PUT
    @Path("metadata")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @AllowedPermissionScope(INTERNAL_SCOPE_METADATA)
    //NOSONAR
    @Deprecated(since = "7.131.x", forRemoval = true)
    public Response updateMetadata(@QueryParam("originUrl") String mirrorUrl,
            @QueryParam("updateMembers") boolean updateMembers, @RequestBody String metadata) {
        log.debug("Metadata update is deprecated - received a request from old version mirror");
        return Response.status(CREATED).entity("Metadata received by the member").build();
    }

    @POST
    @Path("metadata/invalidate")
    @AllowedPermissionScope(INTERNAL_SCOPE_METADATA)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response invalidateMetadata(@QueryParam("type") String packageType, @QueryParam("url") String memberUrl,
            @QueryParam("proxy") String proxyName) {
        log.debug("Received invalidate request for package type {} and member: {} ", packageType, memberUrl);
        federatedRepoService.invalidateMetadata(packageType, memberUrl, proxyName);
        return Response.status(OK).build();
    }

    @PUT
    @Path("negotiation/disable")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response disableNegotiation(@QueryParam("repoTypes") String repoTypes) {
        String userName = ContextHelper.get().beanForType(SecurityService.class).currentUsername();
        log.debug("Received request from user {} to disable repo of types: {}", userName, repoTypes);
        return Response.status(OK).entity("Negotiation is permanently disabled for all types").build();
    }

    @POST
    @Path("repos/enable")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response enableRepos(@RequestBody List<String> repoTypes) {
        String userName = ContextHelper.get().beanForType(SecurityService.class).currentUsername();
        log.debug("Received request from user {} to enable all repos of type {}.", userName, repoTypes);
        federatedRepoService.enableRepos(repoTypes);
        return Response.status(OK).entity(String.format("Repos of type %s enabled.", repoTypes)).build();
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO)
    @Path(PATH_DRY_FULL_SYNC_REPO + "/{fileName}")
    public Response createDryFullSyncFile(@PathParam("fileName") String fileName, InputStream inputStream,
            @QueryParam("jpdId") String jpdId, @QueryParam("localRepoKey") String localRepoKey,
            @QueryParam("remoteRepoKey") String remoteRepoKey) {
        try {
            RepoPath repoPath = federatedFullSyncRepoService.deployDryFullSyncFile(fileName, inputStream, jpdId,
                    localRepoKey, remoteRepoKey);
            return Response.status(OK).entity(new SaveFileResponse(repoPath.toPath())).build();
        } catch (DryFullSyncException e) {
            log.error("Failed to create file {} due to : {}", fileName, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO)
    @Path(PATH_FULL_SYNC_REPO + "/{fileName}")
    public Response createFullSyncFile(@PathParam("fileName") String fileName, InputStream inputStream,
            @QueryParam("eventId") long eventId) {
        try {
            RepoPath repoPath = federatedFullSyncRepoService.deployFullSyncFile(fileName, inputStream, eventId);
            return Response.status(OK).entity(new SaveFileResponse(repoPath.toPath())).build();
        } catch (FullSyncException e) {
            log.debug("Failed to create file {} due to : ", fileName, e);
            log.error("Failed to create file {} due to : {}", fileName, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @Produces(TEXT_PLAIN)
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO)
    @Path(PATH_FULL_SYNC_SYSTEM_REPO)
    public Response createFullSyncFilesRepo() {
        try {
            federatedFullSyncRepoService.createFullSyncRepo();
            return Response.status(OK).entity("Successfully created system repo").build();
        } catch (FullSyncException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @GET
    @Produces(APPLICATION_JSON)
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO)
    @Path(PATH_FULL_SYNC_REPO + "/{fileName}")
    @ActivityDetails(group = RestApiGroup.DOWNLOAD)
    public Response downloadFullSyncFile(@PathParam("fileName") String fileName) {
        try {
            return federatedFullSyncRepoService.downloadFullSyncFile(fileName);
        } catch (FullSyncException e) {
            if (e.getCause() != null && e.getCause() instanceof RestException restException) {
                return Response.status(restException.getStatusCode())
                        .entity("An error has occurred when streaming the " +
                                "response from system repo in local artifactory, " + e.getMessage() + ": " + fileName)
                        .build();
            }
            log.debug("Failed to download file {} due to : ", fileName, e);
            log.error("Failed download file {} due to : {}", fileName, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @AllowedPermissionScope(INTERNAL_SCOPE_FULL_SYNC_SYSTEM_REPO)
    @Consumes(APPLICATION_JSON)
    @Path(PATH_FULL_SYNC_REPO + "/{fileName}")
    public Response deleteFullSyncFile(@PathParam("fileName") String fileName) {
        try {
            federatedFullSyncRepoService.undeployFullSyncFile(fileName);
            return Response.status(NO_CONTENT).build();
        } catch (Exception e) {
            log.debug("Failed to delete file {} due to : ", fileName, e);
            log.error("Failed to delete file {} due to : {}", fileName, e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @Path("recovery")
    @Produces(APPLICATION_JSON)
    public Response recovery(@QueryParam("repoKey") String repoKey) {
        try {
            log.info("Received request to trigger federated recovery...");
            if (StringUtils.isBlank(repoKey)) {
                federatedRepoService.triggerRecovery(true);
            } else {
                federatedRepoService.triggerManualRecoveryForRepository(repoKey);
            }
        } catch (BadRequestException e) {
            String errorMessage =
                    String.format("Failed to trigger recovery for repository %s due to: %s", repoKey, e.getMessage());
            log.error(errorMessage, repoKey, e.getMessage());
            log.debug("Failed to trigger recovery for repository due to:", e);
            return Response.status(SC_BAD_REQUEST).entity(errorMessage).build();
        }
        return Response.status(SC_OK).entity("Recovery triggered successfully").build();
    }

    @GET
    @Produces({APPLICATION_JSON})
    @Path("federatedRepoConfigInfo")
    @AllowedPermissionScope(scopes = {INTERNAL_SCOPE_FED_REPOCONFIG_INFO, INTERNAL_SCOPE_FED_REPOCONFIG_INFO_READ})
    public Response federatedRepoConfigInfo() {
        List<FederatedConfigInfo> federatedRepoConfigInfo = federatedRepoService.getFederatedRepoConfigInfo();
        return Response.ok().entity(federatedRepoConfigInfo).build();
    }

    @GET
    @Produces({APPLICATION_JSON})
    @Path("federatedConfigInfo/{repoKey}")
    @AllowedPermissionScope(scopes = {INTERNAL_SCOPE_FED_REPOCONFIG_INFO, INTERNAL_SCOPE_FED_REPOCONFIG_INFO_READ})
    public Response federatedConfigInfo(@PathParam("repoKey") String repoKey) {
        FederatedConfigInfo federatedConfigInfo = federatedRepoService.getFederatedRepoConfigInfo(repoKey);
        if (federatedConfigInfo == null) {
            return Response.status(SC_BAD_REQUEST)
                    .entity("No federated repository configuration found for repo: " + repoKey)
                    .build();
        }
        return Response.ok().entity(federatedConfigInfo).build();
    }

    @POST
    @Produces({APPLICATION_JSON})
    @Path("disableRemoteMember")
    public Response disableRemoteMemberFederatedRepo(@QueryParam("localRepoKey") String localRepoKey,
            @RequestBody MirrorMemberInfo mirrorMemberInfo, @QueryParam("version") Long version) {
        try {
            return federatedRepoService.updateMemberStatus(localRepoKey, mirrorMemberInfo, false, version);
        } catch (BadRequestException | ArtifactoryFederationServiceException e) {
            return Response.status(SC_BAD_REQUEST)
                    .entity("Failed to disable remote member due to " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Produces({APPLICATION_JSON})
    @Path("member/enable")
    public Response enableRemoteMemberFederatedRepo(@QueryParam("localRepoKey") String localRepoKey,
            @RequestBody MirrorMemberInfo mirrorMemberInfo, @QueryParam("version") Long version) {
        try {
            return federatedRepoService.updateMemberStatus(localRepoKey, mirrorMemberInfo, true, version);
        } catch (BadRequestException | ArtifactoryFederationServiceException e) {
            return Response.status(SC_BAD_REQUEST)
                    .entity("Failed to enable remote member due to " + e.getMessage())
                    .build();
        }
    }

    @GET
    @Produces({APPLICATION_JSON})
    @Path("lastIdInSystem")
    public Response lastIdInSystem() {
        long repoAndLastEventId = federatedRepoService.lastIdInTheSystem();
        return Response.ok().entity(repoAndLastEventId).build();
    }

    @GET
    @Produces({TEXT_PLAIN})
    @Path("rtfs")
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    @AllowedPermissionScope(scopes = {INTERNAL_SCOPE_RTFS, INTERNAL_SCOPE_RTFS_READ})
    public Response rtfsStatus() {
        log.debug("Received request to check RTFS readiness status from: {}", httpRequest.getRequestURI());
        boolean isReady = federatedRepoService.isMigrationOver();
        log.debug("Returning RTFS readiness status: {}", isReady);
        return Response.ok().entity(isReady).build();
    }

    @POST
    @Produces({TEXT_PLAIN})
    @Path("reconnect")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response reconnectToClient(@DefaultValue("false") @QueryParam("internal") boolean internal) {
        try {
            if (EdgeUtils.isEdgeLicensed() || !ContextHelper.get().beanForType(AddonsManager.class)
                    .isEntitled(EntitlementType.FEDERATED_REPOSITORY)) {
                return Response.status(SC_BAD_REQUEST)
                        .entity("Federation is not allowed in this Artifactory instance").build();
            }

            federatedRepoService.reconnectFederationClient(internal);
            return Response.status(SC_OK).entity(true).build();
        } catch (Exception e) {
            log.error("Error occurred processing the request to reconnect the federation client due to: ", e);
            return Response.status(SC_FAILED_DEPENDENCY).entity(e.getMessage()).build();
        }
    }

    @POST
    @Produces({TEXT_PLAIN})
    @Consumes({APPLICATION_JSON})
    @Path("queue/remaining")
    public Response queueRemainingEventsToProcess(@RequestBody QueuePosition lastKnownState) {
        log.debug("Received request for repo '{}' for member '{}/{}' to check amount events left to reach event id #{}",
                lastKnownState.getRepoKey(), lastKnownState.getRemoteUrl(), lastKnownState.getRemoteRepoKey(),
                lastKnownState.getEventId());
        Long size = federatedRepoService.remainingEventsInQueue(lastKnownState);
        log.info("#{} Events left for repo '{}' for member '{}/{}'", size, lastKnownState.getRepoKey(),
                lastKnownState.getRemoteUrl(), lastKnownState.getRemoteRepoKey());
        return Response.status(SC_OK).entity(size).build();
    }

    @PUT
    @Produces({TEXT_PLAIN})
    @Path("queue/stop")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response queueStop(@QueryParam("remote_member") String remoteMemberUrl,
            @QueryParam("remote_member_key") String remoteMemberKey,
            @QueryParam("source_key") String sourceKey) {
        try {
            federatedRepoService.queueStop(remoteMemberUrl, remoteMemberKey, sourceKey);
            String message =
                    "Queue for member %s/%s and repo %s stopped".formatted(remoteMemberUrl, remoteMemberKey, sourceKey);
            log.info(message);
            return Response.status(ACCEPTED).entity(message).build();
        } catch (BadRequestException e) {
            String message = "Queue stop for repo %s failed due to: %s".formatted(sourceKey, e.getMessage());
            return Response.status(SC_BAD_REQUEST).entity(message).build();
        }
    }

    @PUT
    @Produces({TEXT_PLAIN})
    @Path("queue/deregister")
    @RolesAllowed({HaRestConstants.ROLE_HA})
    public Response deregisterQueue(@QueryParam("remote_member") String remoteMemberUrl,
            @QueryParam("remote_member_key") String remoteMemberKey,
            @QueryParam("source_key") String sourceKey) {
        try {
            federatedRepoService.deregisterQueue(sourceKey, remoteMemberUrl, remoteMemberKey);
            String message =
                    "Queue for member %s/%s and repo %s removed".formatted(remoteMemberUrl, remoteMemberKey, sourceKey);
            log.info(message);
            return Response.status(ACCEPTED).entity(message).build();
        } catch (BadRequestException e) {
            String message = "Queue de-register for repo %s failed due to: %s".formatted(sourceKey, e.getMessage());
            return Response.status(SC_BAD_REQUEST).entity(message).build();
        }
    }

    @POST
    @Produces({TEXT_PLAIN})
    @Consumes({APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    @Path("queue/reset")
    public Response queueReset(@QueryParam("time") Long timeToReset, @QueryParam("internal") boolean internal,
            @RequestBody QueueState queueState) {
        try {
            federatedRepoService.queueResetTo(queueState, internal, timeToReset);
            String message =
                    "Queue for member %s/%s and repo %s started".formatted(queueState.getRemoteUrl(),
                            queueState.getRemoteRepoKey(), queueState.getRepoKey());
            return Response.status(ACCEPTED).entity(message).build();
        } catch (Exception e) {
            String errMsg = "Failed to reset queue %s due to: %s".formatted(queueState.getRepoKey(), e.getMessage());
            log.error(errMsg, e);
            return Response.status(BAD_REQUEST).entity(errMsg).build();
        }
    }

    @GET
    @Produces({TEXT_PLAIN})
    @Path("tenant/move/status")
    @AllowedPermissionScope(INTERNAL_SCOPE_TENANT_MOVE_STATUS)
    public Response tenantMoveStatus() {
        try {
            String status = federatedRepoService.getTenantMoveStatus().name();
            log.info("Received request for tenant move status: {}", status);
            return Response.ok().entity(status).build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Produces({TEXT_PLAIN})
    @Path("rtfs")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response enableRTFS(@QueryParam("enable") boolean rtfsEnable, @QueryParam("internal") boolean internal) {
        log.info("Received request for RTFS enable: {} internal request :{}", rtfsEnable, internal);
        String enabled = rtfsEnable ? "enabled" : "disabled";
        try {
            if (!internal) {
                allowOnlyMigrationToolCalls();
            }
            federatedRepoService.initFederationClient(rtfsEnable, internal);
            return Response.status(SC_CREATED).entity("Federation service %s".formatted(enabled)).build();
        } catch (Exception e) {
            log.error("Error occurred processing the request to {} of the federation client due to: {}",
                    enabled, e.getMessage());
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Produces({TEXT_PLAIN})
    @Path("migration/status")
    @AllowedPermissionScope(INTERNAL_SCOPE_MIGRATION_STATUS)
    public Response migrationStatus() {
        try {
            FederationMigrationStatus status = federatedRepoService.getCurrentFederationStatus();
            if (status != null) {
                return Response.ok().entity(status.name()).build();
            }
            return Response.ok().entity("Status not configured").build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Produces({TEXT_PLAIN})
    @Path("migration")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response migrationStatus(@QueryParam("status") FederationMigrationStatus status,
            @QueryParam("internal") boolean internal) {
        try {
            if (!internal) {
                allowOnlyMigrationToolCalls();
            }
            federatedRepoService.updateFederationStatus(status, internal);
            return Response.status(CREATED).entity("Status %s was set".formatted(status)).build();
        } catch (Exception e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("prioritization/{repoKey}")
    @Consumes(APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response updateFederationPriority(@PathParam("repoKey") String repoKey,
            @QueryParam(value = "priorityLevel") String priorityLevel) {
        try {
            RepoPriority priority = RepoPriority.getPriority(priorityLevel);
            FederationPriorityUpdateRtfsResponse rtfsResponse = federatedRepoService.updateFederationPriority(repoKey,
                    priority);
            return Response.status(SC_OK).entity(rtfsResponse.getMessage()).build();
        } catch (BadRequestException | ArtifactoryFederationServiceException e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("queue/state")
    @Produces({TEXT_PLAIN})
    @AllowedPermissionScope(INTERNAL_QUEUE_STATUS)
    public Response internalQueueStatus() {
        try {
            MemberQueueState status = federatedRepoService.getInternalQueueState();
            return Response.status(SC_OK).entity(status).build();
        } catch (BadRequestException e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("queue/state/{state}")
    @Consumes({TEXT_PLAIN})
    @AllowedPermissionScope(INTERNAL_QUEUE_STATUS)
    public Response updateQueueState(@PathParam("state") MemberQueueState state,
            @QueryParam("timeToReset") Long time) {
        try {
            federatedRepoService.updateCursor(state, time);
            return Response.status(NO_CONTENT).build();
        } catch (BadRequestException e) {
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("propagateCreatedTime")
    @Consumes({TEXT_PLAIN})
    public Response updateShouldPropagateCreatedProperty(@QueryParam("enabled") Boolean enabled) {
        try {
            if (enabled == null) {
                return Response.status(BAD_REQUEST)
                        .entity("Query param 'enabled' is required for updating the property").build();
            }
            federatedRepoService.updatePropagateCreatedTimeProperty(enabled);
            return Response.status(OK).build();
        } catch (ArtifactoryFederationServiceException e) {
            return Response.status(INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("task/submit")
    @Consumes({APPLICATION_JSON})
    @AllowedPermissionScope(INTERNAL_SEND_BINARY_TASK_REQUEST)
    public Response submitTask(@RequestBody TaskRequest taskRequest) {
        log.debug("Received request for repo '{}' to handle task '{}'", taskRequest.repoKey(), taskRequest.sha1());
        try {
            federatedRepoService.submitTask(taskRequest);
            return Response.status(SC_ACCEPTED).entity("Task was submitted successfully").build();
        } catch (BinariesTaskException e) {
            return Response.status(e.getStatusCode()).entity(e.getMessage()).build();
        }
    }

    @GET
    @Produces({APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path("compareResult/{fileName}")
    public Response getDryFullSyncComparisonFile(@PathParam("fileName") String fileName) {
        if (!fileName.endsWith("compare-result.json")) {
            return Response.status(SC_BAD_REQUEST).entity("Invalid file name," +
                    " requests must be for dryfullsync files only!").build();
        }
        SystemRepos.SystemRepoConfig fullSyncRepo = SystemRepos.SystemRepoConfig.getSystemRepo(FULL_SYNC_REPO_KEY);
        var repoPath = RepoPathFactory.create(fullSyncRepo.getRepoKey(), fileName);
        if (!fileService.exists(repoPath)) {
            return Response.status(SC_BAD_REQUEST).entity(
                    String.format("File %s does not exist", fileName)).build();
        }
        return downloadDryFullSyncComparisonFile(repoPath);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({HaRestConstants.ROLE_HA})
    @Path("binary/provider/v2/check")
    public Response federatedBinaryProviderV2Check() {
        try {
            boolean isUsingFederatedBinaryProviderV2 = federatedRepoService.isFederatedBinaryProviderV2Active();
            return Response.ok(isUsingFederatedBinaryProviderV2).build();
        } catch (BinariesTaskException e) {
            return Response.status(e.getStatusCode()).entity(e.getMessage()).build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("inconsistentRepos")
    public Response getInconsistentRepositories() {
        try {
            return Response.ok(federatedRepoService.getInconsistentRepos()).build();
        } catch (Exception e) {
            String errMsg = "Failed to get inconsistent repositories due to: %s".formatted(e.getMessage());
            return Response.status(BAD_REQUEST).entity(errMsg).build();
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("removeMember")
    public Response forceRemoveMember(@RequestBody String memberUrl) {
        try {
            federatedRequestCustomContext.setSkipDisabledCheck(true);
            MemberRemovalResponse response = federatedRepoService.forceRemoveMember(memberUrl);
            int statusCode = response.getSuccess() ? SC_OK : SC_BAD_REQUEST;
            return Response.status(statusCode).entity(response.getMessage()).build();
        } catch (ArtifactoryFederationServiceException e) {
            return Response.status(SC_BAD_REQUEST).entity(e.getMessage()).build();
        } finally {
            federatedRequestCustomContext.clear();
        }
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path("snapshotFederatedRepos")
    public Response triggerFederatedReposStateSnapshot() {
        try {
            return federatedRepoService.snapshotFederatedRepos();
        } catch (ArtifactoryFederationServiceException e) {
            return Response.status(SC_INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path("migration/status/verbose")
    public Response getMigrationVerboseStatus() {
        try {
            MigrationVerboseStatus migrationVerboseStatus = federatedRepoService.getMigrationVerboseStatus();
            return Response.ok().entity(migrationVerboseStatus).build();
        } catch (Exception e) {
            log.error("Error in processing the request to get migration verbose status due to: {}", e.getMessage());
            return Response.status(SC_INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Produces({TEXT_PLAIN})
    @Path("migration/status/verbose")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    public Response setMigrationVerboseStatus(@RequestBody MigrationVerboseStatus status) {
        try {
            if (status == null) {
                return Response.status(SC_BAD_REQUEST).entity("Verbose migration status must not be null").build();
            }
            federatedRepoService.setMigrationVerboseStatus(status);
            return Response.status(CREATED).entity("Migration verbose status was set").build();
        } catch (Exception e) {
            log.error("Error in processing the request to set migration verbose status due to: {}", e.getMessage());
            return Response.status(SC_INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    Response downloadDryFullSyncComparisonFile(RepoPath dryFullSyncResultPath) {
        InternalArtifactoryRequest request = createInternalRequestDisableRedirect(dryFullSyncResultPath);
        JerseyArtifactoryResponse response = new JerseyArtifactoryResponse();
        try {
            return securityService.callAsSystem(() -> {
                downloadService.process(request, response);
                if (response.isError()) {
                    return Response.status(SC_INTERNAL_SERVER_ERROR).entity(
                            "Internal error when processing download request").build();
                }
                log.info("Successfully processed dryfullsync request for file {}", dryFullSyncResultPath.getPath());
                return response.build();
            });
        } catch (Exception e) {
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity("Unexpected error when processing download request")
                    .build();
        }
    }

    /**
     * Handles template change notifications from the external Grid service.
     * This endpoint allows the Grid service to notify Artifactory when templates
     * are created, updated, or deleted, triggering cache invalidation.
     *
     * @param event The template change event
     * @return Response indicating success or failure
     */
    @POST
    @Consumes({APPLICATION_JSON})
    @Produces({APPLICATION_JSON})
    @Path("grid/topology/events")
    @AllowedPermissionScope(INTERNAL_SCOPE_GRID_TOPOLOGY_CHANGE)
    public Response notifyGridTopologyChange(@RequestBody GridTopologyChangeEvent event) {
        try {
            if (event == null) {
                return Response.status(SC_BAD_REQUEST)
                        .entity("Grid topology change event is required")
                        .build();
            }

            if (event.getGridTopologyId() == null || event.getGridTopologyId().isEmpty()) {
                return Response.status(SC_BAD_REQUEST)
                        .entity("Grid topology change event must include a gridTopologyId")
                        .build();
            }

            if ((event.getChangeType() == GridTopologyChangeType.CREATED ||
                    event.getChangeType() == GridTopologyChangeType.UPDATED) &&
                    event.getGridTopology() == null) {
                return Response.status(SC_BAD_REQUEST)
                        .entity("Grid topology data is required for CREATED and UPDATED events")
                        .build();
            }

            // Handle grid topology change via federation service (which updates DB and invalidates cache)
            federatedRepoService.handleGridTopologyChange(event.getGridTopology(), event.getChangeType(),
                    event.getGridTopologyId());
            log.info("Processed grid topology change for grid topology {} due to {} event from Grid service",
                    event.getGridTopologyId(), event.getChangeType());

            return Response.status(SC_OK).entity("Grid topology change processed successfully").build();
        } catch (Exception e) {
            log.error("Failed to process grid topology change notification: {}", e.getMessage(), e);
            return Response.status(SC_INTERNAL_SERVER_ERROR)
                    .entity("Failed to process grid topology change notification: " + e.getMessage())
                    .build();
        }
    }

    private void allowOnlyMigrationToolCalls() throws ArtifactoryFederationServiceException {
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        String version = httpRequest.getHeader("Version");

        if (StringUtils.isBlank(userAgent) || !userAgent.equals(MIGRATION_TOOL_USER_AGENT)) {
            throw new ArtifactoryFederationServiceException("Access denied: " +
                    "This endpoint is restricted to migration tool calls only.");
        }

        if (StringUtils.isBlank(version) || !Version.parse(version, false)
                .isHigherThanOrEquivalentTo(MIN_SUPPORTED_MIGRATION_TOOL_VERSION)) {
            throw new ArtifactoryFederationServiceException("Access denied: " +
                    "Migration tool version must be at least " + MIN_SUPPORTED_MIGRATION_TOOL_VERSION);
        }
    }
}
