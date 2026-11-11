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

package org.artifactory.rest.resource.license;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.license.LicenseStatus;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.SearchRestConstants;
import org.artifactory.api.rest.search.result.LicensesSearchResult;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.util.RestUtils;
import org.artifactory.util.CollectionUtils;
import org.jfrog.common.StringList;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A resource for license retrieval
 *
 * @author Tomer Cohen
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
public class LicenseResource {

    private final RestAddon restAddon;
    private final HttpServletRequest request;
    private final RepositoryService repositoryService;

    public LicenseResource(RestAddon restAddon, HttpServletRequest request, RepositoryService repositoryService) {
        this.restAddon = restAddon;
        this.request = request;
        this.repositoryService = repositoryService;
    }

    /**
     * Find licenses in the defined repositories.
     *
     * @param unapprovedString Flag whether unapproved licenses should be included in the results. The default is 1
     * @param unknownString    Flag whether unknown licenses should be included in the results. The default is 1
     * @param notFoundString   Flag whether not found licenses
     * {@link org.artifactory.api.license.LicenseInfo#NOT_FOUND_STR}
     *                         should be included the results. The default is 0
     * @param neutralString    Flag whether neutral licenses should be included in the results. The default is 0
     * @param approvedString   Flag whether approved licenses should be included in the results. The default is 0
     * @param autofindString   Flag whether an auto-find should be performed on artifacts, and reading their respective
     *                         poms or ivys. <b>NOTE: This can affect the speed of the search quite dramatically.</b>
     * @param repos            A comma separated list of repositories to perform the search in.
     * @return Results {@link LicensesSearchResult}
     * @see LicensesSearchResult
     */
    @GET
    @Produces({SearchRestConstants.MT_LICENSE_SEARCH_RESULT, MediaType.APPLICATION_JSON})
    public Response findLicensesInRepos(
            @QueryParam(SearchRestConstants.UNAPPROVED_PARAM) String unapprovedString,
            @QueryParam(SearchRestConstants.UNKNOWN_PARAM) String unknownString,
            @QueryParam(SearchRestConstants.NOT_FOUND_PARAM) String notFoundString,
            @QueryParam(SearchRestConstants.NEUTRAL_PARAM) String neutralString,
            @QueryParam(SearchRestConstants.APPROVED_PARAM) String approvedString,
            @QueryParam(SearchRestConstants.AUTOFIND_PARAM) String autofindString,
            @QueryParam(SearchRestConstants.REPOS_PARAM) StringList repos) throws IOException {

        Set<String> repoKeys = (CollectionUtils.isNullOrEmpty(repos) ? getAllRealRepoKeys() : Sets.newHashSet(repos));
        boolean unapproved = true;
        if (StringUtils.isNotBlank(unapprovedString)) {
            unapproved = Integer.parseInt(unapprovedString) == 1;
        }
        boolean unknown = true;
        if (StringUtils.isNotBlank(unknownString)) {
            unknown = Integer.parseInt(unknownString) == 1;
        }
        boolean notFound = false;
        if (StringUtils.isNotBlank(notFoundString)) {
            notFound = Integer.parseInt(notFoundString) == 1;
        }
        boolean neutral = false;
        if (StringUtils.isNotBlank(neutralString)) {
            neutral = Integer.parseInt(neutralString) == 1;
        }
        boolean approved = false;
        if (StringUtils.isNotBlank(approvedString)) {
            approved = Integer.parseInt(approvedString) == 1;
        }
        boolean autoDiscover = false;
        if (StringUtils.isNotBlank(autofindString)) {
            autoDiscover = Integer.parseInt(autofindString) == 1;
        }

        LicenseStatus status = new LicenseStatus(approved, autoDiscover, neutral, notFound, unapproved, unknown);
        String servletContextUrl = RestUtils.getServletContextUrl(request);
        LicensesSearchResult licensesInRepos = restAddon.findLicensesInRepos(status, repoKeys, servletContextUrl);
        return Response.status(Response.Status.OK).entity(licensesInRepos).build();
    }

    private Set<String> getAllRealRepoKeys() {
        return repositoryService.getLocalFederatedAndCachedRepoConfigs().stream().map(RepoConfig::getKey)
                .collect(Collectors.toSet());
    }
}
