package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildFileInfo;
import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.model.BuildProps;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.acl.model.api.BuildGeneralInfo;
import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.model.api.BaseArtifact;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.ModuleDependencyModel;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Build;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static com.jfrog.build.acl.util.BuildInfoUtils.generateBuildJsonRepoPath;
import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static java.math.RoundingMode.UP;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Slf4j
@RequiredArgsConstructor
public class BuildModuleService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildDatabaseService databaseService;
    private final BuildPermissionService permissionService;
    private final BuildReadService readService;
    private final BuildRepoService repoService;

    @Nullable
    public List<PublishedModule> getPublishedModules(
            String buildNumber,
            String buildStarted,
            String buildName,
            String buildRepo) {
        permissionService.assertBasicReadPermissions(buildName, buildNumber, formatBuildTime(buildStarted), buildRepo);
        return databaseService.getPublishedModules(buildName, buildNumber, buildStarted, buildRepo);
    }

    @Nullable
    public List<ModuleArtifact> getModuleArtifact(
            String buildName,
            String buildNumber,
            String moduleId,
            String buildStarted,
            String buildRepo) {
        permissionService.assertBasicReadPermissions(buildName, buildNumber, formatBuildTime(buildStarted), buildRepo);
        boolean onlyDefaultBuildRepoExists = repoService.isOnlyDefaultBuildRepoExists();
        return databaseService.getModuleArtifact(buildName, buildNumber, moduleId, buildStarted, buildRepo,
                onlyDefaultBuildRepoExists);
    }

    @NonNull
    public List<BuildNodeRecord> getBuildArtifacts(
            String buildName,
            String buildNumber,
            String buildRepo) {
        permissionService.assertBasicReadPermissions(buildName, buildRepo);
        return databaseService.getBuildArtifacts(buildName, buildNumber, buildRepo);
    }

    @NonNull
    public List<BuildNodeRecord> getBuildArtifacts(
            String buildName,
            String buildNumber,
            String buildRepo,
            long publishedEpoch) {
        permissionService.assertBasicReadPermissions(buildName, buildRepo);
        return databaseService.getBuildArtifacts(buildName, buildNumber, buildRepo, publishedEpoch);
    }

    public List<ModuleArtifact> getModuleArtifactsForDiffWithPaging(BuildParams buildParams) {
        permissionService.assertBasicReadPermissions(buildParams.getBuildName(), buildParams.getCurrBuildNum(),
                formatBuildTime(buildParams.getCurrBuildDate()), buildParams.getBuildRepo());
        return databaseService.getModuleArtifactsForDiffWithPaging(buildParams,
                repoService.isOnlyDefaultBuildRepoExists());
    }

    public List<GeneralBuild> getPrevBuildsList(
            String buildName,
            String buildDate,
            String buildRepo) {
        return databaseService.getPrevBuildsList(buildName, buildDate, buildRepo).stream()
                .filter(generalBuild ->
                        permissionService.isBuildBasicRead(generalBuild.getBuildName(), generalBuild.getBuildNumber(),
                                formatBuildTime(generalBuild.getBuildDate()), generalBuild.getBuildRepo()))
                .collect(Collectors.toList());
    }

    public List<BuildProps> getBuildPropsData(BuildParams buildParams) {
        String name = buildParams.getBuildName();
        String number = buildParams.getCurrBuildNum();
        String started = formatBuildTime(buildParams.getCurrBuildDate());
        String buildRepo = buildParams.getBuildRepo();
        permissionService.assertReadPermissions(name, number, started, buildRepo);
        return databaseService.getBuildPropsData(buildParams);
    }

    public BuildGeneralInfo getBuildGeneralInfo(
            String buildName,
            String buildNumber,
            String buildStarted,
            @NonNull String buildRepo) {
        BuildRun buildRun = readService.getBuildRunNonStrict(buildName, buildNumber, buildStarted, buildRepo);
        BuildWrapper buildWrapper = null;
        if (buildRun != null) {
            buildWrapper = readService.getBuildInternally(buildRun);
        }
        if (buildWrapper != null && buildWrapper.isBuildExist()) {
            permissionService.assertBasicReadPermissions(
                    buildWrapper.getBuild().getName(),
                    buildWrapper.getBuild().getNumber(),
                    buildWrapper.getBuild().getStarted(),
                    buildWrapper.getBuildRepo()
            );
        } else {
            log.warn("Build {}:{}-{} from {} repo was not found.", buildName, buildNumber, buildStarted, buildRepo);
            return null;
        }
        Build build = buildWrapper.getBuild();
        String buildAgent = (build.getBuildAgent() == null) ? null : build.getBuildAgent().toString();
        String agent = (build.getAgent() == null) ? null : build.getAgent().toString();
        String foundBuildName = build.getName();
        String foundBuildNumber = build.getNumber();
        String foundBuildStarted = build.getStarted();
        return BuildGeneralInfo.builder()
                .buildName(foundBuildName)
                .lastBuildTime(foundBuildStarted)
                .agent(agent)
                .buildAgent(buildAgent)
                .artifactoryPrincipal(build.getArtifactoryPrincipal())
                .principal(build.getPrincipal())
                .duration(getDuration(build.getDurationMillis()))
                .buildNumber(buildNumber)
                .time(parseBuildTime(build.getStarted()))
                .url(build.getUrl())
                .isBuildFullView(permissionService.canReadBuild(
                        foundBuildName, foundBuildNumber, foundBuildStarted, buildRepo))
                .canManage(permissionService.canManageBuild(
                        foundBuildName, foundBuildNumber, foundBuildStarted, buildRepo))
                .canDelete(permissionService.canDeleteBuild(
                        foundBuildName, foundBuildNumber, foundBuildStarted, buildRepo))
                .buildRepo(buildWrapper.getBuildRepo())
                .sha256(getBuildSha256(buildRun, buildRepo).orElse(null))
                .vcs(build.getVcs())
                .build();
    }

    @NonNull
    private Optional<String> getBuildSha256(
            @NonNull BuildRun buildRun,
            @NonNull String buildRepo) {
        String bn = buildRun.getName();
        String num = buildRun.getNumber();
        String started = buildRun.getStarted();
        BuildRepoPath repoPath = generateBuildJsonRepoPath(bn, num, started, buildRepo);
        BuildFileInfo fileInfo = bridgeToArtifactory.getFileInfo(repoPath.toString());
        return ofNullable(fileInfo.getSha2());
    }

    @NonNull
    private String getDuration(long durationMill) {
        int minutes = (int) ((durationMill / (1000 * 60)) % 60);
        int hours = (int) ((durationMill / (1000 * 60 * 60)) % 24);
        if (hours > 0) {
            BigDecimal duration = BigDecimal.valueOf(durationMill / (1000.0 * 60.0 * 60.0)).setScale(1, UP);
            return duration + " hours";
        }
        if (minutes > 0) {
            BigDecimal duration = BigDecimal.valueOf(durationMill / (1000.0 * 60.0)).setScale(1, UP);
            return duration + " minutes";
        }
        BigDecimal duration = BigDecimal.valueOf(durationMill / 1000.0).setScale(1, UP);
        return duration + " seconds";
    }

    @NonNull
    public ModuleArtifactModel convertToModuleArtifactModel(@NonNull ModuleArtifact artifact) {
        String repoKey = artifact.getRepoKey();
        String path = artifact.getPath();
        return ModuleArtifactModel.builder()
                .repoKey(repoKey)
                .path(resolveRelativePath(repoKey, path))
                .name(artifact.getName())
                .type(artifact.getType())
                .moduleName(artifact.getModule())
                .status(artifact.getStatus())
                .repoPath(resolveRepoPath(repoKey, path))
                .actions(resolveArtifactActions(repoKey, path))
                .build();
    }

    @NonNull
    public ModuleDependencyModel convertToModuleDependencyModel(@NonNull ModuleDependency dependency) {
        String repoKey = dependency.getRepoKey();
        String path = dependency.getPath();
        return ModuleDependencyModel.builder()
                .repoKey(repoKey)
                .path(resolveRelativePath(repoKey, path))
                .name(dependency.getName())
                .type(dependency.getType())
                .scope(dependency.getScope())
                .moduleName(dependency.getModule())
                .status(dependency.getStatus())
                .repoPath(resolveRepoPath(repoKey, path))
                .actions(resolveArtifactActions(repoKey, path))
                .build();
    }

    @Nullable
    private String resolveRepoPath(
            @Nullable String repoKey,
            @Nullable String relativePath) {
        return isNotBlank(repoKey) ? repoKey + ":" + relativePath : null;
    }

    @Nullable
    private String resolveRelativePath(
            @Nullable String repoKey,
            @Nullable String relativePath) {
        return isNotBlank(repoKey) ? relativePath : "No path found (externally resolved or deleted/overwritten)";
    }

    @NonNull
    private List<BaseArtifact> resolveArtifactActions(
            @Nullable String repoKey,
            @Nullable String relativePath) {
        return isBlank(repoKey) || isNull(relativePath) ? List.of()
                : permissionService.getArtifactActions(repoKey, relativePath)
                .stream().map(BaseArtifact::new)
                .toList();
    }
}
