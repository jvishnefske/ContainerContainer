package org.artifactory.repo.service.validation;

import com.google.common.collect.Lists;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.util.UiRequestUtils;

import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.isSystemRepo;
import static org.artifactory.repo.system.SystemRepos.isObservabilityRepo;
import static org.jdom2.Verifier.isXMLNameCharacter;

@UtilityClass
public class RepositoryNameValidator {
    private static final int REPO_KEY_MAX_LENGTH = 64;
    private static final List<String> forbiddenNames = List.of(".", "..", "&");
    private static final List<Character> forbiddenChars = Lists
            .newArrayList('/', '\\', ':', '|', '?', '*', '"', '<', '>');
    private static final List<String> blacklistPrefixes = List.of("jfrog-system-reserved", "jfrog-artifactory-system");

    public static void isKeyBlank(String repoKey) throws IllegalArgumentException {
        if (isBlank(repoKey)) {
            throw new IllegalArgumentException("Repository key cannot be empty");
        }
    }

    public static void isMaxLengthExceed(String repoKey, String repoType) {
        //Local repos limited at 64, remote repos append '-cache' to cache repos which is 6 chars.
        int maxLength = "remote".equals(repoType) ? (REPO_KEY_MAX_LENGTH - 6) : REPO_KEY_MAX_LENGTH;
        if (StringUtils.length(repoKey) > maxLength) {
            throw new IllegalArgumentException("Repository key exceeds maximum length (" + maxLength + ") chars.");
        }
    }

    public static void isReservedName(String repoKey) {
        if (UiRequestUtils.isReservedName(repoKey) || isSystemRepo(repoKey)) {
            throw new IllegalArgumentException("Repository key '" + repoKey + "' is a reserved name");
        }
    }

    public static void isObservabilityName(String repoKey) {
        if (isObservabilityRepo(repoKey)) {
            throw new IllegalArgumentException("Repository key '" + repoKey + "' is a reserved name");
        }
    }

    public static void isBlacklistedPrefix(String repoKey) {
        Optional<String> prefix = blacklistPrefixes.stream().filter(repoKey::startsWith).findFirst();
        if (prefix.isPresent()) {
            throw new IllegalArgumentException(
                    "Repository key '" + repoKey + "' contains blacklisted prefix '" + prefix.get() + "'");
        }
    }

    public static void isForbiddenName(String repoKey) {
        if (forbiddenNames.stream().anyMatch(repoKey::equals)) {
            throw new IllegalArgumentException("Invalid Repository key");
        }
    }

    public static void isForbiddenCharacter(String repoKey) {
        for (int i = 0; i < repoKey.length(); ++i) {
            if (forbiddenChars.contains(repoKey.charAt(i))) {
                throw new IllegalArgumentException("Illegal Repository key : '/,\\,:,|,?,<,>,*,\"' is not allowed");
            }
            if (!isXMLNameCharacter(repoKey.charAt(i))) {
                throw new IllegalArgumentException("Illegal Repository key : '" + repoKey.charAt(i) + "'");
            }
        }
    }

    public static void isKeyAvailableInRepositoryConfigService(String repoKey,
            RepositoryConfigService repositoryConfigService) {
        if (repositoryConfigService.existsByKeyIgnoreCase(repoKey)) {
            throw new IllegalArgumentException("Repository key already exists");
        }
    }
}
