package org.artifactory.repo.service.validation;

import lombok.experimental.UtilityClass;

import static org.artifactory.repo.service.validation.RepositoryNameValidator.isBlacklistedPrefix;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isForbiddenCharacter;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isForbiddenName;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isKeyBlank;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isMaxLengthExceed;
import static org.artifactory.repo.service.validation.RepositoryNameValidator.isReservedName;

@UtilityClass
public class RepositoryNameConfigurationValidator {

    public static void validateYamlRepoName(String repoKey)
            throws IllegalArgumentException {
        isReservedName(repoKey);
        isBlacklistedPrefix(repoKey);
    }

    public static void validateInternalRepoName(String repoKey, String repoType)
            throws IllegalArgumentException {
        isKeyBlank(repoKey);
        isMaxLengthExceed(repoKey, repoType);
        isForbiddenName(repoKey);
        isForbiddenCharacter(repoKey);
    }
}
