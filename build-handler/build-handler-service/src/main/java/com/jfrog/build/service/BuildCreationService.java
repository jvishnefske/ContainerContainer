package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.tx.BuildTxRunner;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.build.api.Build;
import org.jfrog.build.api.Issue;
import org.jfrog.build.api.Issues;
import org.jfrog.build.api.release.PromotionStatus;
import org.jfrog.common.JsonUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.jfrog.build.acl.model.BuildAccessLogType.BUILD_CREATE;
import static com.jfrog.build.acl.model.BuildAccessLogType.BUILD_CREATE_DENIED;
import static com.jfrog.build.acl.model.BuildPluginType.AFTER_CREATE;
import static com.jfrog.build.acl.model.BuildPluginType.BEFORE_CREATE;
import static com.jfrog.build.acl.model.BuildWorkerEventType.AFTER_BUILD_INFO_SAVE;
import static com.jfrog.build.acl.model.BuildWorkerEventType.BEFORE_BUILD_INFO_SAVE;
import static com.jfrog.build.acl.model.BuildXrayEvent.CREATE_BUILD;
import static com.jfrog.build.acl.util.BuildConstants.BUILD_INFO_REPO_KEY_SUFFIX;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static com.jfrog.build.acl.util.BuildInfoUtils.getProjectKeyFromBuildInfoRepoKey;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static com.jfrog.build.service.BuildReadService.getBuildAsJsonString;
import static com.jfrog.build.util.BuildPerformanceMonitor.initOperationStats;
import static com.jfrog.build.util.BuildPerformanceMonitor.logOperationStats;
import static com.jfrog.build.util.BuildPerformanceMonitor.printOperationStats;
import static com.jfrog.build.util.BuildServiceUtils.isDefaultProject;
import static com.jfrog.build.util.BuildServiceUtils.validateBuildCoordinatesNotHidden;
import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.conflictException;
import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;
import static org.jfrog.access.common.ResourceType.REPO;

@Slf4j
@RequiredArgsConstructor
public class BuildCreationService {

    private static final String LATEST_BUILD = "LATEST";

    private static final JsonUtils JSON_UTILS = JsonUtils.getInstance();

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildTxRunner txRunner;
    private final BuildDatabaseService databaseService;
    private final BuildPermissionService permissionService;
    private final BuildReadService readService;
    private final BuildRepoService repoService;
    private final BuildRetentionService retentionService;
    private final BuildSysConfigService sysConfigService;

    @NonNull
    public String createBuild(
            @NonNull Build build,
            @NonNull String buildRepo) {
        validateBuildCoordinatesNotHidden(build.getName(), build.getNumber());
        try {
            long opStart = nanoTime();
            initOperationStats(build, buildRepo);
            sysConfigService.assertNotArtifactoryColdInstance();
            createBuildRepoIfNeeded(buildRepo);
            String response = txRunner.runSupplierTx(() -> persistBuild(build, buildRepo));
            String sha256 = parseBuildSha256(response);
            logOperationStats("Creation total", opStart);
            return sha256;
        } finally {
            printOperationStats();
        }
    }

    private void createBuildRepoIfNeeded(@NonNull String buildRepoKey) {
        if (!buildRepoKey.contains(BUILD_INFO_REPO_KEY_SUFFIX)) {
            log.error("Illegal Build info repo '{}' name. Repo name is missing '-build-info' suffix.", buildRepoKey);
            throwRepoNotExistException(buildRepoKey);
        }
        String projectKey = getProjectKeyFromBuildInfoRepoKey(buildRepoKey);
        Optional<ProjectSlimEntity> project = bridgeToArtifactory.getProjectSpec(projectKey);
        if (project.isEmpty() && !isDefaultProject(projectKey)) {
            log.error("Project '{}' was not found.", projectKey);
            throwRepoNotExistException(buildRepoKey);
        }
        if (repoService.isBuildInfoRepo(buildRepoKey)) {
            String actualProjectKey = bridgeToArtifactory.getProjectForResource(buildRepoKey, REPO);
            if (isDefaultProject(actualProjectKey) && !isDefaultProject(projectKey)) {
                repoService.assignBuildInfoRepoToProject(buildRepoKey, projectKey);
            }
        } else {
            project.map(ProjectSlimEntity::getProjectKey).ifPresent(repoService::createProjectRelatedBuildInfoRepo);
        }
    }

    private void throwRepoNotExistException(@NonNull String buildRepo) {
        throw new IllegalArgumentException(format("Build info repository '%s' does not exist", buildRepo));
    }

    @NonNull
    private String persistBuild(
            @NonNull Build build,
            @NonNull String buildRepo) {
        preCreateBuild(build, buildRepo);
        String buildJson = getBuildAsJsonString(build);
        BuildRepoPath repoPath = generateBuildJsonRepoPath(build, buildRepo);
        return bridgeToArtifactory.upload(repoPath.toString(), buildJson);
    }

    public void preCreateBuild(
            @NonNull Build build,
            @NonNull String buildRepo) {
        build.setArtifactoryPrincipal(bridgeToArtifactory.getCurrentUsername());
        long opStart = nanoTime();
        databaseService.populateMissingChecksums(build);
        logOperationStats("Population of missing checksums", opStart);
        aggregatePreviousBuildIssues(build, buildRepo);
        opStart = nanoTime();
        bridgeToArtifactory.runPluginInterceptor(BEFORE_CREATE, build, buildRepo);
        logOperationStats("Plugins (before create)", opStart);
        BuildWrapper buildWrapper = new BuildWrapper(build, buildRepo);
        opStart = nanoTime();
        bridgeToArtifactory.runWorkers(BEFORE_BUILD_INFO_SAVE, buildWrapper);
        logOperationStats("Workers (before save)", opStart);
    }

    @Transactional
    public void createBuildInternal(@NonNull BuildWrapper buildWrapper) {
        Build build = buildWrapper.getBuild();
        String name = buildWrapper.getName();
        String number = buildWrapper.getNumber();
        String started = buildWrapper.getStarted();
        String buildRepo = buildWrapper.getBuildRepo();
        validateBuildCoordinatesNotHidden(name, number);
        if (!permissionService.canUploadBuild(name, number, started, buildRepo)) {
            bridgeToArtifactory.logAccessEvent(BUILD_CREATE_DENIED, name + ':' + number);
            throw forbiddenException("User is not authorized to access build info");
        }
        if (databaseService.exists(buildWrapper)) {
            String buildString = format("Build %s:%s-%s at %s", name, number, started, buildRepo);
            String oldBuildRepoKey = databaseService.getBuildStoringRepo(name, number, started);
            if (!buildRepo.equals(oldBuildRepoKey)) {
                throw conflictException("%s already exists in repository %s.", buildString, oldBuildRepoKey);
            }
            throw conflictException("%s already exists.", buildString);
        }
        long opStart = nanoTime();
        databaseService.addBuild(buildWrapper);
        logOperationStats("SQL for creation total", opStart);
        bridgeToArtifactory.logAccessEvent(BUILD_CREATE, name + ':' + number);
        bridgeToArtifactory.runXrayInterceptor(CREATE_BUILD, name, number, parseBuildTime(started), buildRepo);
        if (nonNull(build.getBuildRetention())) {
            opStart = nanoTime();
            retentionService.discardOldBuilds(name, buildRepo, build.getBuildRetention(), false);
            logOperationStats("Retention upon creation", opStart);
        }
        opStart = nanoTime();
        bridgeToArtifactory.runPluginInterceptor(AFTER_CREATE, buildWrapper.getBuild(), buildRepo);
        logOperationStats("Plugins (after create)", opStart);
        opStart = nanoTime();
        bridgeToArtifactory.runWorkers(AFTER_BUILD_INFO_SAVE, buildWrapper);
        logOperationStats("Workers (after save)", opStart);
    }

    private void aggregatePreviousBuildIssues(
            @NonNull Build newBuild,
            @NonNull String buildRepo) {
        Issues newBuildIssues = newBuild.getIssues();
        if (isNull(newBuildIssues) || !newBuildIssues.isAggregateBuildIssues()) {
            return;
        }
        String name = newBuild.getName();
        BuildWrapper latestBuild = readService.getLatestBuildByNameAndStatus(name, buildRepo, LATEST_BUILD);
        if (nonNull(latestBuild) && isBuildWithNewStatus(latestBuild, newBuildIssues)) {
            populatePreviousBuildIssues(latestBuild, newBuildIssues);
        }
    }

    private boolean isBuildWithNewStatus(
            @NonNull BuildWrapper latestBuild,
            @NonNull Issues newBuildIssues) {
        List<PromotionStatus> statuses = latestBuild.getBuild().getStatuses();
        String aggregationBuildStatus = newBuildIssues.getAggregationBuildStatus();
        return isNull(statuses) || statuses.stream().noneMatch(status ->
                status.getStatus().equalsIgnoreCase(aggregationBuildStatus));
    }

    private void populatePreviousBuildIssues(
            @NonNull BuildWrapper latestBuild,
            @NonNull Issues newBuildIssues) {
        Issues previousIssues = latestBuild.getBuild().getIssues();
        if (nonNull(previousIssues)) {
            Set<Issue> affectedIssues = previousIssues.getAffectedIssues();
            if (nonNull(affectedIssues)) {
                for (Issue issue : affectedIssues) {
                    Issue issueToAdd = new Issue(issue.getKey(), issue.getUrl(), issue.getSummary());
                    issueToAdd.setAggregated(true);
                    newBuildIssues.addIssue(issueToAdd);
                }
            }
        }
    }

    @NonNull
    @VisibleForTesting
    String parseBuildSha256(@NonNull String responseJson) {
        try {
            if (isNotBlank(responseJson)) {
                return JSON_UTILS.readTree(responseJson).path("originalChecksums").path("sha256").asText("");
            }
        } catch (Exception e) {
            log.error("Failed to parse SHA256 from internal artifactory response", e);
        }
        return EMPTY;
    }

    public void updateBuild(@NonNull BuildWrapper buildWrapper) {
        Build build = buildWrapper.getBuild();
        String name = build.getName();
        String number = build.getNumber();
        String started = build.getStarted();
        String buildRepo = buildWrapper.getBuildRepo();
        sysConfigService.assertNotArtifactoryColdInstance();
        permissionService.assertDeletePermissions(name, number, started, buildRepo);
        permissionService.assertUploadPermissions(name, number, started, buildRepo);
        log.info("Updating build: {}/{}/{} from repo: {}", name, number, started, buildRepo);
        txRunner.runTx(() -> createBuild(build, buildRepo));
        log.info("Updated build: {}/{}/{} from repo: {}", name, number, started, buildRepo);
    }
}
