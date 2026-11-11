package com.jfrog.build.aql;

import com.jfrog.build.acl.model.BuildArtifactAqlRow;
import com.jfrog.build.acl.model.BuildWrapper;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.api.internal.AqlBase.AndClause;
import org.artifactory.aql.result.AqlEagerResult;
import org.artifactory.aql.result.rows.AqlBaseFullRowImpl;
import org.jfrog.build.api.Build;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_NAME;
import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_NUMBER;
import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_TIMESTAMP;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.artifactory.aql.model.AqlComparatorEnum.equals;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings({"unchecked", "rawtypes"})
public class BuildAqlArtifacts {

    private final AqlService aqlService;

    @NonNull
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumberTimestamp(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        Build build = buildWrapper.getBuild();
        String buildName = build.getName();
        String buildNumber = build.getNumber();
        String startedTimestamp = String.valueOf(parseBuildTime(build.getStarted()));
        log.debug("Run AQL artifacts search for build {}:{}:{}", buildName, buildNumber, startedTimestamp);
        AndClause and = createAqlForBuildArtifactsByNameNumber(buildName, buildNumber, sourceRepos, excludedRepos);
        and.append(AqlApiItem.property().property(PROP_BUILD_TIMESTAMP, equals, startedTimestamp));
        AqlBase buildArtifactsQuery = AqlApiItem.create().filter(and);
        AqlEagerResult<AqlBaseFullRowImpl> result = aqlService.executeQueryEager(buildArtifactsQuery);
        log.debug("AQL artifacts search for build {}:{}:{} returned {} items",
                buildName, buildNumber, startedTimestamp, result.getResults().size());
        return convertAqlRowToFileInfos(result.getResults());
    }

    @NonNull
    public List<BuildArtifactAqlRow> getBuildArtifactsByNameNumber(
            @NonNull BuildWrapper buildWrapper,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        Build build = buildWrapper.getBuild();
        String buildName = build.getName();
        String buildNumber = build.getNumber();
        log.debug("Run AQL artifacts search for build {}:{} without timestamp", buildName, buildNumber);
        AndClause andFallback = createAqlForBuildArtifactsByNameNumber(
                buildName, buildNumber, sourceRepos, excludedRepos
        );
        AqlBase buildArtifactsQuery = AqlApiItem.create().filter(andFallback);
        AqlEagerResult<AqlBaseFullRowImpl> result = aqlService.executeQueryEager(buildArtifactsQuery);
        log.debug("AQL artifacts search for build {}:{} without timestamp returned {} items",
                buildName, buildNumber, result.getResults().size()
        );
        return convertAqlRowToFileInfos(result.getResults());
    }

    @NonNull
    private AndClause createAqlForBuildArtifactsByNameNumber(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable List<String> sourceRepos,
            @Nullable List<String> excludedRepos) {
        AndClause and = AqlBase.and();
        if (isNotEmpty(sourceRepos)) {
            addIncludedRepos(and, sourceRepos);
        }
        if (isNotEmpty(excludedRepos)) {
            addExcludedRepos(and, excludedRepos, sourceRepos);
        }
        and.append(AqlApiItem.property().property(PROP_BUILD_NAME, equals, buildName));
        and.append(AqlApiItem.property().property(PROP_BUILD_NUMBER, equals, buildNumber));
        return and;
    }

    private void addIncludedRepos(
            @NonNull AndClause and,
            @NonNull List<String> sourceRepos) {
        log.debug("Search limited to repos: {}", sourceRepos);
        sourceRepos.stream()
                .filter(StringUtils::isNotBlank)
                .forEach(sourceRepo -> {
                    and.append(AqlApiItem.repo().equal(sourceRepo));
                });
    }

    private void addExcludedRepos(
            @NonNull AndClause and,
            @NonNull List<String> excludedRepos,
            @Nullable List<String> sourceRepos) {
        log.debug("Search excludes repos: {}", excludedRepos);
        excludedRepos.stream()
                .filter(StringUtils::isNotBlank)
                .forEach(excludedRepo -> {
                    if (isNull(sourceRepos) || !sourceRepos.contains(excludedRepo)) {
                        and.append(AqlApiItem.repo().notEquals(excludedRepo));
                    }
                });
    }

    @NonNull
    private List<BuildArtifactAqlRow> convertAqlRowToFileInfos(@NonNull List<AqlBaseFullRowImpl> rows) {
        return rows.stream()
                .map(this::convertAqlRowToFileInfo)
                .toList();
    }

    @NonNull
    private BuildArtifactAqlRow convertAqlRowToFileInfo(@NonNull AqlBaseFullRowImpl row) {
        return new BuildArtifactAqlRow(
                row.getNodeId(),
                row.getRepo(),
                row.getPath(),
                row.getName(),
                row.getCreatedBy(),
                row.getModifiedBy(),
                row.getOriginalMd5(),
                row.getActualMd5(),
                row.getOriginalSha1(),
                row.getActualSha1(),
                row.getSha2(),
                row.getSize(),
                row.getCreated(),
                row.getModified(),
                row.getUpdated()
        );
    }
}
