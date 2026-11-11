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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import com.google.common.annotations.VisibleForTesting;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.concurrent.ArtifactoryCallable;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.CopyMoveArtifact;
import org.artifactory.ui.utils.ResponseUtils;
import org.jfrog.client.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Chen Keinan
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class CopyArtifactService extends BaseCopyMoveArtifactService {

    private RepositoryService repositoryService;
    private AuthorizationService authorizationService;
    private SecurityService securityService;

    @Autowired
    public void setRepositoryService(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setAuthorizationService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        CopyMoveArtifact copyArtifact = (CopyMoveArtifact) request.getImodel();

        populateMdcFromSource(copyArtifact.getRepoKey(), repositoryService);

        RepoPath sourceRepoPath = RepoPathFactory.create(copyArtifact.getRepoKey(), copyArtifact.getPath());
        if (!isUserAuthorized(response, securityService.currentUsername(),
                () -> authorizationService.canRead(sourceRepoPath))) {
            return;
        }
        if (isInternalMetadataTargetBlocked(response, copyArtifact.getTargetRepoKey(),
                copyArtifact.getTargetPath())) {
            return;
        }

        copyArtifact(response, copyArtifact);
    }

    /**
     * copy artifact from one repo to another
     *
     * @param artifactoryResponse - encapsulate data related to response
     * @param copyArtifact        - copy action model
     */
    private void copyArtifact(RestResponse artifactoryResponse, CopyMoveArtifact copyArtifact) {
        ExecutorService exec = createExecutorService("copy-operation");
        RepoPath sourcePath = repositoryService
                .getItemInfo(InternalRepoPathFactory.create(copyArtifact.getRepoKey(), copyArtifact.getPath()))
                .getRepoPath();
        RepoPath targetPath = InternalRepoPathFactory.create(copyArtifact.getTargetRepoKey(),
                PathUtils.formatPath(copyArtifact.getTargetPath()), sourcePath.isFolder());
        Callable<MoveMultiStatusHolder> task = new ArtifactoryCallable<>(
                new DoInBackground(copyArtifact, sourcePath, targetPath),
                ContextHelper.get(), AuthenticationHelper.getAuthentication());
        try {
            Future<MoveMultiStatusHolder> future = exec.submit(task);

            waitAndRespondWithTimeout(artifactoryResponse, copyArtifact, future);
        } finally {
            exec.shutdown();
        }
    }

    @VisibleForTesting
    void waitAndRespondWithTimeout(RestResponse artifactoryResponse, CopyMoveArtifact copyArtifact,
            Future<MoveMultiStatusHolder> future) {
        try {
            MoveMultiStatusHolder copyStatus = future.get(TIMEOUT, TimeUnit.SECONDS);

            updateResponseData(artifactoryResponse, copyArtifact, copyStatus);
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof UnifiedEventException unifiedEventException) {
                throw unifiedEventException;
            }
            throw new RuntimeException("Copy operation failed.", e);
        } catch (TimeoutException e) {
            artifactoryResponse.responseCode(HttpStatus.SC_ACCEPTED)
                    .info("Copy operation will continue in the background");
        }
    }

    /**
     * update response feedback and http status code
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param copyArtifact        - copy action model
     * @param copyStatus          - copy status holder
     */
    private void updateResponseData(RestResponse artifactoryResponse, CopyMoveArtifact copyArtifact,
            MoveMultiStatusHolder copyStatus) {
        try {
            if (copyStatus.hasErrors()) {
                ResponseUtils.errorResponse(artifactoryResponse, copyStatus);
            } else {
                artifactoryResponse.info(
                        "Artifacts successfully copied to: " + copyArtifact.getTargetRepoKey() + ":" +
                                copyArtifact.getTargetPath());
            }
        } catch (Exception e) {
            artifactoryResponse.responseCode(HttpServletResponse.SC_CONFLICT);
            List<String> errors = new ArrayList<>();
            copyStatus.getErrors().forEach(error -> errors.add(error.getMessage()));
            artifactoryResponse.errors(errors);
        }
    }

    @Override
    MoveMultiStatusHolder doOperation(CopyMoveArtifact copyArtifact, RepoPath sourcePath, RepoPath targetPath) {
        return repositoryService.copyMultiTx(sourcePath, targetPath, copyArtifact.isDryRun(),
                copyArtifact.isSuppressLayouts(), copyArtifact.isFailFast());
    }

    @Override
    OperationType getOperationType() {
        return OperationType.COPY;
    }

}
