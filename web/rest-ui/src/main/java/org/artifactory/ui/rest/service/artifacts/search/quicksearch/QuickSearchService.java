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

package org.artifactory.ui.rest.service.artifacts.search.quicksearch;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.SearchService;
import org.artifactory.api.search.artifact.ArtifactSearchControls;
import org.artifactory.api.search.artifact.ArtifactSearchResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.search.stats.SearchStatsThreadLocal;
import org.artifactory.ui.rest.model.artifacts.search.SearchResult;
import org.artifactory.ui.rest.model.artifacts.search.quicksearch.QuickSearch;
import org.artifactory.ui.rest.model.artifacts.search.quicksearch.QuickSearchResult;
import org.artifactory.ui.utils.RequestUtils;
import org.artifactory.ui.utils.SearchControlUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Chen Keinan
 */
@Component
public class QuickSearchService implements RestService {

    private final SearchService searchService;
    private final ProjectsService projectsService;
    private final RepositoryService repoService;

    @Autowired
    public QuickSearchService(SearchService searchService, ProjectsService projectsService,
            RepositoryService repoService) {
        this.searchService = searchService;
        this.projectsService = projectsService;
        this.repoService = repoService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        SearchStatsThreadLocal.executeWithLogging(() -> internalExecute(request, response));
    }

    private void internalExecute(ArtifactoryRestRequest request, RestResponse response) {
        QuickSearch quickSearch = (QuickSearch) request.getImodel();
        // get artifact search control instance
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        ArtifactSearchControls artifactSearchControl = getArtifactSearchControl(quickSearch, projectKey);
        // check if the search is empty or contains a wildcard only
        if (isSearchEmptyOrWildCardOnly(artifactSearchControl)) {
            response.error("Search term empty or containing only wildcards is not permitted");
            return;
        }
        // do quick search
        List<QuickSearchResult> quickSearchResults = Lists.newArrayList();
        ItemSearchResults<ArtifactSearchResult> artifactSearchResults =
                searchService.searchArtifacts(artifactSearchControl);
        for (ArtifactSearchResult artifactSearchResult : artifactSearchResults.getResults()) {
            quickSearchResults.add(new QuickSearchResult(artifactSearchResult, request));
        }
        long resultsCount;
        int maxResults = ConstantValues.searchMaxResults.getInt();
        if (artifactSearchControl.isLimitSearchResults() && quickSearchResults.size() > maxResults) {
            quickSearchResults = quickSearchResults.subList(0, maxResults);
            resultsCount = quickSearchResults.size() == 0 ? 0 : artifactSearchResults.getFullResultsCount();
        } else {
            resultsCount = quickSearchResults.size();
        }

        SearchStatsThreadLocal.setReturnedResults(quickSearchResults.size());

        SearchResult model = new SearchResult(quickSearchResults, quickSearch.getQuery(),
                resultsCount, artifactSearchControl.isLimitSearchResults(),
                artifactSearchControl.isLimitSearchResultsForProjects());
        model.addNotifications(response);
        response.iModel(model);
    }


    /**
     * check if search is empty or contain wildcard only
     *
     * @return if true - search is empty or containing wild card only
     */
    private boolean isSearchEmptyOrWildCardOnly(ArtifactSearchControls artifactSearchControl) {
        return artifactSearchControl.isEmpty() || artifactSearchControl.isWildcardsOnly();
    }

    /**
     * create artifact search control from quick search instance
     *
     * @param quickSearch - quick search instance
     * @return artifact search control instance
     */
    private ArtifactSearchControls getArtifactSearchControl(QuickSearch quickSearch, String projectKey) {
        ArtifactSearchControls artifactSearchControls = new ArtifactSearchControls();
        artifactSearchControls.setSelectedRepoForSearch(quickSearch.getSelectedRepositories());
        artifactSearchControls.setRelativePath(quickSearch.getRelativePath());
        artifactSearchControls.setQuery(quickSearch.getQuery());
        artifactSearchControls.setLimitSearchResults(true);
        SearchControlUtils.narrowSearchToProjectRepos(artifactSearchControls, projectKey, projectsService, repoService);
        return artifactSearchControls;
    }

    /**
     * add wild card to query
     *
     * @param artifactSearchControls - quick search model
     */
    private void setQueryWildCard(ArtifactSearchControls artifactSearchControls) {
        String query = artifactSearchControls.getQuery();
        if (StringUtils.isNotBlank(query)) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append(query);

            if (!query.endsWith("*") && !query.endsWith("?")) {
                queryBuilder.append("*");
            }
            artifactSearchControls.setQuery(queryBuilder.toString());
        }
    }
}
