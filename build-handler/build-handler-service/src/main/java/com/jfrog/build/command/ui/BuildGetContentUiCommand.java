package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.model.api.BuildContentResponse;
import com.jfrog.build.model.api.BuildReleasableModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildReleasableCollector;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetContentUiCommand {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 200;

    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildReleasableCollector releasableCollector;

    @NonNull
    public BuildContentResponse getBuildContent(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildStarted,
            @Nullable String repositoryKey,
            @Nullable String projectKey,
            @Nullable String offset,
            @Nullable String limit,
            @Nullable String searchTerm,
            @Nullable String orderBy,
            boolean orderAsc) {
        String buildRepo = repoService.getPreferredBuildRepo(repositoryKey, projectKey);
        BuildWrapper buildWrapper = getBuildWrapper(buildName, buildNumber, buildStarted, buildRepo);
        if (buildWrapper == null || !buildWrapper.isBuildExist()) {
            throw notFoundException("Could not find build '%s' #%s", buildName, buildNumber);
        }

        List<BuildReleasableModel> releasables = releasableCollector.collect(buildWrapper, buildRepo);
        if (isNotBlank(searchTerm)) {
            releasables = releasables.stream()
                    .filter(releasable -> releasable.getName() != null && releasable.getName().contains(searchTerm))
                    .toList();
        }
        long total = releasables.size();
        releasables = sortReleasables(releasables, orderBy, orderAsc);
        releasables = paginate(releasables, parsePositiveInt(offset, 0),
                Math.min(parsePositiveInt(limit, DEFAULT_LIMIT), MAX_LIMIT));

        return BuildContentResponse.builder()
                .releasables(releasables)
                .total(total)
                .build();
    }

    @Nullable
    private BuildWrapper getBuildWrapper(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildStarted,
            @NonNull String buildRepo) {
        if (isNotBlank(buildStarted)) {
            BuildRun buildRun = readService.getBuildRun(buildName, buildNumber, buildStarted, buildRepo);
            return buildRun == null ? null : readService.getBuild(buildRun);
        }
        return readService.getLatestBuildByNameAndNumber(buildName, buildNumber, buildRepo);
    }

    @NonNull
    private List<BuildReleasableModel> sortReleasables(
            @NonNull List<BuildReleasableModel> releasables,
            @Nullable String orderBy,
            boolean ascending) {
        Comparator<BuildReleasableModel> comparator = "name".equalsIgnoreCase(orderBy)
                ? Comparator.comparing(BuildReleasableModel::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                : Comparator.comparing(BuildReleasableModel::getPackageType,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        if (!ascending) {
            comparator = comparator.reversed();
        }
        return releasables.stream().sorted(comparator).toList();
    }

    @NonNull
    private List<BuildReleasableModel> paginate(@NonNull List<BuildReleasableModel> releasables, int offset, int limit) {
        if (offset >= releasables.size()) {
            return List.of();
        }
        return releasables.subList(offset, Math.min(offset + limit, releasables.size()));
    }

    private int parsePositiveInt(@Nullable String value, int defaultValue) {
        if (isNotBlank(value)) {
            try {
                int parsed = Integer.parseInt(value);
                return parsed >= 0 ? parsed : defaultValue;
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
