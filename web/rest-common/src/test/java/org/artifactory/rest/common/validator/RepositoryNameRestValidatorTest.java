package org.artifactory.rest.common.validator;

import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.rest.common.exception.RepoConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.artifactory.repo.system.SystemRepos.BILLING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.EDGE_UPLOADS_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.FULL_SYNC_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.IN_TRANSIT_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.LOG_SHIPPING_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.PIPEINFO_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.repo.system.SystemRepos.USAGE_REPO_KEY;
import static org.artifactory.rest.common.validator.RepositoryNameRestValidator.validateRepoNameForCreate;
import static org.artifactory.rest.common.validator.RepositoryNameRestValidator.validateRepoNameForUpdate;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/*
 * @author Omri Ziv
 */
class RepositoryNameRestValidatorTest {

    private final RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);

    @Test
    void testValidRepoName() throws RepoConfigException {
        when(repositoryConfigService.existsByKeyIgnoreCase(anyString())).thenReturn(false);
        validateRepoNameForCreate("valid-repo-name", "remote", repositoryConfigService);
        validateRepoNameForUpdate("valid-repo-name");
    }

    @ParameterizedTest
    @MethodSource("invalidRepoNamesCreate")
    void testInvalidRepoNameCreate(String repoName) {
        when(repositoryConfigService.existsByKeyIgnoreCase(anyString())).thenReturn(false);
        assertThrows(RepoConfigException.class,
                () -> validateRepoNameForCreate(repoName, "local", repositoryConfigService));
    }

    private static Stream<Arguments> invalidRepoNamesCreate() {
        return Stream.of(
                Arguments.of("jfrog-artifactory-system-repo-name"),
                Arguments.of("jfrog-system-reserved-repo-name"),
                Arguments.of("jfrog-logs"),
                Arguments.of("jfrog-usage-logs"),
                Arguments.of("jfrog-billing-logs"),
                Arguments.of("some-repo-?-name"),
                Arguments.of("/some-repo-name"),
                Arguments.of("some-repo-name*"),
                Arguments.of(""),
                Arguments.of((Object) null),
                Arguments.of("very-very-l0000000oooooooooooooooooooooooooooooooooooong-local-repo"),
                Arguments.of("_intransit"),
                Arguments.of("artifactory-pipe-info"),
                Arguments.of("   leading-space-repo"),
                Arguments.of("trailing-space-repo   ")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRepoNamesUpdate")
    void testInvalidRepoNameUpdate(String repoName) {
        when(repositoryConfigService.existsByKeyIgnoreCase(anyString())).thenReturn(false);
        assertThrows(RepoConfigException.class, () -> validateRepoNameForUpdate(repoName));
    }

    private static Stream<Arguments> invalidRepoNamesUpdate() {
        return Stream.of(
                Arguments.of(LOG_SHIPPING_REPO_KEY),
                Arguments.of(USAGE_REPO_KEY),
                Arguments.of(BILLING_REPO_KEY)
        );
    }

    @ParameterizedTest
    @MethodSource("validRepoNamesUpdate")
    void testValidRepoNameUpdate(String repoName) {
        when(repositoryConfigService.existsByKeyIgnoreCase(anyString())).thenReturn(false);
        assertDoesNotThrow(() -> validateRepoNameForUpdate(repoName));
    }

    private static Stream<Arguments> validRepoNamesUpdate() {
        return Stream.of(
                Arguments.of(BUILDINFO_REPO_KEY),
                Arguments.of(PIPEINFO_REPO_KEY),
                Arguments.of(IN_TRANSIT_REPO_KEY),
                Arguments.of(EDGE_UPLOADS_REPO_KEY),
                Arguments.of(RELEASE_BUNDLE_REPO_KEY),
                Arguments.of(FULL_SYNC_REPO_KEY)
        );
    }

    @Test
    void testKeyUnavailableInRepositoryConfigService() {
        when(repositoryConfigService.existsByKeyIgnoreCase(anyString())).thenReturn(true);
        assertThrows(RepoConfigException.class,
                () -> validateRepoNameForCreate("valid-repo-name", "remote", repositoryConfigService));
    }
}
