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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryBrowsingService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.SearchRestConstants;
import org.artifactory.api.rest.search.result.InfoRestSearchResult;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.SearchService;
import org.artifactory.api.search.artifact.ArtifactSearchControls;
import org.artifactory.api.search.artifact.ArtifactSearchResult;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.fs.ItemInfo;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.rest.util.StorageInfoHelper;
import org.artifactory.sapi.common.RepositoryRuntimeException;
import org.jfrog.common.StringList;

import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;

/**
 * Resource class that handles artifact search actions
 *
 * @author Eli givoni
 */
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
public class ArtifactSearchResource {

    private SecurityService securityService;
    private SearchService searchService;
    private RepositoryService repositoryService;
    private PropertiesService propertiesService;
    private RepositoryBrowsingService repoBrowsingService;
    private HttpServletRequest request;

    /**
     * Main constructor
     *
     * @param searchService Search service instance
     */
    public ArtifactSearchResource(SecurityService securityService, SearchService searchService,
            RepositoryService repositoryService, PropertiesService propertiesService,
            HttpServletRequest request, RepositoryBrowsingService repoBrowsingService) {
        this.securityService = securityService;
        this.searchService = searchService;
        this.repositoryService = repositoryService;
        this.propertiesService = propertiesService;
        this.repoBrowsingService = repoBrowsingService;
        this.request = request;
    }

    /**
     * Parametrized artifact search
     *
     * @param name          to search for
     * @param reposToSearch Specific repositories to search in
     * @return Rest search results object
     */
    @GET
    @Produces({SearchRestConstants.MT_ARTIFACT_SEARCH_RESULT, MediaType.APPLICATION_JSON})
    public Response get(
            @QueryParam(SearchRestConstants.PARAM_SEARCH_NAME) String name,
            @QueryParam(SearchRestConstants.PARAM_REPO_TO_SEARCH) StringList reposToSearch) throws IOException {
        return search(name, reposToSearch);
    }


    /**
     * Performs the artifact search
     *
     * @param name          Artifact ID to search for
     * @param reposToSearch Specific repositories to search in
     * @return Rest search results object
     */
    private Response search(String name, List<String> reposToSearch) {
        ArtifactSearchControls controls = new ArtifactSearchControls();
        controls.setQuery(appendAndReturnWildcards(name));
        controls.setLimitSearchResultsByPolicy(securityService);
        controls.setSelectedRepoForSearch(reposToSearch);

        if (controls.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("The search term cannot be empty").build();
        }
        if (controls.isWildcardsOnly()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(
                    "Search term containing only wildcards is not permitted").build();
        }

        ItemSearchResults<ArtifactSearchResult> searchResults;
        try {
            searchResults = searchService.searchArtifacts(controls);
        } catch (RepositoryRuntimeException e) {
            throw new NotFoundException(e.getMessage());
        }
        InfoRestSearchResult result = new InfoRestSearchResult();
        for (ArtifactSearchResult searchResult : searchResults.getResults()) {
            ItemInfo itemInfo = searchResult.getItemInfo();
            StorageInfoHelper storageInfoHelper = new StorageInfoHelper(request, repositoryService, propertiesService,
                    repoBrowsingService, itemInfo, null, false);
            result.results.add(storageInfoHelper.createStorageInfo());
        }

        return Response.ok(result).build();
    }

    private String appendAndReturnWildcards(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        StringBuilder queryBuilder = new StringBuilder();
        if (!name.startsWith("*") && !name.startsWith("?")) {
            queryBuilder.append("*");
        }

        queryBuilder.append(name);

        if (!name.endsWith("*") && !name.endsWith("?")) {
            queryBuilder.append("*");
        }

        return queryBuilder.toString();
    }
}