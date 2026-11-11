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

import com.google.common.annotations.VisibleForTesting;
import org.apache.http.HttpStatus;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.access.AccessService;
import org.artifactory.storage.StorageService;
import org.artifactory.storage.db.InternalDbService;
import org.artifactory.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Resource to get information about Artifactory current state.
 * The method will:
 * <ul><li>Check that DB behaves correctly (do an actual DB retrieve)</li>
 * <li>Check that the logger behaves correctly (read and/or write to log)</li></ul>
 * Upon results return:
 * <ul><li>Code 200 with text exactly equal to "OK" if all is good</li>
 * <li>Code 500 with failure description in the text if something wrong</li></ul>
 *
 * @author Fred Simon
 */
@Component
@Path(SystemRestConstants.PATH_ROOT + "/" + SystemRestConstants.PATH_PING)
@ActivityDetails(group = RestApiGroup.PROBES_API)
public class PingResource {

    @Context
    @VisibleForTesting
    HttpServletRequest httpRequest;

    private static final Logger log = LoggerFactory.getLogger(PingResource.class);

    private AddonsManager addonsManager;

    @Autowired
    public PingResource(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    /**
     * @return The artifactory state "OK" or "Failure"
     */
    @GET
    @Produces({MediaType.TEXT_PLAIN})
    public Response pingArtifactory(@QueryParam("skipLicenseCheck") boolean skipLicenseCheck) {
        try {
            log.debug("Received ping call");
            if ((addonsManager.getArtifactoryRunningMode().isHa() &&
                    isLockdown(skipLicenseCheck))) {
                log.error("Ping failed since the server state is blocked");
                return Response.status(HttpStatus.SC_SERVICE_UNAVAILABLE).entity("Server state is offline").build();
            }
            if (ContextHelper.get().isOffline()) {
                log.error("Ping failed since the server state is offline");
                return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity("Server state is offline").build();
            }
            ArtifactoryHome artifactoryHome = ArtifactoryHome.get();
            if (!artifactoryHome.getDataDir().canWrite() || !artifactoryHome.getLogDir().canWrite()) {
                log.error("Ping failed due to file system access to data or log dir failed");
                return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity("File system access failed").build();
            }
            String userAgent = HttpUtils.getRequestUserAgent(httpRequest);
            boolean isLocalOnly = userAgent != null && userAgent.toLowerCase().contains("jfrog");
            ContextHelper.get().beanForType(StorageService.class).ping(isLocalOnly);
            ContextHelper.get().beanForType(InternalDbService.class).health();
            log.debug("Performing ping request to Access");
            ContextHelper.get().beanForType(AccessService.class).ping();
        } catch (Exception e) {
            log.error("Error during ping test", e);
            return Response.status(HttpStatus.SC_INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
        log.debug("Ping successful");
        return Response.ok().entity("OK").build();
    }

    /**
     * Router passes this flag as true since 'healthy' for it means the node is capable (infrastructure-wise)
     * A load balancer that supports probe functionality (say, nginx) will still want to get 503 in this case so it does
     * not pass requests to this node (where the router should in fact pass since the load balancer above it decided
     * to).
     */
    private boolean isLockdown(boolean skipLicenseCheck) {
        return !skipLicenseCheck && addonsManager.lockdown();
    }
}
