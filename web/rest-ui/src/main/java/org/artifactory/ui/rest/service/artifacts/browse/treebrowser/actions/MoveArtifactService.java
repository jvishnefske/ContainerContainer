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
import lombok.RequiredArgsConstructor;
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
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

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
@RequiredArgsConstructor
public class MoveArtifactService extends BaseCopyMoveArtifactService {

    private final RepositoryService repositoryService;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        CopyMoveArtifact moveArtifact = (CopyMoveArtifact) request.getImodel();

        populateMdcFromSource(moveArtifact.getRepoKey(), repositoryService);

        RepoPath sourceRepoPath = RepoPathFactory.create(moveArtifact.getRepoKey(), moveArtifact.getPath());
        if (!isUserAuthorized(response, securityService.currentUsername(),
                () -> authorizationService.canRead(sourceRepoPath) && authorizationService.canDelete(sourceRepoPath))) {
            return;
        }
        if (isInternalMetadataTargetBlocked(response, moveArtifact.getTargetRepoKey(),
                moveArtifact.getTargetPath())) {
            return;
        }

        moveArtifact(response, moveArtifact);
    }

    /**
     * move artifact from one repo to another
     *
     * @param artifactoryResponse - encapsulate data related to response
     * @param moveArtifact        - move action model
     */
    private void moveArtifact(RestResponse artifactoryResponse, CopyMoveArtifact moveArtifact) {
        ExecutorService exec = createExecutorService("move-operation");
        RepoPath sourceRepoPath = InternalRepoPathFactory.create(moveArtifact.getRepoKey(), moveArtifact.getPath());
        RepoPath targetRepoPath = InternalRepoPathFactory.create(moveArtifact.getTargetRepoKey(),
                PathUtils.formatPath(moveArtifact.getTargetPath()));
        Callable<MoveMultiStatusHolder> task = new ArtifactoryCallable<>(
                new DoInBackground(moveArtifact, sourceRepoPath, targetRepoPath),
                ContextHelper.get(), AuthenticationHelper.getAuthentication());
        try {
            Future<MoveMultiStatusHolder> future = exec.submit(task);

            waitAndRespondWithTimeout(artifactoryResponse, moveArtifact, future);
        } catch (UnifiedEventException e) {
            throw e;
        } catch (Exception e) {
            String msg = String.format("Failed to move from src=%s to target=%s: ", moveArtifact.getPath(),
                    moveArtifact.getTargetPath());
            MoveMultiStatusHolder status = new MoveMultiStatusHolder();
            log.debug("{}", e);
            status.error(msg, e, log);
        } finally {
            exec.shutdown();
        }
    }

    @VisibleForTesting
    void waitAndRespondWithTimeout(RestResponse artifactoryResponse, CopyMoveArtifact moveArtifact,
            Future<MoveMultiStatusHolder> future) {
        try {
            MoveMultiStatusHolder moveStatus = future.get(TIMEOUT, TimeUnit.SECONDS);

            updateResponseData(artifactoryResponse, moveArtifact, moveStatus);
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof UnifiedEventException unifiedEventException) {
                throw unifiedEventException;
            }
            throw new RuntimeException("Move operation failed.", e);
        } catch (TimeoutException e) {
            artifactoryResponse.responseCode(HttpStatus.SC_ACCEPTED)
                    .info("Move operation will continue in the background");
        }
    }

    /**
     * update response feedback and http status code
     *
     * @param artifactoryResponse - encapsulate data require for response
     * @param moveArtifact        - move action model
     * @param moveStatus          - move status holder
     */
    private void updateResponseData(RestResponse artifactoryResponse, CopyMoveArtifact moveArtifact,
            MoveMultiStatusHolder moveStatus) {
        if (moveStatus.hasErrors()) {
            ResponseUtils.errorResponse(artifactoryResponse, moveStatus);
        } else {
            artifactoryResponse.info(
                    "Successfully moved artifact to " + moveArtifact.getTargetRepoKey() + "/" +
                            moveArtifact.getTargetPath());
        }
    }

    @Override
    MoveMultiStatusHolder doOperation(CopyMoveArtifact artifact, RepoPath sourcePath, RepoPath targetPath) {
        return repositoryService.moveMultiTx(sourcePath, targetPath, artifact.isDryRun(),
                artifact.isSuppressLayouts(), artifact.isFailFast());
    }

    @Override
    OperationType getOperationType() {
        return OperationType.MOVE;
    }

}
