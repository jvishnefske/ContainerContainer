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

package org.artifactory.ui.rest.service.admin.configuration.repositories;

import com.jfrog.build.acl.bridge.BuildFacade;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.model.LocalConfig;
import org.artifactory.pipelines.InternalPipelinesService;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.util.AolUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.repo.RepoDetailsType.LOCAL;

/**
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
@Slf4j
@Component
public class DeleteRepositoryConfigService<T extends RepositoryConfigModel> implements RestService<T> {

    private final RepositoryService repositoryService;
    private final RepositoryConfigService repositoryConfigService;
    private final BuildFacade buildFacade;
    private final InternalPipelinesService pipelinesService;
    private final SecurityService securityService;

    @Autowired
    public DeleteRepositoryConfigService(
            RepositoryService repositoryService,
            RepositoryConfigService repositoryConfigService,
            AddonsManager addonsManager,
            BuildFacade buildFacade,
            InternalPipelinesService pipelinesService,
            SecurityService securityService) {
        this.repositoryService = repositoryService;
        this.repositoryConfigService = repositoryConfigService;
        this.buildFacade = buildFacade;
        this.pipelinesService = pipelinesService;
        this.securityService = securityService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String repoKey = request.getPathParamByKey("repoKey");
        if (isRemoveRepositoryFlowV2Enabled()) {
            removeRepositoryFlowV2(response, repoKey);
            return;
        }
        deprecatedRemoveRepositoryFlow(response, repoKey);
    }

    private boolean isRemoveRepositoryFlowV2Enabled() {
        return ConstantValues.removeRepositoryFlowV2Enabled.getBoolean();
    }

    private void removeRepositoryFlowV2(RestResponse<ArtifactoryRestRequest<String>> response, String repoKey) {
        try {
            log.info("Deleting repository {}", repoKey);
            BasicStatusHolder statusHolder = repositoryService.removeRepository(repoKey);
            if (statusHolder.hasErrorsOrWarnings()) {
                response.error(statusHolder.getStatusMsg()).responseCode(SC_FORBIDDEN);
            } else {
                response.info(statusHolder.getStatusMsg()).responseCode(statusHolder.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Deleting repo '{}' failed: {}", repoKey, e.getMessage(), e);
            response.error("Deleting repo '%s' failed: %s".formatted(repoKey, e.getMessage()));
        }
    }

    /**
     * @deprecated for removal soon
     */
    @Deprecated(since = "7.104.x")
    private void deprecatedRemoveRepositoryFlow(RestResponse<ArtifactoryRestRequest<String>> response, String repoKey) {
        if (isSystemRepo(repoKey)) {
            response.error("Deleting a system repo '" + repoKey + "' is not allowed.").responseCode(SC_FORBIDDEN);
            return;
        }
        if (!repositoryConfigService.existsByRepoKey(repoKey)) {
            response.error("Repository '" + repoKey + "' does not exist").responseCode(SC_NOT_FOUND);
            return;
        }
        if (!AolUtils.isAol()) {
            LocalConfig localConfig = repositoryConfigService.getLocalRepoConfigByKeyForDelete(repoKey);
            if (localConfig != null && onlyOneNonSystemLocalRepositoryLeft()) {
                response.error("Deleting the last local repository is not allowed").responseCode(SC_FORBIDDEN);
                return;
            }
        }
        if (!securityService.isProjectAdmin(RepoPathFactory.create(repoKey, ""))) {
            response.error("Not enough permissions to delete content of repository '" + repoKey +
                    "' (by user: '" + securityService.currentUsername() +
                    "'). This requires admin privileges.").responseCode(SC_FORBIDDEN);
            return;
        }
        try {
            log.info("Deleting repository {}", repoKey);
            BasicStatusHolder statusHolder = repositoryService.removeRepository(repoKey);
            if (statusHolder.hasErrors()) {
                StatusEntry statusEntry = statusHolder.getMostImportantErrorStatusCode();
                response.error(statusEntry.getMessage()).responseCode(statusEntry.getStatusCode());
            } else {
                String successMsg = "Successfully deleted '" + repoKey + "' repository";
                log.info(successMsg);
                response.info(successMsg).responseCode(SC_OK);
            }
        } catch (Exception e) {
            log.error("Deleting repo '{}' failed: {}", repoKey, e.getMessage(), e);
            response.error("Deleting repo '" + repoKey + "' failed: " + e.getMessage());
        }
    }

    private boolean onlyOneNonSystemLocalRepositoryLeft() {
        return repositoryConfigService.getRepoConfigsByType(LOCAL).stream()
                .filter(repo -> !isSystemRepo(repo.getKey())).count() == 1;
    }

    private boolean isSystemRepo(String repoKey) {
        return SystemRepos.SystemRepoConfig.isSystemRepo(repoKey) ||
                buildFacade.isBuildInfoRepo(repoKey) ||
                pipelinesService.isPipeInfoRepo(repoKey);
    }
}