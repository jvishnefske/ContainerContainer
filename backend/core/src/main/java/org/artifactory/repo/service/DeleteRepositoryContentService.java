package org.artifactory.repo.service;

import org.artifactory.api.common.BasicStatusHolder;

interface DeleteRepositoryContentService {

    /**
     * If real repository, deletes content recursively.
     * If virtual repository, NOOP
     *
     * @param repoKey the repository key
     * @param statusHolder collects artifact deletion failures
     * @throws RepositoryContentDeletionFailedException in case an artifact could not be deleted, or repository not
     * found
     */
    void deleteRepositoryContent(String repoKey, BasicStatusHolder statusHolder);
}