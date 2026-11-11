package org.artifactory.repo.packagetype;

import org.artifactory.repo.vcs.VcsGitProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VcsUrlBuilderTest {

    @ParameterizedTest
    @MethodSource("providers")
    void testResourceDownloadUrl(VcsGitProvider vcsGitProvider,
            String expectedResourceDownloadUrl,
            String expectedReleaseDownloadUrl,
            String expectedRepositoryDownloadUrl) {
        String resourceDownloadUrl = VcsUrlBuilder.resourceDownloadUrl(vcsGitProvider,
                "user", "repo", "file", "branch");
        assertEquals(expectedResourceDownloadUrl, resourceDownloadUrl);

        String releaseDownloadUrl = VcsUrlBuilder.releaseDownloadUrl(vcsGitProvider,
                "user", "repo", "file", "branch");
        assertEquals(expectedReleaseDownloadUrl, releaseDownloadUrl);

        String repoDownloadUrl = VcsUrlBuilder.repositoryDownloadUrl(vcsGitProvider.getRepositoryDownloadUrl(),
                "org", "repo", "file", "branch");
        assertEquals(expectedRepositoryDownloadUrl, repoDownloadUrl);
    }

    public static Stream<Arguments> providers() {
        return Stream.of(
                Arguments.of(VcsGitProvider.GITHUB,
                        "https://api.github.com/repos/user/repo/contents/file?ref=branch",
                        "https://api.github.com/repos/user/repo/branch/file",
                        "org/repo/archive/file.branch"),
                Arguments.of(VcsGitProvider.BITBUCKET,
                        "https://bitbucket.org/user/repo/raw/branch/file",
                        null,
                        "org/repo/get/file.branch"),
                Arguments.of(VcsGitProvider.ARTIFACTORY,
                        null,
                        null,
                        "org/repo/file?ext=branch"),
                Arguments.of(VcsGitProvider.GITLAB,
                        "api/v4/projects/user%2Frepo/repository/files/file/raw?ref=branch",
                        null,
                        "api/v4/projects/org%2Frepo/repository/archive.branch?sha=file")
        );
    }
}
