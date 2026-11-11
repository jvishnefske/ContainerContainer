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

package org.artifactory.rest.resource.search.types;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.rest.constant.SearchRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.jfrog.common.StringList;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;

/**
 * Resource for retrieving artifacts with bad checksums.
 *
 * @author Tomer Cohen
 */
@Slf4j
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
public class BadChecksumSearchResource {

    private final RestAddon restAddon;
    private final SecurityService securityService;
    private final HttpServletRequest request;

    public BadChecksumSearchResource(SecurityService securityService, RestAddon restAddon,
            HttpServletRequest request) {
        this.restAddon = restAddon;
        this.securityService = securityService;
        this.request = request;
    }

    /**
     * Searches for artifacts by checksum
     *
     * @param type          The {@link org.artifactory.checksum.ChecksumType} to search for
     * @param reposToSearch Specific repositories to search within
     * @return Search results object
     */
    @GET
    @Produces({SearchRestConstants.MT_BAD_CHECKSUM_SEARCH_RESULT, MediaType.APPLICATION_JSON})
    public Object get(@QueryParam("type") String type,
            @QueryParam(SearchRestConstants.PARAM_REPO_TO_SEARCH) StringList reposToSearch) throws IOException {
        if (!securityService.isAuthenticated()) {
            throw new AuthorizationRestException();
        }
        log.debug("Finding bad '{}' checksum artifacts in {} ", type, reposToSearch);
        try {
            return restAddon.searchBadChecksumArtifacts(type, reposToSearch, request);
        } catch (MissingRestAddonException mrae) {
            throw mrae;
        } catch (RuntimeException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }
}
