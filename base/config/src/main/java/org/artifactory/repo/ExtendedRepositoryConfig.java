package org.artifactory.repo;

import org.artifactory.model.RepoConfig;

/**
 * @author Tamar Rosen
 */
public class ExtendedRepositoryConfig<T> {
    private RepoConfig repositoryConfig;

    private T extendedRepoConfig;

    public ExtendedRepositoryConfig(RepoConfig repositoryConfig, T extendedRepoConfig) {
        this.repositoryConfig = repositoryConfig;
        this.extendedRepoConfig = extendedRepoConfig;
    }

    public T getExtendedRepoConfig() {
        return extendedRepoConfig;
    }
}
