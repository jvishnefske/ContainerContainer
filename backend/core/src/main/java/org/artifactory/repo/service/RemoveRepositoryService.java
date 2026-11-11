package org.artifactory.repo.service;

import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.common.RepoBulkRemovalReport;
import org.artifactory.spring.ReloadableBean;

import java.util.LinkedHashSet;

interface RemoveRepositoryService extends ReloadableBean {

    /**
     * Removes the repository with the given key.
     * If the number of artifacts in the repository is smaller than
     * {@link org.artifactory.common.ConstantValues#removeRepositoryFlowV2AsyncLimit}, the operation is synchronous.
     * Otherwise, "202 Accepted" is returned and the flow continues asynchronously.
     * In case some artifacts were non-removable, the response will contain a list of the non-removable artifacts
     * and their errors.
     * @param repoKey The key of the repository to remove.
     * @return StatusHolder with the result of the operation.
     */
    BasicStatusHolder removeRepository(String repoKey);

    /**
     * Removes the repositories with the given keys.
     * If the number of artifacts in the repository is smaller than
     * {@link org.artifactory.common.ConstantValues#removeRepositoryFlowV2AsyncLimit}, the operation is synchronous.
     * Otherwise, "202 Accepted" is returned and the flow continues asynchronously.
     * In case some artifacts were non-removable, the response will contain a list of the non-removable artifacts
     * and their errors.
     * A key difference between this and {@link RemoveRepositoryService#removeRepository(String)} singular version is
     * that the repository config deletion is made in bulk (single write to DB).
     *
     * @param repoKeys The keys of the repositories to remove.
     * @return report with the results of the operation.
     */
    RepoBulkRemovalReport removeRepositories(LinkedHashSet<String> repoKeys);
}