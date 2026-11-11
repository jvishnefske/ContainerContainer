/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.rest.resource.replication;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.addon.replication.event.ChannelType;
import org.artifactory.addon.replication.event.OutboundReplicationChannel;
import org.artifactory.addon.replication.event.ReplicationChannel;
import org.artifactory.addon.replication.event.ReplicationEventQueueData;
import org.artifactory.addon.replication.event.ReplicationOwnerModel;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.ReplicationsRestConstants;
import org.artifactory.api.rest.replication.MultipleReplicationConfigRequest;
import org.artifactory.api.rest.replication.ReplicationConfigRequest;
import org.artifactory.api.rest.replication.ReplicationEnableDisableRequest;
import org.artifactory.api.rest.restmodel.JsonUtil;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.rest.resource.BaseResource;
import org.artifactory.rest.services.ConfigServiceFactory;
import org.artifactory.util.HttpUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import static org.artifactory.api.rest.constant.HaRestConstants.ARTIFACTORY_HA_ORIGINATED_SERVER_ID;
import static org.artifactory.security.permissionscope.PermissionScopeToken.REPO_REPLICATION_READ;

/**
 * REST resource for configuring local and remote replication.
 *
 * @author mamo
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(ReplicationsRestConstants.ROOT)
public class ReplicationsResource extends BaseResource {
    private static final Logger log = LoggerFactory.getLogger(ReplicationsResource.class);
    private static final String NGINX_ACCEL_HEADER = "X-Accel-Buffering";

    @Context
    HttpServletRequest httpRequest;

    @Autowired
    private ConfigServiceFactory configFactory;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private SecurityService securityService;

    /**
     * get Single or multiple replications
     */
    @GET
    @Path("{repoKey: .+}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Produces({ReplicationsRestConstants.MT_REPLICATION_CONFIG_REQUEST,
            ReplicationsRestConstants.MT_MULTI_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    public Response getReplication() {
        return runService(configFactory.getReplication());
    }

    /**
     * get all configured replications
     */
    @GET
    @AllowedPermissionScope(REPO_REPLICATION_READ)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllReplications() {
        return runService(configFactory.getAllReplications());
    }

    /**
     * Create or replace single replication
     * RTFACT-9055 - We now support JSON as an array OR as an object in order to support legacy.
     * The GET method return an array of configurations in case of Enterprise license, and single object json in case
     * of Pro license, while we do support sending a PUT request of an object replication settings as well (and not
     * only as an array).
     */
    @PUT
    @Path("{repoKey: .+}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Consumes({ReplicationsRestConstants.MT_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    public Response addOrReplace() {
        try {
            return internalAddOrReplace();
        } catch (IOException e) {
            // We use inner try/catch for a reason: the IOException might be thrown in multiple lines
            log.error("Error adding or replacing replication", e);
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage())
                    .build();
        }
    }

    private Response internalAddOrReplace() throws IOException {
        // We get the inputStream in order to avoid filters parsing the request
        ServletInputStream inputStream = httpRequest.getInputStream();
        JsonParser jsonParser = JacksonFactory.createJsonParser(inputStream);
        try {
            // First we try parsing the request as a single 'ReplicationConfigRequest' object
            ReplicationConfigRequest replicationRequest = jsonParser.readValueAs(ReplicationConfigRequest.class);
            return runService(configFactory.createOrReplaceReplication(), replicationRequest);
        } catch (JsonMappingException e) {
            log.debug("Failed parsing the response", e);
            // If it wasn't a single object, try parsing the request as an array of ReplicationConfigRequest
            ReplicationConfigRequest[] replicationRequestArray = jsonParser
                    .readValueAs(ReplicationConfigRequest[].class);
            ArrayList<ReplicationConfigRequest> replicationList = Lists.newArrayList(replicationRequestArray);
            // Create a MultipleReplicationConfigRequest from the list and send it to addOrReplaceMultiple
            MultipleReplicationConfigRequest multiRequest = createMultipleReplicationRequestObject(replicationList);
            return addOrReplaceMultiple(multiRequest);
        } finally {
            IOUtils.closeQuietly(jsonParser);
        }
    }

    /**
     * Used to generate an instance of MultipleReplicationConfigRequest out of an array
     * compiled from multiple ReplicationConfigRequest.
     * Constraints:
     * cronExp will be the cronExp of the first object in the array
     * EnableEventReplication will be the EnableEventReplication of the first object in the array
     */
    private MultipleReplicationConfigRequest createMultipleReplicationRequestObject(
            ArrayList<ReplicationConfigRequest> configRequests) {
        MultipleReplicationConfigRequest request = new MultipleReplicationConfigRequest();

        if (configRequests.isEmpty()) {
            throw new BadRequestException("Request is empty");
        }

        request.setReplications(configRequests);
        ReplicationConfigRequest replicationRequest = configRequests.get(0);
        if (replicationRequest.getUrl() == null || replicationRequest.getCronExp() == null) {
            throw new BadRequestException("One of the required fields are empty");
        }
        request.setCronExp(replicationRequest.getCronExp());
        request.setEnableEventReplication(replicationRequest.getEnableEventReplication());

        return request;
    }

    /**
     * create or replace multiple replication
     */
    @PUT
    @Consumes({ReplicationsRestConstants.MT_MULTI_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Path("multiple{repoKey: .+}")
    public Response addOrReplaceMultiple(MultipleReplicationConfigRequest replicationRequest) {
        return runService(configFactory.createMultipleReplication(), replicationRequest);
    }

    /**
     * update existing single replication
     */
    @POST
    @Consumes({ReplicationsRestConstants.MT_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Path("{repoKey: .+}")
    public Response updateReplications(ReplicationConfigRequest replicationRequest) {
        return runService(configFactory.updateReplication(), replicationRequest);
    }

    /**
     * update existing multi replication
     */
    @POST
    @Consumes({ReplicationsRestConstants.MT_MULTI_REPLICATION_CONFIG_REQUEST, MediaType.APPLICATION_JSON})
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Path("multiple{repoKey: .+}")
    public Response updateMultipleReplications(MultipleReplicationConfigRequest replicationRequest) {
        return runService(configFactory.updateMultipleReplications(), replicationRequest);
    }

    /**
     * delete  existing single / multi replication
     */
    @DELETE
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Path("{repoKey: .+}")
    public Response deleteReplications() {
        return runService(configFactory.deleteReplicationsService());
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("enable")
    public Response enable(ReplicationEnableDisableRequest replicationEnableDisableRequest) {
        replicationEnableDisableRequest.setEnable(true);
        return runService(configFactory.enableDisableReplicationsService(), replicationEnableDisableRequest);
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("disable")
    public Response disable(ReplicationEnableDisableRequest replicationEnableDisableRequest) {
        replicationEnableDisableRequest.setEnable(false);
        return runService(configFactory.enableDisableReplicationsService(), replicationEnableDisableRequest);
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER, HaRestConstants.ROLE_HA})
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("channels/establishChannel")
    public Response establishChanel() {
        log.debug("Entering establishChannel method.");
        if (denyAnonymousReplicationCaller("establishChannel")) {
            return Response.status(HttpStatus.SC_UNAUTHORIZED).build();
        }
        ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
        if (replicationAddon.isDefault()) {
            log.debug("Replication services are disabled.");
            return Response.status(HttpStatus.SC_BAD_REQUEST).entity("Replication services are disabled.").build();
        }
        try (ServletInputStream inputStream = httpRequest.getInputStream()) {
            ReplicationOwnerModel replicationOwnerModel = JsonUtil
                    .mapDataToModel(inputStream, ReplicationOwnerModel.class);
            log.debug("ReplicationOwnerModel created from input stream. replicationOwnerModel: {}",
                    replicationOwnerModel);

            String targetNode = httpRequest.getRemoteAddr() + "#" + replicationOwnerModel.getOwner();
            String requestOrigin = httpRequest.getHeader(ARTIFACTORY_HA_ORIGINATED_SERVER_ID);
            String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
            log.debug("Target node: {}, Request origin: {}, User agent: {}", targetNode, requestOrigin, userAgent);

            ArtifactoryServiceVersion artifactoryServiceVersion =
                    HttpUtils.extractVersionFromHeader(userAgent).orElse(null);
            logVersion(artifactoryServiceVersion, userAgent, targetNode);

            ReplicationChannel replicationChannel = replicationAddon
                    .establishReplicationChannel(replicationOwnerModel, targetNode, requestOrigin,
                            artifactoryServiceVersion);
            log.debug("ReplicationChannel established.");

            ChannelType channelType = replicationChannel.getChannelType();
            if (channelType.isOutboundStream()) {
                OutboundReplicationChannel outboundChannel = (OutboundReplicationChannel) replicationChannel;
                log.debug("Channel type is outbound stream.");
                return Response.ok()
                        .entity((StreamingOutput) outboundChannel::write)
                        .header(NGINX_ACCEL_HEADER, "no")
                        .header(ARTIFACTORY_HA_ORIGINATED_SERVER_ID, outboundChannel.getOwner())
                        .type(MediaType.APPLICATION_OCTET_STREAM)
                        .build();
            } else {
                log.debug("Channel type is not outbound stream.");
                return Response.ok().build();
            }

        } catch (IOException e) {
            log.error("Error occurred during pull replication establish replication channel", e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER, HaRestConstants.ROLE_HA})
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("channels/removeChannel")
    public Response removeListeners() {
        if (denyAnonymousReplicationCaller("removeChannel")) {
            return Response.status(HttpStatus.SC_UNAUTHORIZED).build();
        }
        ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
        try {
            ReplicationOwnerModel replicationOwnerModel = JsonUtil
                    .mapDataToModel(httpRequest.getInputStream(), ReplicationOwnerModel.class);
            replicationAddon.removeReplicationChannel(replicationOwnerModel);
            return Response.ok().build();
        } catch (IOException e) {
            log.error("Error occurred during pull replication remove channel propagation", e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
        }
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER, HaRestConstants.ROLE_HA})
    @Path("propagateEvents")
    public Response handlePullReplicationEvents() {
        if (denyAnonymousReplicationCaller("propagateEvents")) {
            return Response.status(HttpStatus.SC_UNAUTHORIZED).build();
        }
        try {
            ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
            String originNode = httpRequest.getHeader("X-Artifactory-HA-Originated-ServerId");
            ReplicationEventQueueData events = JsonUtil.mapDataToModel(httpRequest.getInputStream(),
                    ReplicationEventQueueData.class);
            replicationAddon.handlePropagatedRemoteReplicationEvents(originNode, events);
            return Response.ok().build();
        } catch (IOException e) {
            log.error("Error occurred during pull replication event propagation", e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path("channels")
    public Response getAllChannelsListeners() {
        return getAllRepoListeners(null);
    }

    @GET
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path("channels/{repoKey}")
    public Response getAllRepoListeners(@PathParam("repoKey") String repoKey) {
        ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
        Set<String> remoteRepoSubscribers = replicationAddon.getRemoteRepoSubscribers(repoKey);
        return Response.ok(remoteRepoSubscribers).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    @POST
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Path("channels/release")
    public Response releaseAllReposSubscribers() {
        ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
        replicationAddon.releaseAllReposSubscribers();
        return Response.ok().build();
    }

    // Replication-channel endpoints accept either a real authenticated user (cross-site pull replication,
    // via the remote-repo's configured credentials) or an HA peer (HaRestAuthenticationFilter -> ROLE_HA).
    // Anonymous must always be rejected: when global anonymous access is enabled, RestAuthenticationFilter
    // pre-elevates anon to ROLE_USER, so @RolesAllowed alone cannot block it. See RTDEV-85226.
    private boolean denyAnonymousReplicationCaller(String endpoint) {
        if (securityService.isAnonymous()) {
            log.warn("Rejected anonymous call to /api/replications/{} from {}", endpoint, httpRequest.getRemoteAddr());
            return true;
        }
        return false;
    }

    private static void logVersion(ArtifactoryServiceVersion artifactoryServiceVersion, String userAgent,
            String targetNode) {
        if (artifactoryServiceVersion == null) {
            log.debug("Extracted null version from Header User-Agent {} of Artifactory {}", userAgent, targetNode);
        } else {
            log.debug("Extracted version from Header User-Agent {} of Artifactory {}", userAgent, targetNode);
        }
    }
}
