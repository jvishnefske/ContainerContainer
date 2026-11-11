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

package org.artifactory.ui.rest.service.artifacts.search.packagesearch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.artifactory.aql.util.AqlUtils;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.search.SearchResult;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria.PackageSearchType;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.result.PackageSearchResult;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchModel;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUiSearchControls;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.result.PackageSearchResultMerger;
import org.artifactory.ui.utils.RequestUtils;
import org.artifactory.ui.utils.SearchControlUtils;
import org.artifactory.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.InternalServerErrorException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.aqlQueryTimeoutForUi;
import static org.artifactory.ui.rest.service.artifacts.search.packagesearch.util.PackageSearchHelper.buildItemQuery;

/**
 * Search service that provides an abstraction over aql for UI searches.
 * Using {@link PackageSearchCriteria} you can specify any combination of field / property criterion from any aql domain
 * and link it to proper search and result models for the UI to consume
 *
 * @author Dan Feldman
 */
@Component
public class PackageSearchService implements RestService {
    private static final Logger log = LoggerFactory.getLogger(PackageSearchService.class);
    private static final String ERROR_EXECUTING_SEARCH_AQL_QUERY = "Error executing search AQL query ";

    private final AqlService aqlService;
    private final ProjectsService projectsService;
    private final RepositoryService repositoryService;

    @Autowired
    public PackageSearchService(AqlService aqlService, ProjectsService projectsService,
            RepositoryService repositoryService) {
        this.aqlService = aqlService;
        this.projectsService = projectsService;
        this.repositoryService = repositoryService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        AqlUiSearchControls searchControls = SearchControlUtils
                .narrowAqlSearchToProjectRepos((List<AqlUISearchModel>) request.getModels(), projectKey,
                        projectsService, repositoryService);
        if (CollectionUtils.isNullOrEmpty(searchControls.getSearchModels())) {
            log.debug("Got empty search criteria for Package Search.");
            response.error("Search criteria cannot be empty.");
            response.responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        SearchResult model = search(searchControls);
        setDownloadLinksOnModel((Collection<PackageSearchResult>) model.getResults(), request);
        model.addNotifications(response);
        response.iModel(model);
    }

    public SearchResult search(AqlUiSearchControls searchControls) {
        //Build the query here to return native in response
        AqlBase query = buildItemQuery(searchControls.getSearchModels(), true);
        //Property result filter will cause no results to return in case some of the props are missing, therefore we
        //rerun the search
        //without the filter to get all the props.
        String nativeQuery = query.toNative(0);
        log.debug("strategies resolved to query: {}", nativeQuery);
        Set<PackageSearchResult> results;
        if (!searchControls.isSearchShouldBeSkipped()) {
            results = executeSearch(query);
        } else {
            results = Collections.emptySet();
        }
        // update response data
        return new SearchResult(results, nativeQuery, results.size(), true,
                searchControls.isLimitSearchResultsForProjects());
    }

    @VisibleForTesting
    Set<PackageSearchResult> executeSearch(AqlBase<? extends AqlBase, AqlBaseFullRowImpl> query) {
        long timer = System.currentTimeMillis();
        try (AqlLazyResult<AqlBaseFullRowImpl> aqlLazyResult =
                     aqlService.executeQueryLazy(query, aqlQueryTimeoutForUi.getLong())
        ) {
            List<AqlBaseFullRowImpl> queryResults = aqlLazyResult.asStream(e -> {
                if (e != null) {
                    log.error(ERROR_EXECUTING_SEARCH_AQL_QUERY + " {}", e.getMessage(), e);
                }
            }).toList();
            HashMultimap<RepoPath, AqlBaseFullRowImpl> resultsByPath = AqlUtils.aggregateResultsByPath(queryResults);
            Set<PackageSearchResult> results = reduceAggregatedResultRows(resultsByPath);
            manipulateResultFields(results);
            results = reduceAggregatedResults(results);
            log.trace("Search found {} results in {} milliseconds", results.size(), System.currentTimeMillis() - timer);
            return results;
        } catch (AqlException e) {
            throw e;
        } catch (Exception e) {
            log.error(ERROR_EXECUTING_SEARCH_AQL_QUERY + " {}", e.getMessage(), e);
            throw new InternalServerErrorException(ERROR_EXECUTING_SEARCH_AQL_QUERY);
        }
    }

    /**
     * Reduce and merge the results by a package type specific merge strategy.
     *
     * @param results the results to merge
     * @return the merged results
     */
    private Set<PackageSearchResult> reduceAggregatedResults(Set<PackageSearchResult> results) {
        //Collect results by merge key
        int maxMergeSetSize = 0;
        boolean operateOnSingleEntry = false;
        HashMultimap<String, PackageSearchResult> aggregatedResults = HashMultimap.create();
        for (PackageSearchResult result : results) {
            PackageSearchResultMerger merger = result.getPackageType().getResultMerger();
            String key = merger.getMergeKey(result);
            aggregatedResults.put(key, result);
            maxMergeSetSize = Integer.max(maxMergeSetSize, aggregatedResults.get(key).size());
            operateOnSingleEntry |= merger.isOperateOnSingleEntry();
        }
        //Merge aggregated results only if there is a need
        if (maxMergeSetSize > 1 || operateOnSingleEntry) {
            log.trace("Merging package search results");
            Set<PackageSearchResult> concurrentSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
            aggregatedResults.keySet().parallelStream().forEach(key -> {
                Set<PackageSearchResult> packageSearchResults = aggregatedResults.get(key);
                PackageSearchType packageType = packageSearchResults.iterator().next().getPackageType();
                PackageSearchResultMerger merger = packageType.getResultMerger();
                PackageSearchResult merged = merger.merge(packageSearchResults);
                concurrentSet.add(merged);
            });
            return concurrentSet;
        }
        log.trace("No need to merge package search results");
        return results;
    }

    /**
     * Reduces all rows of a single path into one result that represents a single artifact and all extra fields that
     * were requested with it and are a part of its criteria domain.
     */
    private Set<PackageSearchResult> reduceAggregatedResultRows(
            HashMultimap<RepoPath, AqlBaseFullRowImpl> resultsByPath) {

        Set<PackageSearchResult> concurrentSet = Collections.newSetFromMap(new ConcurrentHashMap<>());

        resultsByPath.keySet().parallelStream()
                .forEach(path -> concurrentSet.add(
                        resultsByPath.get(path).stream()
                                .reduce(new PackageSearchResult(resultsByPath.get(path).iterator().next()),
                                        PackageSearchResult::aggregateRow,
                                        PackageSearchResult::merge)
                ));

        return concurrentSet.stream()
                .filter(packageSearchResult -> packageSearchResult.getPackageType() != null)
                .collect(Collectors.toSet());
    }

    private void setDownloadLinksOnModel(Collection<PackageSearchResult> results, ArtifactoryRestRequest request) {
        results.forEach(result -> result.setDownloadLinkAndActions(request));
    }

    /**
     * Calls all AqlUISearchResultManipulators associated with each result's PackageType to manipulate
     * any relevant fields that should be changed before returning to the UI.
     */
    private void manipulateResultFields(Set<PackageSearchResult> results) {
        results.forEach(result -> PackageSearchCriteria.getResultManipulatorsByPackage(result.getPackageType())
                .forEach(manipulator -> manipulator.manipulate(result))
        );
    }
}
