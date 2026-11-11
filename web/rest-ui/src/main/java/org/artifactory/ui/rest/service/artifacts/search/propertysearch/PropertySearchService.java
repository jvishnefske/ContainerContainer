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

package org.artifactory.ui.rest.service.artifacts.search.propertysearch;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.SearchService;
import org.artifactory.api.search.property.PropertySearchControls;
import org.artifactory.api.search.property.PropertySearchResult;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.search.stats.SearchStatsThreadLocal;
import org.artifactory.ui.rest.model.artifacts.search.SearchResult;
import org.artifactory.ui.rest.model.artifacts.search.propertysearch.PropertyKeyValues;
import org.artifactory.ui.rest.model.artifacts.search.propertysearch.PropertyResult;
import org.artifactory.ui.rest.model.artifacts.search.propertysearch.PropertySearch;
import org.artifactory.ui.utils.RequestUtils;
import org.artifactory.ui.utils.SearchControlUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chen Keinan
 */

@Component
public class PropertySearchService implements RestService {

    private final SearchService searchService;
    private final AuthorizationService authorizationService;
    private final ProjectsService projectsService;
    private final RepositoryService repoService;

    @Autowired
    public PropertySearchService(SearchService searchService, AuthorizationService authorizationService,
            ProjectsService projectsService, RepositoryService repoService) {
        this.searchService = searchService;
        this.authorizationService = authorizationService;
        this.projectsService = projectsService;
        this.repoService = repoService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        SearchStatsThreadLocal.executeWithLogging(() -> internalExecute(request, response));
    }

    private void internalExecute(ArtifactoryRestRequest request, RestResponse response) {
        PropertySearch propertySearch = (PropertySearch) request.getImodel();
        // update property control search
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        PropertySearchControls propertySearchControls = updatePropertyControlSearch(propertySearch, projectKey);

        if (isSearchEmptyOrWildCardOnly(propertySearchControls)) {
            response.error("Search term empty or containing only wildcards is not permitted");
            return;
        }
        // search property
        ItemSearchResults<PropertySearchResult> propertyResults =
                searchService.searchPropertyAql(propertySearchControls);
        // update response data
        List<PropertyResult> results = new ArrayList<>();
        for (PropertySearchResult propsResult : propertyResults.getResults()) {
            if (filterNoReadResults(propsResult)) { results.add(new PropertyResult(propsResult, request)); }
        }
        int maxResults = ConstantValues.searchMaxResults.getInt();
        long resultsCount;
        if (propertySearchControls.isLimitSearchResults() && results.size() > maxResults) {
            results = results.subList(0, maxResults);
            resultsCount = results.size() == 0 ? 0 : propertyResults.getFullResultsCount();
        } else {
            resultsCount = results.size();
        }

        SearchStatsThreadLocal.setReturnedResults(results.size());

        SearchResult model = new SearchResult(results, propertySearchControls.getValue(),
                resultsCount, propertySearchControls.isLimitSearchResults(),
                propertySearchControls.isLimitSearchResultsForProjects());
        model.addNotifications(response);
        response.iModel(model);
    }

    private boolean filterNoReadResults(PropertySearchResult propertyResult) {
        RepoPath repoPath = RepoPathFactory.create(propertyResult.getRepoKey(), propertyResult.getRelativePath());
        return authorizationService.canRead(repoPath);
    }

    /**
     * check if search is empty or contain wildcard only
     *
     * @param propertySearchControls - property search control
     * @return if true - search is empty or containing wild card only
     */
    private boolean isSearchEmptyOrWildCardOnly(PropertySearchControls propertySearchControls) {
        return propertySearchControls.isEmpty() || propertySearchControls.isWildcardsOnly();
    }

    /**
     * update property control search
     *
     * @param propertySearch imodel list
     * @return property control search
     */
    private PropertySearchControls updatePropertyControlSearch(PropertySearch propertySearch, String projectKey) {
        PropertySearchControls propertySearchControls = new PropertySearchControls();
        propertySearchControls.setSelectedRepoForSearch(propertySearch.getSelectedRepositories());
        propertySearchControls.setLimitSearchResults(true);
        List<PropertyKeyValues> propertyKeyValues = propertySearch.getPropertyKeyValues();
        propertyKeyValues.forEach(props -> {
                    List<String> values = props.getValues();
                    values.forEach(value -> propertySearchControls.put(props.getKey(), value, true));
                }
        );
        SearchControlUtils.narrowSearchToProjectRepos(propertySearchControls, projectKey, projectsService, repoService);
        return propertySearchControls;
    }
}
