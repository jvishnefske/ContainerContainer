package org.artifactory.api.repo.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RepoUrlValidatorTest {

    @ParameterizedTest
    @MethodSource("invalidUrlKeyCombinations")
    void testValidateNoSelfPointingUrlError(String repoKey, String url, String baseHost) {
        assertThrows(IllegalArgumentException.class, () ->
                RepoUrlValidator.validateNoSelfPointingUrl(baseHost, repoKey, url));
    }

    @ParameterizedTest
    @MethodSource("validUrlKeyCombinations")
    void testValidateNoSelfPointingUrl(String repoKey, String url, String baseHost) {
        assertDoesNotThrow(() -> RepoUrlValidator.validateNoSelfPointingUrl(baseHost, repoKey, url));
    }

    @Test
    void testValidateNoSelfPointingUrlDifferentHosts() {
        assertDoesNotThrow(() ->
                RepoUrlValidator.validateNoSelfPointingUrl("jfrog.com", "spm-demo-repo-remote",
                        "https://servername.jfrog.io/artifactory/api/swift/spm-demo-repo-remote"));
    }

    private static Stream<Arguments> invalidUrlKeyCombinations() {
        return Stream.of(
                Arguments.of("spm-demo-repo-remote",
                        "https://servername.jfrog.io/artifactory/api/swift/spm-demo-repo-remote", "servername.jfrog.io")
        );
    }

    private static Stream<Arguments> validUrlKeyCombinations() {
        return Stream.of(
                Arguments.of("repo1", null, null),
                Arguments.of("repo1", "wrongUrl", "wrongHost"),
                Arguments.of("repo2", "://example.com/repo2", "https://example.com"),
                Arguments.of("repo1", "https://example.com/repo2", "example.com"),
                Arguments.of("repo2", "https://example.com/repo2", "test.com")
        );
    }
}
