package org.artifactory.repo.service;

import jakarta.annotation.Nonnull;
import org.artifactory.model.RepoConfig;
import org.artifactory.spring.ReloadableBean;

interface RemoveRepositoryValidator extends ReloadableBean {

    /**
     * Validates the removal of the repository, including calling interceptors, for the given key.
     * if the repository is non-removable, Status holder will contain an error or warning.
     * Otherwise, 200 OK.
     *
     * @param repoConfig The key of the repository to remove.
     * @throws RepositoryRemovalFailedException containing a status holder upon validation failure
     */
    void validate(@Nonnull RepoConfig repoConfig);
}