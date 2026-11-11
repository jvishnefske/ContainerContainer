package org.artifactory.repo.service.validation;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AolUtils;
import org.artifactory.util.HttpUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * @author Oleksandr Legenkiy
 */
class RepoKeyLengthLimitsHelperTest extends ArtifactoryHomeBoundJUnitTest {

    private static final int DEFAULT_REPO_KEY_LENGTH_LIMIT = 64;
    private static final int DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT = 63;
    private static final int REMOTE_REPO_KEY_LENGTH_LIMIT = 58; // 64 - "-cache".length()

    private RepoKeyLengthLimitsHelper helper;

    @BeforeEach
    void setUp() {
        helper = new RepoKeyLengthLimitsHelper();
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.enableMaxRepoKeyLengthSoftLimit.getPropertyName(), Boolean.TRUE.toString());
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.aolDockerSkipCreatingDnsRecord.getPropertyName(), Boolean.TRUE.toString());
    }

    @Test
    void testGetForbiddenChars() {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.aolForbiddenRepoKeyChars.getPropertyName(), "._");
        assertEquals(Set.of('.', '_'), helper.getForbiddenChars());
    }

    @ParameterizedTest
    @MethodSource("provideHardLimitTestArguments")
    void testHardLimit(RepoDetailsType repoType, RepoType repoPackageType, int expectedResult, int expResAol) {
        runWithMockedAolUtils(false, () -> {
            int hardLimit = helper.hardLimit(repoType, repoPackageType);
            assertEquals(expectedResult, hardLimit);
        });
        runWithMockedAolUtils(true, () -> {
            int hardLimit = helper.hardLimit(repoType, repoPackageType);
            assertEquals(expResAol, hardLimit);
        });
    }

    @ParameterizedTest
    @MethodSource("provideSoftLimitTestArguments")
    void testSoftLimit(RepoType repoPackageType, boolean aol, Boolean softLimitEnabled,
            boolean dockerSubdomainEnabled, Integer expectedSoftLimit) {
        var helperSpy = spy(helper);
        doReturn("abcde").when(helperSpy).getServerNameForDnsRecord(); // server name is 5 chars long
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.enableMaxRepoKeyLengthSoftLimit.getPropertyName(),
                        softLimitEnabled.toString());
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.aolDockerSkipCreatingDnsRecord.getPropertyName(),
                        Boolean.valueOf(!dockerSubdomainEnabled).toString());
        runWithMockedAolUtils(aol, () -> {
            Integer softLimit = helperSpy.softLimit(repoPackageType);
            assertEquals(expectedSoftLimit, softLimit);
        });
    }

    @ParameterizedTest
    @MethodSource("provideTestServerNameArguments")
    void testServerNameIsExtractedProperlyFromRequest(String artifactoryUrl, boolean aol, String expectedResult) {
        try (MockedStatic<HttpUtils> httpUtilsMockedStatic = Mockito.mockStatic(HttpUtils.class)) {
            httpUtilsMockedStatic.when(HttpUtils::getArtifactoryUrlFromRequestIfPresent).thenReturn(artifactoryUrl);
            runWithMockedAolUtils(aol, () -> assertEquals(expectedResult, helper.getServerNameForDnsRecord()));
        }
    }

    @ParameterizedTest
    @MethodSource("provideRepoKeyExceedsSoftLimitTestData")
    void testRepoKeyExceedsSoftLimit(Boolean shouldUseSoftLimit, boolean softLimitIsNull, String repoKey,
            boolean expRes, String expMessage) {
        // mocking a shouldUseSoftLimit method
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.enableMaxRepoKeyLengthSoftLimit.getPropertyName(),
                        shouldUseSoftLimit.toString());
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.aolDockerSkipCreatingDnsRecord.getPropertyName(),
                        Boolean.valueOf(!shouldUseSoftLimit).toString());
        var helperSpy = spy(helper);
        doReturn("abcde").when(helperSpy).getServerNameForDnsRecord(); // server name is 5 char long
        Integer softLimit = softLimitIsNull ? null : 57; // 63 - "abcde".length() - 1 = 57
        doReturn(softLimit).when(helperSpy).softLimit(any());
        doCallRealMethod().when(helperSpy).repoKeyExceedsSoftLimit(any(), any());
        runWithMockedAolUtils(true, () -> {
            var checkResult = helperSpy.repoKeyExceedsSoftLimit(repoKey, RepoType.Docker);
            assertNotNull(checkResult);
            assertEquals(expRes, checkResult.softLimitExceeded());
            assertEquals(expMessage, checkResult.details());
        });
    }

    private void runWithMockedAolUtils(boolean aol, Runnable runnable) {
        try (MockedStatic<AolUtils> aolUtilsMockedStatic = Mockito.mockStatic(AolUtils.class)) {
            aolUtilsMockedStatic.when(AolUtils::isAol).thenReturn(aol);
            runnable.run();
        }
    }

    private static Stream<Arguments> provideHardLimitTestArguments() {
        return Stream.of(
                Arguments.of(RepoDetailsType.LOCAL, RepoType.Docker, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.LOCAL, RepoType.Maven, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DEFAULT_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.REMOTE, RepoType.Docker, REMOTE_REPO_KEY_LENGTH_LIMIT,
                        REMOTE_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.REMOTE, RepoType.Generic, REMOTE_REPO_KEY_LENGTH_LIMIT,
                        REMOTE_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.VIRTUAL, RepoType.Docker, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.VIRTUAL, RepoType.Npm, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DEFAULT_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.FEDERATED, RepoType.Docker, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.FEDERATED, RepoType.YUM, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DEFAULT_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.RELEASE_BUNDLE, RepoType.Docker, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DOCKER_GROUP_SAAS_REPO_KEY_LENGTH_LIMIT),
                Arguments.of(RepoDetailsType.RELEASE_BUNDLE, RepoType.BuildInfo, DEFAULT_REPO_KEY_LENGTH_LIMIT,
                        DEFAULT_REPO_KEY_LENGTH_LIMIT)
        );
    }

    private static Stream<Arguments> provideSoftLimitTestArguments() {
        return Stream.of(
                Arguments.of(RepoType.Docker, true, true, true, 57), // 63-5-1=57
                Arguments.of(RepoType.OCI, true, true, true, 57),
                Arguments.of(RepoType.HelmOCI, true, true, true, 57),
                Arguments.of(RepoType.Generic, true, true, true, null), // only Docker-group has soft limit
                Arguments.of(RepoType.Docker, true, true, false, null), // subdomain disabled
                Arguments.of(RepoType.Docker, true, false, true, null), // soft limit feature is disabled
                Arguments.of(RepoType.Docker, false, true, true, null) // on-prem
        );
    }

    private static Stream<Arguments> provideTestServerNameArguments() {
        return Stream.of(
                Arguments.of("http://abcde.jfrog.io/artifactory/", true, "abcde"),
                Arguments.of("https://abcde.jfrog.io/artifactory/", true, "abcde"),
                Arguments.of("https://abcde.jfrog.io/artifactory/", false, ""),
                Arguments.of("abc://abcde.jfrog.io/artifactory/", true, "abcde"),
                Arguments.of("https://abcde.jfrog.io/", true, "abcde"),
                Arguments.of("abcde.jfrog.io", true, ""),
                Arguments.of("", true, ""),
                Arguments.of(null, true, ""),
                Arguments.of("   ", true, "")
        );
    }

    private static Stream<Arguments> provideRepoKeyExceedsSoftLimitTestData() {
        return Stream.of(
                // Soft limit is not applicable to current repoPackageType or Arti deployment type
                Arguments.of(false, false, "repo-key-should-not-matter-in-this-case", false, "No soft limit applied"),
                // soft limit is returned as null, probably a server name was not fetched properly
                Arguments.of(true, true, "repo-key-should-not-matter-in-this-case-also", false,
                        "Soft limit not exceeded"),
                // Valid value of repo key that is shorter than the soft limit
                Arguments.of(true, false, "repo-key-that-is-one-char-shorter-than-the-soft-limit---", false,
                        "Soft limit not exceeded"),
                // Valid value of repo key that is exactly the same length as the soft limit
                Arguments.of(true, false, "repo-key-that-is-equal-to-the-soft-limit-which-is-57-char", false,
                        "Soft limit not exceeded"),
                // Invalid length of repo key that exceeds the soft limit by one char
                Arguments.of(true, false, "repo-key-that-is-one-char-longer-than-the-soft-limit-58-ch", true,
                        "Total length of server name and repository key " +
                                "'abcde-repo-key-that-is-one-char-longer-than-the-soft-limit-58-ch' exceeds the " +
                                "maximum of 63 characters")
        );
    }
}