package org.artifactory.repo.interceptor;

/**
 * @author Mor Merhav
 * Interface for validators which run upon any Repository Configuration change (Create/Update/Delete) and throw
 * {@link org.artifactory.config.ConfigurationException} with an appropriate status code and message if validation fails
 */
public interface RepoConfigChangeValidator {

    void validate(RepoConfigChanges repoConfigChanges);
}