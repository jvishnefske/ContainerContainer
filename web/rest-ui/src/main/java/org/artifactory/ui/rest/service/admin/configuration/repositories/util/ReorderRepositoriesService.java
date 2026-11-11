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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.RepoConfig;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoDetailsType.REMOTE_REPO;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL_REPO;

/**
 * Reorders the list of repositories based on a list sent by the UI
 *
 * @author Dan Feldman
 */
@Component
public class ReorderRepositoriesService implements RestService<List<String>> {
    private static final Logger log = LoggerFactory.getLogger(ReorderRepositoriesService.class);

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RepositoryConfigService repositoryConfigService;

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoType = request.getPathParamByKey("repoType");
        log.debug("Processing reorder repos request for type {}", repoType);
        if (StringUtils.isBlank(repoType)) {
            response.error("The type of repositories to reorder must be specified").responseCode(SC_BAD_REQUEST);
            return;
        }
        List<String> newOrderRepoKeyList = request.getModels();
        if (CollectionUtils.isEmpty(newOrderRepoKeyList)) {
            response.error("No list to reorder by was sent.").responseCode(SC_BAD_REQUEST);
            return;
        }
        List<? extends RepoConfig> currentRepoConfigs = null;
        final LinkedList<? extends RepoConfig> orderedRepoConfigs;

        switch (repoType) {
            case LOCAL_REPO -> currentRepoConfigs = repositoryService.getLocalRepoConfigsIncludingInfoRepos();
            case FEDERATED_REPO -> currentRepoConfigs = repositoryService.getFederatedRepoConfigs();
            case REMOTE_REPO -> currentRepoConfigs = repositoryService.getRemoteRepoConfigs();
            case VIRTUAL_REPO -> currentRepoConfigs = repositoryService.getVirtualRepoConfigs();
            case RELEASE_BUNDLE_REPO -> currentRepoConfigs = repositoryService.getReleaseBundleRepoConfigs();
            default -> response.error("Invalid repository type given: " + repoType).responseCode(SC_BAD_REQUEST);
        }

        if (!CollectionUtils.isEmpty(currentRepoConfigs)) {
            orderedRepoConfigs =
                    getNewOrderRepoList(response, repoType, newOrderRepoKeyList, currentRepoConfigs);
            if (!CollectionUtils.isEmpty(orderedRepoConfigs)) {
                repositoryConfigService.bulkUpsertRepoConfigs(orderedRepoConfigs, true);
            }
        }
    }

    private LinkedList<? extends RepoConfig> getNewOrderRepoList(RestResponse<? extends Object> response,
            String repoType, List<String> newOrderRepoKeyList, List<? extends RepoConfig> currentRepoConfigs) {
        LinkedList<RepoConfig> newRepoList = Lists.newLinkedList();
        if (isLegalReorder(response, repoType, newOrderRepoKeyList, currentRepoConfigs)) {
            for (String repoKey : newOrderRepoKeyList) {
                Optional<? extends RepoConfig> optionalRepoConfig = currentRepoConfigs.stream()
                        .filter(repoConfig -> repoConfig.getKey().equals(repoKey)).findAny();
                optionalRepoConfig.ifPresent(newRepoList::add);
            }
            log.info("Reordering {} repositories list.", repoType);
        }
        return newRepoList;
    }

    private boolean isLegalReorder(RestResponse response, String repoType, List<String> newOrderRepoKeyList,
            List<? extends RepoConfig> currentRepoConfigs) {
        Set<String> oldOrderRepoKeys = currentRepoConfigs.stream()
                        .map(repoConfig -> repoConfig.getKey())
                        .collect(Collectors.toSet());
        if (oldOrderRepoKeys.size() != newOrderRepoKeyList.size()) {
            log.debug("Current {} repo map size: {}", repoType, oldOrderRepoKeys.size());
            log.debug("New Order list size : {}", newOrderRepoKeyList.size());
            response.error("The size of the list to order by does not match the size of the current repo list, " +
                    "aborting.").responseCode(SC_BAD_REQUEST);
            return false;
        } else if (!newOrderRepoKeyList.containsAll(oldOrderRepoKeys)) {
            response.error("The new order list is missing \\ has excess repositories that are currently saved." +
                    repoType).responseCode(SC_BAD_REQUEST);
            return false;
        }
        return true;
    }
}