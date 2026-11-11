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

package org.artifactory.repo.interceptor.config;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.repo.system.suppliers.EdgeRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.system.SystemRepos.EDGE_UPLOADS_REPO_KEY;

/**
 * @author Fred Simon
 */
@Component
public class EdgeUploadReposConfigInterceptor implements RepoConfigChangeInterceptor {
    private final AddonsManager addonsManager;
    private final CentralConfigService centralConfigService;
    private RepositoryService repositoryService;

    @Autowired
    public EdgeUploadReposConfigInterceptor(AddonsManager addonsManager,
            CentralConfigService centralConfigService) {
        this.addonsManager = addonsManager;
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        onBeforeSave(new RepoConfigChanges(mutableRepoConfigChanges));
    }

    @Override
    public void onBeforeSave(RepoConfigChanges repoConfigChanges) {
        if (!isEdge()) {
            return;
        }
        if (shouldCreateEdgeUploadsRepo()) {
            addChangeToCreateEdgeUploadsRepo(repoConfigChanges);
        }
        if (repoConfigChanges.isImport()) {
            return;
        }
        if (isAttemptingToDeleteEdgeUploadsRepo(repoConfigChanges)) {
            throw new ConfigurationException("Cannot delete the edge uploads repository", SC_FORBIDDEN);
        }
    }

    private boolean shouldCreateEdgeUploadsRepo() {
        return !repositoryService.existsByRepoKey(EDGE_UPLOADS_REPO_KEY);
    }

    private boolean isEdge() {
        return addonsManager.isEntitled(EntitlementType.EDGE);
    }

    private void addChangeToCreateEdgeUploadsRepo(RepoConfigChanges repoConfigChanges) {
        boolean aboutToBeCreated = isEdgeUploadsRepoCreationInChanges(repoConfigChanges);
        if (aboutToBeCreated) {
            return;
        }
        CentralConfigDescriptor centralConfigDescriptor = centralConfigService.getDescriptor();
        LocalRepoConfig edgeUploadsRepoConfig =
                new EdgeRepo().createSystemRepoConfig(centralConfigDescriptor, EDGE_UPLOADS_REPO_KEY);
        RepoConfigChange addEdgeUploadsRepoChange =
                new RepoConfigChange(EDGE_UPLOADS_REPO_KEY, REPO_CREATE, edgeUploadsRepoConfig, null);
        repoConfigChanges.add(addEdgeUploadsRepoChange);
    }

    private boolean isAttemptingToDeleteEdgeUploadsRepo(RepoConfigChanges repoConfigChanges) {
        return repoConfigChanges.filterBy(LOCAL).stream()
                .filter(repoConfigChange -> REPO_DELETE == repoConfigChange.getConfigAction())
                .anyMatch(repoConfigChange -> EDGE_UPLOADS_REPO_KEY.equals(repoConfigChange.getRepoKey()));
    }

    private static boolean isEdgeUploadsRepoCreationInChanges(
            RepoConfigChanges repoConfigChanges) {
        return repoConfigChanges.filterBy(REPO_CREATE, LOCAL).stream()
                .anyMatch(repoConfigChange -> EDGE_UPLOADS_REPO_KEY.equals(repoConfigChange.getRepoKey()));
    }

    @Override
    public void onAfterSave(RepoConfigChanges repoConfigChanges) {
        if (isEdgeUploadsRepoCreationInChanges(repoConfigChanges)) {
            addEdgeUploadsRepoToBackups();
        }
    }

    private void addEdgeUploadsRepoToBackups() {
        LocalRepoDescriptor edgeUploadsRepoDescriptor = new LocalRepoDescriptor();
        edgeUploadsRepoDescriptor.setKey(EDGE_UPLOADS_REPO_KEY);
        MutableCentralConfigDescriptor mutableDescriptor = centralConfigService.getMutableDescriptor();
        mutableDescriptor.conditionallyAddToBackups(edgeUploadsRepoDescriptor.getKey());
        centralConfigService.saveEditedDescriptorAndReload(mutableDescriptor, edgeUploadsRepoDescriptor);
    }
}