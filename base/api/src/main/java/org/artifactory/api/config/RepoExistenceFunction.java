package org.artifactory.api.config;

/**
 * Interface for checking the existence of a repository by its key.
 *
 * @author Mor Merhav
 */
public interface RepoExistenceFunction {

    boolean existsByRepoKey(String repoKey);
}