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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.SearchRestConstants;
import org.artifactory.api.rest.search.result.PatternResultFileSet;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.LocalCacheRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.util.RestUtils;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.artifactory.rest.exception.RestException;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Exposes the pattern searcher to REST via the REST addon
 *
 * @author Noam Y. Tenne
 */
@Slf4j
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
public class PatternSearchResource {

    private SecurityService securityService;
    private RepositoryService repositoryService;
    private RestAddon restAddon;
    private HttpServletRequest request;

    public PatternSearchResource(SecurityService securityService, RepositoryService repositoryService,
            RestAddon restAddon, HttpServletRequest request) {
        this.securityService = securityService;
        this.repositoryService = repositoryService;
        this.restAddon = restAddon;
        this.request = request;
    }

    @GET
    @Produces({SearchRestConstants.MT_PATTERN_SEARCH_RESULT, MediaType.APPLICATION_JSON})
    public PatternResultFileSet get(@QueryParam(SearchRestConstants.PARAM_PATTERN) String pattern) throws IOException {
        if (!securityService.isAuthenticated() || securityService.isAnonymous()) {
            throw new AuthorizationRestException();
        }
        try {
            return search(pattern);
        } catch (IllegalArgumentException iae) {
            throw new BadRequestException(iae.getMessage());
        } catch (MissingRestAddonException mrae) {
            throw mrae;
        } catch (TimeoutException te) {
            String secs = ConstantValues.searchPatternTimeoutSecs.getString();
            log.error("Artifacts pattern search timeout: artifactory.search.pattern.timeoutSecs={}.", secs);
            throw new RestException(HttpStatus.SC_REQUEST_TIMEOUT,
                    "Artifacts pattern search returned 0 results because it " +
                            "exceeded the configured timeout (" + secs +
                            " seconds). Please make sure your query is not too " +
                            "broad, causing the search to scan too many nodes, or ask your Artifactory administrator " +
                            "to " +
                            "change the default timeout."
            );
        } catch (Exception e) {
            String errorMessage =
                    String.format("Error occurred while searching for artifacts matching the pattern '%s': %s", pattern,
                            e.getMessage());
            log.error(errorMessage, e);
            throw new RestException(errorMessage);
        }
    }

    private PatternResultFileSet search(String pattern) throws ExecutionException, TimeoutException,
            InterruptedException {
        Set<String> matchingArtifacts = restAddon.searchArtifactsByPattern(pattern);

        String[] patternTokens = StringUtils.split(pattern, ":", 2);
        String requestedRepoKey = patternTokens[0];
        Repo repo = ((InternalRepositoryService) repositoryService).repositoryByKey(requestedRepoKey);
        if ((repo != null) && repo.isCache()) {
            requestedRepoKey = ((LocalCacheRepo) repo).getRemoteRepo().getKey();
        }

        String repoUri = new StringBuilder().append(RestUtils.getServletContextUrl(request)).append("/").
                append(requestedRepoKey).toString();

        PatternResultFileSet fileSet = new PatternResultFileSet(repoUri, pattern, matchingArtifacts);
        return fileSet;
    }
}
