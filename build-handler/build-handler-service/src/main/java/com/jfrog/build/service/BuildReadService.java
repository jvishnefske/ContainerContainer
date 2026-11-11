package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.BuildRunComparators;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import com.jfrog.build.dao.BuildsDao;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.Build;
import org.jfrog.common.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Lists.newArrayList;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static java.util.Collections.reverseOrder;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildReadService {

    private static final String LATEST_BUILD = "LATEST";

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildsDao buildsDao;
    private final BuildDatabaseService databaseService;
    private final BuildPermissionService permissionService;
    private final BuildRepoService repoService;

    public BuildRun getBuildRun(
            String buildName,
            String buildNumber,
            String buildStarted,
            @NonNull String buildRepo) {
        BuildRun buildRun = getBuildRunInternally(buildName, buildNumber, buildStarted);
        if (buildRun == null) {
            permissionService.assertBasicReadPermissions(buildName, buildNumber, buildStarted, buildRepo);
        } else {
            permissionService.assertBasicReadPermissions(buildName, buildNumber, buildStarted, buildRun.getBuildRepo());
        }
        return buildRun;
    }

    public BuildRun getBuildRunInternally(
            String buildName,
            String buildNumber,
            String buildStarted) {
        return databaseService.getBuildRun(buildName, buildNumber, buildStarted);
    }

    @Nullable
    public BuildWrapper getBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildStarted,
            @NonNull String buildRepo) {
        if (isBlank(buildStarted)) {
            return getLatestBuildByNameAndNumber(buildName, buildNumber, buildRepo);
        }
        BuildRun buildRun = getBuildRun(buildName, buildNumber, buildStarted, buildRepo);
        return isNull(buildRun) ? null : getBuild(buildRun);
    }

    @Nullable
    public BuildWrapper getBuild(@NonNull BuildRun buildRun) {
        BuildWrapper buildWrapper = getBuildInternally(buildRun);
        if (buildWrapper == null || !buildWrapper.isBuildExist()) {
            permissionService.assertReadPermissions(buildRun.getName(), buildRun.getNumber(), buildRun.getStarted(),
                    repoService.getBuildInfoRepoForDefaultProject());
            return null;
        }
        permissionService.assertReadPermissions(
                buildRun.getName(), buildRun.getNumber(), buildRun.getStarted(), buildWrapper.getBuildRepo());
        return buildWrapper;
    }

    public BuildWrapper getBuildInternally(BuildRun buildRun) {
        if (isBuildModelInvalid(buildRun.getName(), buildRun.getNumber(), buildRun.getStarted())) {
            return null;
        }
        return getBuildAccordingToCoordinates(buildRun);
    }

    public String getBuildAsJson(BuildRun buildRun) {
        if (isBuildModelInvalid(buildRun.getName(), buildRun.getNumber(), buildRun.getStarted())) {
            return null;
        }
        permissionService.assertReadPermissions(
                buildRun.getName(), buildRun.getNumber(), buildRun.getStarted(), buildRun.getBuildRepo());
        BuildWrapper buildWrapper = getBuildAccordingToCoordinates(buildRun);
        return getBuildAsJsonString(buildWrapper.getBuild());
    }

    @NonNull
    public BuildWrapper getBuildAccordingToCoordinates(@NonNull BuildRun build) {
        String buildName = build.getName();
        String buildNumber = build.getNumber();
        String buildStarted = build.getStarted();
        String buildRepo = build.getBuildRepo();
        if (isEmpty(buildRepo)) {
            throw new IllegalArgumentException("Build info file does not exist");
        }
        BuildRepoPath repoPath = generateBuildJsonRepoPath(buildName, buildNumber, buildStarted, buildRepo);
        return getBuildModelFromFile(repoPath);
    }

    @SuppressWarnings("java:S2139")
    public BuildWrapper getBuildModelFromFile(BuildRepoPath buildJsonPath) {
        try (InputStream in = bridgeToArtifactory.getResourceStream(buildJsonPath.toString())) {
            return new BuildWrapper(JsonUtils.getInstance().readValue(in, Build.class), buildJsonPath.getRepoKey());
        } catch (NotFoundException e) {
            throw notFoundException("Build info file does not exist: %s", buildJsonPath);
        } catch (IOException e) {
            log.error("Unable to retrieve build info json from '{}': {}", buildJsonPath, e.getMessage());
            log.debug("Unable to retrieve build info json from '{}'", buildJsonPath, e);
            throw new IllegalStateException("Could not get JSON string for build " + buildJsonPath, e);
        }
    }

    @Nullable
    public BuildWrapper getLatestBuildByNameAndStatus(
            String buildName,
            String buildRepo,
            String buildStatus) {
        if (isBlank(buildName)) {
            return null;
        }
        if (isBlank(buildStatus)) {
            return null;
        }
        Set<BuildRun> buildsByName = searchBuildsByNameInternally(buildName, buildRepo);
        if (buildsByName == null || buildsByName.isEmpty()) {
            return null;
        }
        List<BuildRun> buildRuns = newArrayList(buildsByName);
        buildRuns.sort(BuildRunComparators.getComparatorFor(buildRuns));
        BuildRun latestBuildRun;
        if (buildStatus.equals(LATEST_BUILD)) {
            latestBuildRun = getLast(buildRuns, null);
        } else {
            latestBuildRun = getLast(filter(buildRuns, buildRun ->
                    buildStatus.equals(buildRun.getReleaseStatus())), null);
        }
        return latestBuildRun == null ? null : getBuildInternally(latestBuildRun);
    }

    public boolean canReadBuild(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo) {
        BuildRun buildRun = getLatestBuildRunByNameAndNumberInternally(buildName, buildNumber, buildRepo);
        if (buildRun == null) {
            log.warn("Couldn't find matching build for {}:{} in order to assert permission", buildName, buildNumber);
            return false;
        }
        return permissionService.canReadBuild(buildName, buildNumber, buildRun.getStarted(), buildRun.getBuildRepo());
    }

    public boolean canUploadBuild(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo) {
        BuildRun buildRun = getLatestBuildRunByNameAndNumberInternally(buildName, buildNumber, buildRepo);
        if (buildRun == null) {
            log.warn("Couldn't find matching build for {}:{} in order to assert permission", buildName, buildNumber);
            return false;
        }
        return permissionService.canUploadBuild(buildName, buildNumber, buildRun.getStarted(), buildRun.getBuildRepo());
    }

    @Nullable
    public BuildWrapper getLatestBuildByNameAndNumber(
            String buildName,
            String buildNumber,
            String buildRepo) {
        BuildWrapper buildWrapper = getLatestBuildByNameAndNumberInternally(buildName, buildNumber, buildRepo);
        if (buildWrapper == null || buildWrapper.getBuild() == null) {
            return null;
        }
        permissionService.assertReadPermissions(
                buildName, buildNumber, buildWrapper.getBuild().getStarted(), buildWrapper.getBuildRepo());
        return buildWrapper;
    }

    @Nullable
    public BuildWrapper getLatestBuildByNameAndNumberInternally(
            String buildName,
            String buildNumber,
            String buildRepo) {
        BuildRun latestBuildRun = getLatestBuildRunByNameAndNumberInternally(buildName, buildNumber, buildRepo);
        if (latestBuildRun != null) {
            return getBuildInternally(latestBuildRun);
        }
        return null;
    }

    public BuildRun getLatestBuildRunByNameAndNumberInternally(
            String buildName,
            String buildNumber,
            String repoKey) {
        if (isBlank(buildName) || isBlank(buildNumber) || isBlank(repoKey)) {
            return null;
        }
        return databaseService.getLatestBuildRun(buildName, buildNumber, repoKey);
    }

    @NonNull
    public SortedSet<BuildRun> searchBuildsByName(
            String buildName,
            @NonNull String buildRepo) {
        return searchBuildsByNameInternally(buildName, buildRepo).stream()
                .filter(build -> permissionService.isBuildBasicRead(
                        build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo()))
                .collect(Collectors.toCollection(() -> new TreeSet<>(
                        reverseOrder(BuildRunComparators.getBuildStartDateComparator()))));
    }

    public Set<BuildRun> searchBuildsByNameInternally(
            @Nullable String buildName,
            @NonNull String buildRepo) {
        return databaseService.findBuildsByName(buildName, buildRepo);
    }

    public Set<BuildRun> searchBuildsByNameInternally(@Nullable String buildName) {
        return databaseService.findBuildsByName(buildName);
    }

    public List<String> getBuildNames(String buildRepo) {
        return getBuildsInfoInternally(buildRepo).stream()
                .filter(buildInfo -> permissionService.isBuildBasicRead(buildInfo.getName(), buildInfo.getRepo()))
                .map(BuildNameAndRepo::getName)
                .collect(Collectors.toList());
    }

    public List<BuildNameAndRepo> getBuildsInfoInternally(String buildRepo) {
        return databaseService.getAllBuildsInfo(buildRepo).stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Set<BuildRun> searchBuildsByNameAndNumber(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo) {
        return databaseService.findBuildsByNameAndNumber(buildName, buildNumber, buildRepo).stream()
                .filter(build -> permissionService.isBuildBasicRead(
                        build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo()))
                .collect(Collectors.toSet());
    }

    public Set<BuildRun> searchBuildsByNameAndNumberInternal(
            String buildName,
            String buildNumber,
            @NonNull String buildRepo) {
        return databaseService.findBuildsByNameAndNumber(buildName, buildNumber, buildRepo);
    }

    @NonNull
    public Set<BuildRun> searchBuildsByNameAndNumbersInternal(
            @NonNull String buildName,
            @NonNull Collection<String> buildNumbers,
            @NonNull String buildRepo) {
        return databaseService.findBuildsByNameAndNumbers(buildName, buildNumbers, buildRepo);
    }

    public boolean buildNameExists(@NonNull String buildName, @NonNull String buildRepo) {
        return databaseService.buildNameExists(buildName, buildRepo);
    }

    public void assertMutable(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted) {
        if (isImmutableBuild(buildName, buildNumber, buildStarted)) {
            throw forbiddenException("The build %s:%s is immutable and cannot be renamed", buildName, buildNumber);
        }
    }

    public boolean isImmutableBuild(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String startDate) {
        long startedMillis = parseBuildTime(startDate);
        try {
            return buildsDao.isImmutable(buildName, buildNumber, startedMillis);
        } catch (SQLException e) {
            throw internalServerException("Failed to check if build is immutable", e);
        }
    }

    @Nullable
    public BuildRun getBuildRunNonStrict(
            String buildName,
            String buildNumber,
            @Nullable String buildStarted,
            @NonNull String buildRepo) {
        BuildRun buildRun;
        if (isNotBlank(buildStarted)) {
            buildRun = getBuildRunInternally(buildName, buildNumber, buildStarted);
        } else {
            buildRun = getLatestBuildRunByNameAndNumberInternally(buildName, buildNumber, buildRepo);
        }
        return buildRun;
    }

    public boolean isBuildModelInvalid(
            @Nullable String buildName,
            @Nullable String buildNumber,
            @Nullable String buildStarted) {
        return isBlank(buildName) || isBlank(buildNumber) || isBlank(buildStarted);
    }

    public static String getBuildAsJsonString(Build build) {
        return JsonUtils.getInstance().valueToString(build, true);
    }

    public Stream<ProducedBy> retrieveProducedBuilds(String sha1, String md5) {
        return bridgeToArtifactory.getProducedBuildsByAql(sha1, md5)
                .filter(producedBy -> permissionService.isBuildBasicRead(
                                producedBy.getName(),
                                producedBy.getNumber(),
                                formatBuildTime(producedBy.getStarted()),
                                producedBy.getBuildRepo()
                        )
                );
    }

    public boolean isBuildExistsInDb(BuildWrapper build) {
        return databaseService.exists(build);
    }

    public Stream<UsedBy> retrieveDependantBuilds(String sha1, String md5) {
        return bridgeToArtifactory.getDependentBuildsByAql(sha1, md5)
                .filter(usedBy -> permissionService.isBuildBasicRead(
                                usedBy.getName(),
                                usedBy.getNumber(),
                                formatBuildTime(usedBy.getStarted()),
                                usedBy.getBuildRepo()
                        )
                );
    }
}
