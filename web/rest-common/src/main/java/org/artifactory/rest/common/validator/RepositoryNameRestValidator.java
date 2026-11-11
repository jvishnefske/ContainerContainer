package org.artifactory.rest.common.validator;

import lombok.experimental.UtilityClass;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.rest.common.exception.RepoConfigException;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isBlacklistedPrefix;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isForbiddenCharacter;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isForbiddenName;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isKeyAvailableInRepositoryConfigService;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isKeyBlank;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isMaxLengthExceed;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isObservabilityName;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isReservedName;

@UtilityClass
public class RepositoryNameRestValidator {

    public static void validateRepoNameForCreate(String repoKey, String repoType,
            RepositoryConfigService repositoryConfigService) throws RepoConfigException {
        try {
            isKeyBlank(repoKey);
            isMaxLengthExceed(repoKey, repoType);
            isReservedName(repoKey);
            isBlacklistedPrefix(repoKey);
            isForbiddenName(repoKey);
            isForbiddenCharacter(repoKey);
            isKeyAvailableInRepositoryConfigService(repoKey, repositoryConfigService);
        } catch (IllegalArgumentException e) {
            throw new RepoConfigException(e.getMessage(), SC_BAD_REQUEST);
        }
    }

    public static void validateRepoNameForUpdate(String repoKey) throws RepoConfigException {
        try {
            isObservabilityName(repoKey);
        } catch (IllegalArgumentException e) {
            throw new RepoConfigException(e.getMessage(), SC_BAD_REQUEST);
        }
    }
}
