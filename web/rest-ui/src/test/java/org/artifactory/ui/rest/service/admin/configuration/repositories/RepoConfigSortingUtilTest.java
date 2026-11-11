package org.artifactory.ui.rest.service.admin.configuration.repositories;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.RepoConfigSortingUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;

public class RepoConfigSortingUtilTest {

    @ParameterizedTest
    @MethodSource("provideSortingScenarios")
    void buildComparator_sortsCorrectly(String message, RepoConfigRequest request, Map<String, RepoStatDto> repoStats,
                                        List<RepoConfig> input, List<RepoConfig> expected) {
        Comparator<RepoConfig> comparator = RepoConfigSortingUtil.buildComparator(request, repoStats);

        input.sort(comparator);

        Assertions.assertEquals(expected, input, message);
    }

    private static Stream<Arguments> provideSortingScenarios() {
        RepoConfig repo1 = Mockito.mock(RepoConfig.class);
        when(repo1.getKey()).thenReturn("repo1");
        when(repo1.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repo1.getPackageType()).thenReturn(RepoType.Maven);
        when(repo1.getUrl()).thenReturn("http://localhost/repo1");

        RepoConfig repo2 = Mockito.mock(RepoConfig.class);
        when(repo2.getKey()).thenReturn("repo2");
        when(repo2.getRepoType()).thenReturn(RepoDetailsType.REMOTE);
        when(repo2.getPackageType()).thenReturn(RepoType.Alpine);
        when(repo2.getUrl()).thenReturn("http://localhost/repo2");

        RepoStatDto stats1 = RepoStatDto.builder()
                .repoKey("repo1")
                .lastDownloaded(2000)
                .lastUploaded(2000)
                .build();

        RepoStatDto stats2 = RepoStatDto.builder()
                .repoKey("repo2")
                .lastUploaded(10000)
                .build();

        Map<String, RepoStatDto> repoStats = Map.of("repo1", stats1, "repo2", stats2);

        return Stream.of(
                Arguments.of("Sort by repoKey ascending",
                        RepoConfigRequest.builder().sortBy("repoKey").sortDirection("ASC").build(),
                        repoStats,
                        new ArrayList<>(List.of(repo2, repo1)),
                        new ArrayList<>(List.of(repo1, repo2))),
                Arguments.of("Sort by repoType descending",
                        RepoConfigRequest.builder().sortBy("repoType").sortDirection("DESC").build(),
                        repoStats,
                        new ArrayList<>(List.of(repo1, repo2)),
                        new ArrayList<>(List.of(repo2, repo1))),
                Arguments.of("Sort by packageType ascending",
                        RepoConfigRequest.builder().sortBy("packageType").sortDirection("DESC").build(),
                        repoStats,
                        new ArrayList<>(List.of(repo2, repo1)),
                        new ArrayList<>(List.of(repo1, repo2))),
                Arguments.of("Sort by URL descending",
                        RepoConfigRequest.builder().sortBy("url").sortDirection("DESC").build(),
                        repoStats,
                        new ArrayList<>(List.of(repo1, repo2)),
                        new ArrayList<>(List.of(repo2, repo1))),
                Arguments.of("Sort by lastActivity ascending",
                        RepoConfigRequest.builder().sortBy("lastActivity").sortDirection("ASC").build(),
                        repoStats,
                        new ArrayList<>(List.of(repo2, repo1)),
                        new ArrayList<>(List.of(repo1, repo2))),
                Arguments.of("Keep repoKeys order",
                        RepoConfigRequest.builder().keepRepoKeysOrder(true).build(),
                        repoStats,
                        new ArrayList<>(List.of(repo2, repo1)),
                        new ArrayList<>(List.of(repo2, repo1)))
        );
    }
}
