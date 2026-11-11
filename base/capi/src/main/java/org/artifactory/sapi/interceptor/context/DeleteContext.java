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

package org.artifactory.sapi.interceptor.context;

import lombok.Getter;
import org.artifactory.repo.RepoPath;

/**
 * A context to pass when performing item deletion.
 *
 * @author Yossi Shaul
 */
@Getter
public class DeleteContext extends AbstractInterceptorContext {

    private final RepoPath repoPath;
    private boolean triggerMetadataCalculation;
    private boolean avoidBuildDeleteInterceptor;
    private boolean allowAsyncDelete;
    private boolean skipTrashcan;
    private boolean isTriggeredByGc;
    private boolean markForEventsGc;
    private boolean isExpectEmpty;
    private boolean triggeredByFolderPruning;
    private boolean fullRepoContentDeletion;
    private boolean idempotentDelete;

    public DeleteContext(RepoPath repoPath) {
        this.repoPath = repoPath;
    }

    public DeleteContext triggerMetadataCalculation(boolean triggerMetadataCalculation) {
        this.triggerMetadataCalculation = triggerMetadataCalculation;
        return this;
    }

    public DeleteContext triggeredByMove() {
        setTriggeredByMove(true);
        return this;
    }

    public DeleteContext avoidBuildDeleteInterceptor() {
        avoidBuildDeleteInterceptor = true;
        return this;
    }

    public DeleteContext allowAsyncDelete(boolean allowAsyncDelete) {
        this.allowAsyncDelete = allowAsyncDelete;
        return this;
    }

    public DeleteContext skipTrashcan(boolean skipTrashcan) {
        this.skipTrashcan = skipTrashcan;
        return this;
    }

    /**
     * @param isTriggeredByGc true if the delete (undeploy) was triggered by minor GC
     */
    public DeleteContext triggeredByGc(boolean isTriggeredByGc) {
        this.isTriggeredByGc = isTriggeredByGc;
        return this;
    }

    /**
     * @param markForEventsGc - if true, should insert new delete event to the storage events table
     */
    public DeleteContext markForEventsGc(boolean markForEventsGc) {
        this.markForEventsGc = markForEventsGc;
        return this;
    }

    /**
     * @param isExpectEmpty - if true, ensure that folder not deleted if it has children
     */
    public DeleteContext isExpectEmpty(boolean isExpectEmpty) {
        this.isExpectEmpty = isExpectEmpty;
        return this;
    }

    /**
     * @param triggeredByFolderPruning true if the delete was triggered by the asynchronous
     *                                 folder-pruning job (empty-folder cleanup after artifact deletion)
     */
    public DeleteContext triggeredByFolderPruning(boolean triggeredByFolderPruning) {
        this.triggeredByFolderPruning = triggeredByFolderPruning;
        return this;
    }

    public DeleteContext fullRepoContentDeletion(boolean fullRepoContentDeletion) {
        this.fullRepoContentDeletion = fullRepoContentDeletion;
        return this;
    }

    /**
     * @param idempotentDelete when {@code true}, a delete that targets an already-missing artifact is treated as a
     *                         no-op success instead of failing with a not-found error. Used for overwrite/republish
     *                         cleanup deletes (e.g. build-info JSON) where a concurrent request may have already
     *                         removed the artifact, so the desired end-state (artifact absent) is already met.
     */
    public DeleteContext idempotentDelete(boolean idempotentDelete) {
        this.idempotentDelete = idempotentDelete;
        return this;
    }
}
