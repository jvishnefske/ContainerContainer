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

package org.artifactory.rest.resource.system;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.rest.search.result.StatusRestResult;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static org.artifactory.addon.AddonsManager.NO_LICENSE_HASH_WITH_OSS_PADDING;

/**
 * Resource to get information about Artifactory's current nodes' status (version, state and license)
 *
 * @author Rotem Kfir
 */
@Component
@Path(SystemRestConstants.PATH_ROOT + "/" + SystemRestConstants.PATH_STATUS)
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@ActivityDetails(group = RestApiGroup.SYSTEM_INFO_API)
public class SystemStatusResource {

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private ArtifactoryServersCommonService serversService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getArtifactoryStatus() {
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        boolean isHa = haAddon.isHaEnabled();

        List<ArtifactoryServer> artifactoryServers = serversService.getActiveMembers();
        List<StatusRestResult.NodeStatus> nodes = convertToNodeStatuses(artifactoryServers);

        return Response.ok()
                .entity(new StatusRestResult(isHa, nodes))
                .build();
    }

    private List<StatusRestResult.NodeStatus> convertToNodeStatuses(List<ArtifactoryServer> servers) {
        return servers.stream()
                .map(this::convertToNodeStatus)
                .collect(Collectors.toList());
    }

    private StatusRestResult.NodeStatus convertToNodeStatus(ArtifactoryServer server) {
        String licenseHash =
                NO_LICENSE_HASH_WITH_OSS_PADDING.equals(server.getLicenseKeyHash()) ? null : server.getLicenseKeyHash();
        return new StatusRestResult.NodeStatus(server.getServerId(), server.getServerState().name(),
                server.getArtifactoryVersion(), licenseHash);
    }
}
