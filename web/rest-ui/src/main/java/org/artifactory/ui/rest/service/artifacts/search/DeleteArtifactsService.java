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

package org.artifactory.ui.rest.service.artifacts.search;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DeleteArtifact;
import org.artifactory.ui.rest.model.artifacts.search.DeleteArtifactsModel;
import org.jfrog.client.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;

/**
 * @author Gidi Shabat
 */
@Component
public class DeleteArtifactsService<T extends DeleteArtifactsModel> implements RestService<T> {
    private static final Logger log = LoggerFactory.getLogger(DeleteArtifactsService.class);

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest<T> request, RestResponse response) {
        DeleteArtifactsModel model = request.getImodel();
        RepoPath blockedPath = findBlockedInternalMetadataPath(model);
        if (blockedPath != null) {
            denyInternalMetadataDelete(response, blockedPath);
            return;
        }
        for (DeleteArtifact deleteArtifactModel : model.getArtifacts()) {
            String repoKey = deleteArtifactModel.getRepoKey();
            String path = deleteArtifactModel.getPath();
            if (StringUtils.isNotBlank(repoKey) && StringUtils.isNotBlank(path)) {
                RepoPath repoPath = InternalRepoPathFactory.create(repoKey, path);
                // delete artifact from repo path
                StatusHolder statusHolder = deleteArtifact(repoPath);
                // update response data
                updateResponseData(response, statusHolder, repoPath);
            } else {
                log.warn("Failed to delete item : {} ", repoKey + "/" + path);
            }
        }
    }

    /**
     * Deleting {@code .jfrog/} internal metadata is restricted to admins, mirroring the WebDAV/REST delete
     * handler. Guarded by a feature flag for safe rollback.
     * <p>
     * A single blocked entry rejects the whole request, so a bulk delete can never partially succeed and
     * leave the caller guessing which paths survived.
     * <p>
     * Each path is normalized first, so that variants which still resolve under {@code .jfrog/} (a leading
     * slash, repeated slashes) cannot slip past the check. Entries the delete loop below skips as blank are
     * ignored here too.
     *
     * @return the first blocked path, or null when the delete is allowed to proceed
     */
    @Nullable
    private RepoPath findBlockedInternalMetadataPath(DeleteArtifactsModel model) {
        if (!ConstantValues.deleteBlockInternalMetadata.getBoolean() || securityService.isAdmin()) {
            return null;
        }
        for (DeleteArtifact artifact : model.getArtifacts()) {
            String repoKey = artifact.getRepoKey();
            String path = artifact.getPath();
            if (StringUtils.isBlank(repoKey) || StringUtils.isBlank(path)) {
                continue;
            }
            RepoPath repoPath = InternalRepoPathFactory.create(repoKey, PathUtils.formatPath(path));
            if (InternalMetadataChecker.isInternalMetadata(repoPath.getPath())) {
                return repoPath;
            }
        }
        return null;
    }

    /**
     * Reports the block as a 404 so the response is indistinguishable from a missing path, disclosing the reason
     * through the response body only — matching the WebDAV and REST delete handlers.
     */
    private void denyInternalMetadataDelete(RestResponse<?> response, RepoPath repoPath) {
        String message = "Internal metadata request detected for '" + repoPath + "'. No artifacts were deleted.";
        log.debug(message);
        response.error(message).responseCode(SC_NOT_FOUND);
    }

    /**
     * update response feedback
     */
    private void updateResponseData(RestResponse artifactoryResponse, StatusHolder statusHolder, RepoPath repoPath) {
        if (statusHolder.isError()) {
            artifactoryResponse.error("Fail to delete the artifact : " + repoPath);
        } else {
            artifactoryResponse.info("Successfully deleted artifacts");
        }
    }

    /**
     * un deploy artifact from repo path
     *
     * @param repoPath - artifact repo path
     * @return - status of the un deploy
     */
    protected StatusHolder deleteArtifact(RepoPath repoPath) {
        return repositoryService.undeploy(repoPath, true, true);
    }
}