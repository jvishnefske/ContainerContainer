package org.artifactory.repo.service.validation;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.util.UiRequestUtils;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

/*
 * @author Omri Ziv
 */
public class RepositoryNameValidatorTest {
    private static final List<Character> illegalCharacters =
            List.of('/', '\\', ':', '|', '?', '*', '"', '<', '>', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '=',
                    '~', '`', ';', ',', ' ');

    private final RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);

    @Test
    public void validateBadCharacters() {
        illegalCharacters.forEach(chr -> {
            try {
                RepositoryNameValidator.isForbiddenCharacter("repo" + chr + "name");
            } catch (IllegalArgumentException e) {
                return;
            }
            Assert.fail();
        });
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Repository key " +
            "cannot be empty")
    public void negativeEmptyRepoKey() {
        RepositoryNameValidator.isKeyBlank("");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Repository key " +
            "cannot be empty")
    public void negativeNullRepoKey() {
        RepositoryNameValidator.isKeyBlank(null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void negativeMaxLengthRepo() {
        try {
            RepositoryNameValidator
                    .isMaxLengthExceed("very-very-l0000000oooooooooooooooooooooooooooooooooooong-remote-repo", "local");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Repository key exceeds maximum length"));
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void negativeMaxLengthRemoteRepo() {
        try {
            RepositoryNameValidator
                    .isMaxLengthExceed("very-very-loooooooooooooooooooooooooooooooooooong-remote-repo", "remote");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Repository key exceeds maximum length"));
            throw e;
        }
    }

    @Test(dataProvider = "prefixes", expectedExceptions = IllegalArgumentException.class)
    public void blacklistedPrefixRepoNameArtifactory(String prefix) {
        try {
            RepositoryNameValidator.isBlacklistedPrefix(prefix + "bla");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("contains blacklisted prefix"));
            throw e;
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Repository key already exists")
    public void validateRepoKeyNotAvailableRepoExists() {
        when(repositoryConfigService.existsByKeyIgnoreCase(anyString())).thenReturn(true);
        RepositoryNameValidator.isKeyAvailableInRepositoryConfigService("valid-repo-name", repositoryConfigService);
    }

    @Test(dataProvider = "reservedNames", expectedExceptions = IllegalArgumentException.class)
    public void validateReservedNames(String repoName) {
        RepositoryNameValidator.isReservedName(repoName);
    }

    @Test
    public void validateReservedPathPrefixes() {
        UiRequestUtils.setUiPathPrefixes(List.of("ui-path-prefix"));
        UiRequestUtils.setNonUiPathPrefixes(List.of("non-ui-path-prefix"));
        List.of("ui-path-prefix", "non-ui-path-prefix")
                .forEach(pathPrefix -> Assert.assertThrows(IllegalArgumentException.class,
                        () -> RepositoryNameValidator.isReservedName(pathPrefix)));
    }

    @Test(dataProvider = "forbiddenNames", expectedExceptions = IllegalArgumentException.class)
    public void validateForbiddenNames(String forbiddenName) {
        RepositoryNameValidator.isForbiddenName(forbiddenName);
    }

    @DataProvider(name = "prefixes")
    public static Object[][] prefixes() {
        return new Object[][] {
                {"jfrog-artifactory-system"},
                {"jfrog-system-reserved"}
        };
    }

    @DataProvider(name = "forbiddenNames")
    public static Object[][] forbiddenNames() {
        return new Object[][] {
                {"."},
                {".."},
                {"&"}
        };
    }

    @DataProvider(name = "reservedNames")
    public static Object[][] reservedNames() {
        List<Object[]> names =
                Arrays.stream(SystemRepos.SystemRepoConfig.values()).map(SystemRepos.SystemRepoConfig::getRepoKey)
                        .map(key -> new Object[] {key}).toList();
        return names.toArray(new Object[names.size()][]);
    }
}
