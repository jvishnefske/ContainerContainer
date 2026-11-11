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

import com.google.common.annotations.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.NegotiationException;
import org.artifactory.federation.exceptions.FederatedRemoteMembersUpdateException;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.federated.NegotiationConflictsModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.UpdateRepoConfigHelper;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.validator.RepoConfigValidator;
import org.artifactory.util.ExceptionUtils;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.security.crypto.exception.CryptoRuntimeException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * @author Dan Feldman
 * @author Aviad Shikloshi
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class UpdateRepositoryConfigService implements RestService<RepositoryConfigModel<?, ?, ?>> {

    private static final String FED_ATTR_OPT_IN_PARAM = "isFedAttrSupported";

    private final UpdateRepoConfigHelper updater;
    private final ProjectsService projectsService;
    private final RepoConfigValidator repoValidator;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        RepositoryConfigModel<?, ?, ?> model = (RepositoryConfigModel<?, ?, ?>) request.getImodel();
        log.debug("Creating descriptor from received model");
        String repoKey = model.getGeneral().getRepoKey();
        log.info("Updating repository {}", repoKey);
        boolean fedAttrOptIn = ConstantValues.repositoriesFederationsAttributeEnabled.getBoolean() &&
                "true".equalsIgnoreCase(request.getQueryParamByKey(FED_ATTR_OPT_IN_PARAM));
        try {
            repoValidator.validateRepoNameForUpdate(model.getGeneral().getRepoKey());
            validateEnvironmentsForRepoType(model, repoKey);
            model.updateRepo(updater, fedAttrOptIn);
            updateRepoEnvironmentsIfNeeded(model, repoKey);
            String successMsg = "Successfully updated repository '" + repoKey + "'";
            log.info(successMsg);
            response.info(successMsg);
        } catch (NegotiationException e) {
            log.warn(e.getMessage());
            log.debug(e.getMessage(), e);
            response.responseCode(HttpStatus.SC_BAD_REQUEST)
                    .entity(new NegotiationConflictsModel(e.getMessage(), e.getConflicts()))
                    .buildResponse();
        } catch (FederatedRemoteMembersUpdateException e) {
            log.warn(e.getMessage());
            log.debug(e.getMessage(), e);
            response.error(e.getMessage());
        } catch (CryptoRuntimeException e) {
            log.warn("Failed to decrypt repository {} password {}", repoKey, e.getMessage());
            response.responseCode(HttpStatus.SC_BAD_REQUEST).error("The password could not be decrypted");
        } catch (RepoConfigException e) {
            log.warn("Failed to update repository '{}'.", repoKey, e);
            response.responseCode(e.getStatusCode())
                    .error("Failed to update repository " + repoKey + ": " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update repository {}: {}", repoKey, e.getMessage());
            log.debug("Failed to update repository: ", e);
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            response.error("Failed to update repository " + repoKey + ": " +
                    (rootCause != null ? rootCause.getMessage() : e.getMessage()));
        }
    }

    /**
     * Rejects assigning or changing env/stage on build-info or release-bundle repositories before any repo mutation.
     * Re-sending the environments already assigned to the repo is allowed, so a read -> modify (non-env field) ->
     * update round trip does not fail because the existing env value is re-sent.
     */
    @VisibleForTesting
    void validateEnvironmentsForRepoType(RepositoryConfigModel<?, ?, ?> model, String repoKey)
            throws RepoConfigException {
        // Resolve the project the repo is actually assigned to rather than the (possibly omitted, "default"-defaulted)
        // project key from the request body. Using the request's project key would look up the wrong project, return
        // null current environments and falsely reject a legitimate re-send of the repo's existing environments.
        String projectKey = projectsService.getProjectKeyOfRepository(repoKey);
        ProjectResourceEntity existingProject = projectsService.getProjectRepositoryEntity(projectKey, repoKey);
        Collection<String> currentEnvironments =
                existingProject != null ? existingProject.getEnvironments() : Set.of();
        repoValidator.validateEnvironmentsForRepoType(model, currentEnvironments);
    }

    @VisibleForTesting
    void updateRepoEnvironmentsIfNeeded(RepositoryConfigModel<?, ?, ?> model, String repoKey) {
        String projectKey = model.getGeneral().getProjectKey();
        ProjectResourceEntity existingProject = projectsService.getProjectRepositoryEntity(projectKey, repoKey);
        Set<String> envsFromRequest = model.getGeneral().getEnvironments();
        if (existingProject != null) {
            if (!CollectionUtils.isEqualCollection(envsFromRequest, existingProject.getEnvironments())) {
                log.info("Updating environments for repo {}, project {}", repoKey, projectKey);
                projectsService.updateProjectRepository(repoKey, model.getGeneral().getEnvironments());
            }
        } else {
            // For some cases (like default project without environments) Access is returning null,
            // but we still should update envs
            log.info("Updating environments for repo {}", repoKey);
            projectsService.updateProjectRepository(repoKey, model.getGeneral().getEnvironments());
        }
    }
}