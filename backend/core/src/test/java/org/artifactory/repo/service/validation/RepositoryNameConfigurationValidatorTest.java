package org.artifactory.repo.service.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryNameConfigurationValidatorTest {

    private AutoCloseable mockitoAutoCloseable;

    @BeforeEach
    void setUp() {
        mockitoAutoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoAutoCloseable.close();
    }

    @ParameterizedTest
    @MethodSource("yamlRepoNames")
    void testInvalidYamlRepoName(String repoName) {
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryNameConfigurationValidator.validateYamlRepoName(repoName));
    }

    @ParameterizedTest
    @MethodSource("internalRepoNames")
    void testInvalidInternalRepoName(String repoName, boolean shouldThrow) {
        if (shouldThrow) {
            assertThrows(IllegalArgumentException.class,
                    () -> RepositoryNameConfigurationValidator.validateInternalRepoName(repoName, "local"));
        } else {
            assertDoesNotThrow(() -> RepositoryNameConfigurationValidator.validateInternalRepoName(repoName, "local"));
        }
    }

    private static Object[][] yamlRepoNames() {
        return new Object[][] {
                {"jfrog-artifactory-system-repo-name"},
                {"jfrog-system-reserved-repo-name"},
                {"_intransit"},
                {"artifactory-pipe-info"}
        };
    }

    private static Object[][] internalRepoNames() {
        return new Object[][] {
                {"jfrog-artifactory-system-repo-name", false},
                {"jfrog-system-reserved-repo-name", false},
                {"some-repo-?-name", true},
                {"/some-repo-name", true},
                {"some-repo-name*", true},
                {"", true},
                {null, true},
                {"very-very-l0000000oooooooooooooooooooooooooooooooooooong-local-repo", true},
                {"_intransit", false},
                {"artifactory-pipe-info", false}
        };
    }
}
