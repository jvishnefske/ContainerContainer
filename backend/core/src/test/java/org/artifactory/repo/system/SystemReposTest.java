package org.artifactory.repo.system;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.system.SystemRepos.SystemRepoConfig;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Eli Skoran 07/11/2022
 */
class SystemReposTest extends ArtifactoryHomeBoundJUnitTest {

    @ParameterizedTest
    @MethodSource("testInTransitDataProvider")
    void testInTransit(String repoName, boolean expectedIsInTransit) {
        // test
        boolean isInTransit = SystemRepos.isInTransitRepo(repoName);
        // verify
        assertEquals(expectedIsInTransit, isInTransit);
    }

    private static Stream<Arguments> testInTransitDataProvider() {
        return Stream.of(
                Arguments.of("docker-local", false),
                Arguments.of("_intransit", true),
                Arguments.of("intransit", false),
                Arguments.of(null, false)
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("testIsBuildInfoTypeRepoDataProvider")
    void testIsBuildInfoTypeRepo(RepoConfig repoConfig, boolean expected, String description) {
        assertEquals(expected, SystemRepoConfig.isBuildInfoTypeRepo(repoConfig),
                "Case: " + description);
    }

    private static Stream<Arguments> testIsBuildInfoTypeRepoDataProvider() {
        return Stream.of(
                Arguments.of(buildInfoRepo("myprj-build-info"), true, "BuildInfo type"),
                Arguments.of(buildInfoRepo("artifactory-build-info"), true,
                        "BuildInfo type (default build-info repo key)"),
                Arguments.of(localRepo("docker-local", RepoType.Generic), false, "Generic type"),
                Arguments.of(null, false, "null repoConfig"),
                Arguments.of(localRepo("myprj-build-info", RepoType.Generic), false,
                        "user-created Generic repo with build-info-like key"),
                Arguments.of(localRepo("myprj-build-info", RepoType.Maven), false,
                        "user-created Maven repo with build-info-like key")
        );
    }

    private static RepoConfig buildInfoRepo(String key) {
        return localRepo(key, RepoType.BuildInfo);
    }

    private static RepoConfig localRepo(String key, RepoType type) {
        return LocalRepoConfig.create(key, type);
    }
}