package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import lombok.experimental.UtilityClass;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@UtilityClass
public class RepoConfigSortingUtil {

    public static Comparator<RepoConfig> buildComparator(RepoConfigRequest request,
                                                         Map<String, RepoStatDto> repoStats) {
        if (request.isKeepRepoKeysOrder()) {
            return keepRepoKeysOrderComparator(request.getRepoKeys());
        }
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "repoKey";
        String sortDirection = request.getSortDirection() != null ? request.getSortDirection() : "DESC";

        Comparator<RepoConfig> comparator = switch (sortBy) {
            case "repoType" -> compareBy(repo -> repo.getRepoType().typeNameLowercase());
            case "packageType" -> compareBy(repo -> repo.getPackageType().getDisplayName());
            case "url" -> compareByUrl();
            case "lastActivity" -> compareByLastActivity(repoStats);
            default -> compareBy(RepoConfig::getKey);
        };
        return "DESC".equalsIgnoreCase(sortDirection) ? comparator.reversed() : comparator;
    }

    private static Comparator<RepoConfig> compareByLastActivity(Map<String, RepoStatDto> repoStats) {
        return Comparator.comparing(repo -> getLastActivity(repo, repoStats));
    }

    private static Comparator<RepoConfig> compareByUrl() {
        return Comparator.comparing(RepoConfig::getUrl, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static long getLastActivity(RepoConfig repo, Map<String, RepoStatDto> repoStats) {
        RepoStatDto stats = repoStats.get(repo.getKey());
        if (stats == null) {
            return 0;
        }
        return Math.max(stats.getLastDownloaded(), stats.getLastUploaded());
    }

    private static Comparator<RepoConfig> compareBy(Function<RepoConfig, String> keyExtractor) {
        return Comparator.comparing(keyExtractor, String.CASE_INSENSITIVE_ORDER);
    }

    private static Comparator<RepoConfig> keepRepoKeysOrderComparator(List<String> repoKeys) {
        if (CollectionUtils.isNullOrEmpty(repoKeys)) {
            return (o1, o2) -> 0;
        }
        Map<String, Integer> keyOrderMap = IntStream.range(0, repoKeys.size())
                .boxed()
                .collect(Collectors.toMap(repoKeys::get, Function.identity()));
        return Comparator.comparing(repo -> keyOrderMap.getOrDefault(repo.getKey(), Integer.MAX_VALUE));
    }
}