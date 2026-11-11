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

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.distribution.DistributionAddon;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.ReplicationRestConstants;
import org.artifactory.api.rest.distribution.bundle.models.FileSpec;
import org.artifactory.api.rest.replication.ReplicationRequest;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.artifactory.rest.util.ResponseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static org.artifactory.security.permissionscope.PermissionScopeToken.REPO_REPLICATION_EXECUTE;

/**
 * REST resource for invoking local and remote replication procedures.
 *
 * @author Noam Y. Tenne
 */
@Component
@Path(ReplicationRestConstants.ROOT)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
public class ReplicationResource {
    private static final Logger log = LoggerFactory.getLogger(ReplicationResource.class);
    @Context
    HttpServletRequest httpRequest;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private CentralConfigService centralConfigService;


    /**
     * Returns the latest replication status of the given path if annotated
     *
     * @param path Path to check for annotations
     * @return Response
     */
    @GET
    @Produces({ReplicationRestConstants.MT_REPLICATION_STATUS, MediaType.APPLICATION_JSON})
    @Path("{path: .+}")
    public Response getReplicationStatus(@PathParam("path") String path) {
        RepoPath repoPath = InternalRepoPathFactory.create(path);
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.addonByType(RestAddon.class).getReplicationStatus(repoPath);
    }

    /**
     * Schedule immediate replication for local/multipush/remote repository.
     *
     * @param path                Path for replication
     * @param replicationRequests List of replicationRequests objects
     * @return Response that is translated to a JSON content
     * @throws IOException
     */
    @POST
    @AllowedPermissionScope(REPO_REPLICATION_EXECUTE)
    @Consumes({MediaType.APPLICATION_JSON})
    @Path("execute/{path: .+}")
    public Response replicateAll(@PathParam("path") String path, List<ReplicationRequest> replicationRequests,
            @QueryParam("strategy") String strategy)
            throws IOException {
        AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
        return addonsManager.addonByType(RestAddon.class)
                .replicate(RepoPathFactory.create(path), replicationRequests, strategy);
    }


    /**
     * Schedule immediate replication for local/multipush/remote repository.
     * Since no payload supplied by the user, Artifactory will schedule the replication for existing replication
     * configurations
     *
     * @param path Path for replication
     * @return JSON response
     */
    @POST
    @AllowedPermissionScope(REPO_REPLICATION_EXECUTE)
    @Path("execute/{path: .+}")
    public Response replicateAllNoJson(@PathParam("path") String path, @QueryParam("strategy") String strategy)
            throws IOException {
        if (!StringUtils.isBlank(strategy)) {
            List<LocalReplicationDescriptor> replicationDescriptors =
                    centralConfigService.getDescriptor().getLocalReplications()
                            .stream().filter(desc -> desc.getRepoKey().equals(path))
                            .filter(ReplicationBaseDescriptor::isEnabled).collect(Collectors.toList());

            if (replicationDescriptors.isEmpty()) {
                log.info("Request replication/execute/{} ignored. The repository was not found.", path);
                return Response.status(NOT_FOUND).build();
            }

            ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
            replicationDescriptors
                    .forEach(replicatonDescriptor -> replicationAddon.setPushStrategy(replicatonDescriptor, strategy));
        }

        return replicateAll(path, Lists.newArrayList(), strategy);
    }

    @POST
    @Path("replicate/file/{tx_path: .+}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Deprecated
    public Response replicateArtifact(FileSpec fileSpec,
            @HeaderParam(ReplicationRestConstants.DELEGATE_TOKEN_HEADER) String delegateToken,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @PathParam("tx_path") String transactionPath) {
        validateReplicateArtifactParameters(fileSpec, delegateToken, transactionPath);
        fileSpec.setInternalTmpPath(transactionPath);
        DistributionAddon distributionAddon = addonsManager.addonByType(DistributionAddon.class);
        BasicStatusHolder statusHolder = distributionAddon.distributeArtifact(fileSpec, delegateToken, auth);
        if (statusHolder.hasErrors()) {
            return createErrorResponse(statusHolder);
        }
        return Response.ok(ResponseUtils.getResponse(statusHolder)).build();
    }

    @POST
    @Path("replicate/file/streaming/{tx_path: .+}")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    public Response replicateArtifactStreaming(FileSpec fileSpec,
            @HeaderParam(ReplicationRestConstants.FILE_TXID_HEADER) String fileTransactionId,
            @HeaderParam(ReplicationRestConstants.DELEGATE_TOKEN_HEADER) String delegateToken,
            @HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
            @PathParam("tx_path") String transactionPath,
            @QueryParam("include_properties") boolean includeProperties) {
        log.debug("Replicate streaming request for file: {}, includeProperties: {}", fileSpec, includeProperties);
        validateReplicateArtifactParameters(fileSpec, delegateToken, transactionPath);
        fileSpec.setInternalTmpPath(transactionPath);
        DistributionAddon distributionAddon = addonsManager.addonByType(DistributionAddon.class);
        
        if (includeProperties) {
            distributionAddon.populateArtifactProperties(fileSpec);
        }
        
        try {
            ChecksumsInfo checksums = distributionAddon.validateFileAndGetChecksums(fileSpec);
            return Response.ok()
                    .entity((StreamingOutput) outputStream -> distributionAddon
                            .distributeArtifactStreaming(fileTransactionId, fileSpec, delegateToken, auth, checksums,
                                    outputStream))
                    .type(MediaType.APPLICATION_OCTET_STREAM)
                    .build();
        } catch (StatusHasErrorException e) {
            return createErrorResponse(e.getStatus());
        }
    }

    private void validateReplicateArtifactParameters(FileSpec fileSpec, String delegateToken, String transactionPath) {
        if (StringUtils.isBlank(delegateToken)) {
            throw new BadRequestException("Missing X-Auth-Token-Delegate header");
        }
        if (StringUtils.isBlank(transactionPath)) {
            throw new BadRequestException("Missing transaction id");
        }
        if (StringUtils.isBlank(fileSpec.getTargetPath())) {
            throw new BadRequestException("No target repo path specified to use for distributing the artifact " +
                    fileSpec.getSourcePath());
        }
    }

    private Response createErrorResponse(BasicStatusHolder statusHolder) {
        int code = statusHolder.getMostImportantErrorStatusCode().getStatusCode();
        return Response.status(code).entity(ResponseUtils.getErrorResponse(statusHolder)).build();
    }
}
