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

package org.artifactory.ui.utils;

import jakarta.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.search.SearchControlsBase;
import org.artifactory.api.search.artifact.ChecksumSearchControls;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchModel;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUiSearchControls;
import org.artifactory.util.RepoPathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Dan Feldman
 */
public class SearchControlUtils {

    private static final int MAX_SEARCH_REPOS_FOR_PROJECT = ConstantValues.searchInProjectMaxReposToProcess.getInt();
    private static final String REPO_AQL_SEARCH_ID = "repo";

    private SearchControlUtils() {
    }

    /**
     * If there is a projectKey provided, modify the search model to apply project-specific search rules.
     * Those are the following:<br/>
     * 1) The search should be applied only to repos related to the specified project.<br/>
     * 2) The search should be applied only to first N repos under the specified project. N is defined as a constant
     * at the application level.
     * 3) If a user selected some specific repos, the search will be narrowed to only those of them which belong
     * to the specified project.
     * 4) If there are no repositories related to the specified project, or all the repositories selected by a user
     * are not under the specified project, then an empty result should be returned by the search.
     */
    public static void narrowSearchToProjectRepos(SearchControlsBase controls, String projectKey,
            ProjectsService projectsService, RepositoryService repoService) {
        if (StringUtils.isNotBlank(projectKey) && !ProjectsUtils.isDefaultProject(projectKey)) {
            Set<String> projectRepos = projectsService.getProjectRepositoriesKeys(projectKey, false);
            List<String> userSelectedRepos = controls.getSelectedRepoForSearch();
            List<String> reposForSearch =
                    SearchControlUtils.limitSearchToProjectRepos(userSelectedRepos, projectRepos, repoService);
            controls.setSelectedRepoForSearch(reposForSearch);
            controls.setSearchShouldBeSkipped(reposForSearch.isEmpty());
            controls.setLimitSearchResultsForProjects(
                    searchWasAffectedByProjects(reposForSearch, userSelectedRepos, projectRepos));
        }
    }

    /**
     * If there is a projectKey provided, modify the search model to apply project-specific search rules.
     * Those are the following:<br/>
     * 1) The search should be applied only to repos related to the specified project.<br/>
     * 2) The search should be applied only to first N repos under the specified project. N is defined as a constant
     * at the application level.
     * 3) If a user selected some specific repos, the search will be narrowed to only those of them which belong
     * to the specified project.
     * 4) If there are no repositories related to the specified project, or all the repositories selected by a user
     * are not under the specified project, then an empty result should be returned by the search.
     */
    @Nonnull
    public static AqlUiSearchControls narrowAqlSearchToProjectRepos(List<AqlUISearchModel> searchModels,
            String projectKey, ProjectsService projectsService, RepositoryService repoService) {
        List<AqlUISearchModel> resultList =
                CollectionUtils.isNotEmpty(searchModels) ? searchModels : new ArrayList<>(1);
        boolean searchShouldBeSkipped = false;
        boolean searchLimitedByProjects = false;
        if (StringUtils.isNotBlank(projectKey) && !ProjectsUtils.isDefaultProject(projectKey)) {
            Optional<AqlUISearchModel> selectedReposModel =
                    resultList.stream().filter(item -> REPO_AQL_SEARCH_ID.equals(item.getId())).findFirst();
            List<String> selectedRepos = selectedReposModel.map(AqlUISearchModel::getValues).orElse(null);
            Set<String> projectRepos = projectsService.getProjectRepositoriesKeys(projectKey, false);
            List<String> repoKeysForSearch = SearchControlUtils.limitSearchToProjectRepos(selectedRepos, projectRepos,
                    repoService);
            // Remove the existing search options for repos if there were any.
            selectedReposModel.ifPresent(resultList::remove);
            searchShouldBeSkipped = repoKeysForSearch.isEmpty();
            searchLimitedByProjects = searchWasAffectedByProjects(repoKeysForSearch, selectedRepos, projectRepos);
            resultList.add(new AqlUISearchModel(REPO_AQL_SEARCH_ID, AqlComparatorEnum.equals, repoKeysForSearch));
        }
        return new AqlUiSearchControls(resultList, searchShouldBeSkipped, searchLimitedByProjects);
    }

    /**
     * Checks if one of the following is true:<br/>
     * 1) A search should be skipped because after applying the projects limitations there are no repositories left
     * to search in.<br/>
     * 2) User did not select any specific repositories but the number of repos for the specified project is higher
     * than the threshold.<br/>
     * 3) Some of the specific repositories selected by user were filtered out because they do not belong to the
     * specified project.
     */
    private static boolean searchWasAffectedByProjects(List<String> reposForSearch, List<String> userSelectedRepos,
            Set<String> projectRepos) {
        return reposForSearch.isEmpty() ||
                (projectRepos.size() > MAX_SEARCH_REPOS_FOR_PROJECT && CollectionUtils.isEmpty(userSelectedRepos)) ||
                (CollectionUtils.isNotEmpty(userSelectedRepos) &&
                        !ListUtils.isEqualList(reposForSearch, userSelectedRepos));
    }

    private static List<String> limitSearchToProjectRepos(List<String> userSelectedRepos, Set<String> projectRepos,
            RepositoryService repoService) {
        List<String> result = Collections.emptyList();
        if (projectRepos != null) {
            if (CollectionUtils.isNotEmpty(userSelectedRepos)) {
                // Limit selected repos to only those that belong to the selected project. And limit the length to a
                // limit defined at the application level.
                result = userSelectedRepos.stream()
                        .filter(selectedRepo -> projectRepos.contains(RepoPathUtils.removeDashCache(selectedRepo)))
                        .limit(MAX_SEARCH_REPOS_FOR_PROJECT).collect(Collectors.toList());
            } else {
                // Take repo keys from the project repositories.
                result = new ArrayList<>();
                int count = 0;
                for (String repoKey : projectRepos) {
                    if (count < MAX_SEARCH_REPOS_FOR_PROJECT) {
                        LocalRepoDescriptor localDescriptor =
                                repoService.localFederatedOrCachedRepoDescriptorByKey(repoKey);
                        if (localDescriptor != null) {
                            result.add(localDescriptor.getKey());
                            count++;
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void addChecksumCriteria(String query, ChecksumSearchControls searchControls) {
        if (StringUtils.length(query) == ChecksumType.md5.length()) {
            searchControls.addChecksum(ChecksumType.md5, query);
            searchControls.setLimitSearchResults(true);
        } else if (StringUtils.length(query) == ChecksumType.sha1.length()) {
            searchControls.addChecksum(ChecksumType.sha1, query);
        } else if (StringUtils.length(query) == ChecksumType.sha256.length()) {
            searchControls.addChecksum(ChecksumType.sha256, query);
        }
    }

}
