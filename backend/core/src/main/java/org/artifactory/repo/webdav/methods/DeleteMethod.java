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

package org.artifactory.repo.webdav.methods;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.mime.NamingUtils;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.webdav.WebdavMethod;
import org.artifactory.request.ArtifactoryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Yoav Luft
 */
@Slf4j
@Component
public class DeleteMethod implements WebdavMethod {

    private InternalRepositoryService repoService;
    private PropertiesService propertiesService;
    private SecurityService securityService;

    @Autowired
    public void setRepoService(InternalRepositoryService repoService) {
        this.repoService = repoService;
    }

    @Autowired
    public void setPropertiesService(PropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public boolean canHandle(String method) {
        return getName().equalsIgnoreCase(method);
    }

    @Override
    public void handle(ArtifactoryRequest request, ArtifactoryResponse response) throws IOException {
        log.debug("Handling {}", getName());
        RepoPath repoPath = request.getRepoPath();
        String repoKey = repoPath.getRepoKey();
        LocalRepo<?, ?> localRepository = repoService.localFederatedOrCachedRepositoryByKey(repoKey);
        if (localRepository == null) {
            response.setStatus(HttpStatus.SC_NOT_FOUND);
            return;
        }
        if (InternalMetadataChecker.isInternalMetadata(repoPath.getPath()) && !securityService.isAdmin()) {
            String msg = "Internal metadata request detected for '" + repoPath + "'.";
            response.sendError(HttpStatus.SC_NOT_FOUND, msg);
            return;
        }
        if (!NamingUtils.isProperties(repoPath.getPath())) {
            deleteItems(request, response, repoPath);
        } else {
            deleteProperties(response, repoPath);
        }
    }

    /**
     * Delete items in single or multi trx
     *
     * @param request  - encapsulate data related to http servlet request
     * @param response - encapsulate data related to http servlet response
     * @param repoPath - repository path
     */
    @VisibleForTesting
    void deleteItems(ArtifactoryRequest request, ArtifactoryResponse response, RepoPath repoPath)
            throws IOException {
        boolean atomic = Boolean.parseBoolean(request.getParameter("atomic"));
        StatusHolder statusHolder;
        if (atomic) {
            statusHolder = repoService.undeploy(repoPath);
        } else {
            statusHolder = repoService.undeployMultiTransaction(repoPath);
        }
        if (statusHolder.isError() || statusHolder.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
            response.sendError(statusHolder.getStatusCode(), statusHolder.getStatusMsg());
        } else {
            response.setStatus(HttpStatus.SC_NO_CONTENT);
        }
    }

    @Override
    public String getName() {
        return "delete";
    }

    private void deleteProperties(ArtifactoryResponse response, RepoPath repoPath) throws IOException {
        RepoPathImpl itemRepoPath = new RepoPathImpl(repoPath.getRepoKey(),
                NamingUtils.stripMetadataFromPath(repoPath.getPath()));
        boolean removed = propertiesService.removeProperties(itemRepoPath);
        if (removed) {
            response.setStatus(HttpStatus.SC_NO_CONTENT);
        } else {
            response.sendError(HttpStatus.SC_NOT_FOUND, "Failed to remove properties from " + itemRepoPath, log);
        }
    }
}
