package com.jfrog.build.service;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRepoDescriptor;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildConstants.BUILD_INFO_REPO_KEY_SUFFIX;
import static com.jfrog.build.acl.util.BuildConstants.PACKAGE_TYPE_BUILD_INFO;
import static com.jfrog.build.acl.util.BuildConstants.REPO_TYPE_FEDERATED;
import static com.jfrog.build.acl.util.BuildConstants.REPO_TYPE_LOCAL;
import static com.jfrog.build.util.BuildServiceUtils.isDefaultProject;
import static com.jfrog.build.util.BuildServiceUtils.isUnassignedContext;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jfrog.access.common.EnvironmentsConstants.DEFAULT_ENVIRONMENT;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;
import static org.jfrog.access.common.ResourceType.REPO;

@Slf4j
@RequiredArgsConstructor
public class BuildRepoService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final Map<String, String> projectToBuildRepos = new ConcurrentHashMap<>();

    private Set<String> allBuildsRepositories = Set.of();

    public void clearProjectRepoMapping() {
        projectToBuildRepos.clear();
    }

    public void clearCaches() {
        log.debug("Restarting the Build Info repositories map");
        projectToBuildRepos.clear();
        refreshAllBuildsRepositories();
    }

    @VisibleForTesting
    public void refreshAllBuildsRepositories() {
        Set<String> repoTypes = Set.of(REPO_TYPE_LOCAL, REPO_TYPE_FEDERATED);
        allBuildsRepositories = bridgeToArtifactory.getRepoKeys(repoTypes, PACKAGE_TYPE_BUILD_INFO);
    }

    public Set<String> getAllBuildInfoRepos() {
        return new HashSet<>(allBuildsRepositories);
    }

    public boolean isBuildInfoRepo(String repoKey) {
        return allBuildsRepositories.contains(repoKey);
    }

    public boolean isOnlyDefaultBuildRepoExists() {
        return getAllBuildInfoRepos().size() == 1;
    }

    @NonNull
    public String getBuildInfoRepoKeyByProject(@Nullable String projectKey) {
        return getBuildInfoRepoKeyByProject(projectKey, true);
    }

    @NonNull
    public String getBuildInfoRepoForDefaultProject() {
        return getBuildInfoRepoKeyByProject(DEFAULT_PROJECT);
    }

    public String getBuildInfoRepoKeyByProject(
            @Nullable String projectKey,
            boolean checkProjectExists) {
        if (isDefaultProject(projectKey)) {
            projectKey = DEFAULT_PROJECT;
        }
        final String finalProject = projectKey;
        String repoKey =
                projectToBuildRepos.computeIfAbsent(projectKey,
                        s -> extractBuildInfoRepoKeyFromRepos(finalProject, checkProjectExists));
        log.debug("Build-info repository of project {} is {}", projectKey, repoKey);
        return repoKey;
    }

    @NonNull
    public String normaliseBuildRepoName(@Nullable String buildRepo) {
        if (isBlank(buildRepo)) {
            return getBuildInfoRepoForDefaultProject();
        }
        return buildRepo;
    }

    @NonNull
    public String getPreferredBuildRepo(
            @Nullable String buildRepo,
            @Nullable String projectKey) {
        return isBlank(buildRepo) ? getBuildInfoRepoKeyByProject(projectKey) : normaliseBuildRepoName(buildRepo);
    }

    /**
     * Resolves the build-info repository to search in:
     * <ul>
     *     <li>an explicit {@code buildRepo} (normalised),</li>
     *     <li>the default build-info repo for an unassigned/empty project context,</li>
     *     <li>the project's build-info repo for an explicit project,</li>
     *     <li>{@code null} when neither is given - meaning search across all build-info repos.</li>
     * </ul>
     */
    @Nullable
    public String getBuildRepoForSearch(
            @Nullable String buildRepo,
            @Nullable String projectKey) {
        if (isNotBlank(buildRepo)) {
            return normaliseBuildRepoName(buildRepo);
        }
        if (isNotBlank(projectKey)) {
            return isUnassignedContext(projectKey)
                    ? getBuildInfoRepoForDefaultProject()
                    : getBuildInfoRepoKeyByProject(projectKey, true);
        }
        return null;
    }

    public String createProjectRelatedBuildInfoRepo(String projectKey) {
        if (isDefaultProject(projectKey)) {
            return BUILDINFO_REPO_KEY;
        }
        String repoKey = projectKey + BUILD_INFO_REPO_KEY_SUFFIX;
        bridgeToArtifactory.doAsSystem(() -> createBuildInfoRepoIfNotExist(repoKey, projectKey));
        return repoKey;
    }

    @VisibleForTesting
    void createBuildInfoRepoIfNotExist(
            @NonNull String repoKey,
            @NonNull String project) {
        Optional<BuildRepoDescriptor> repoDescOpt = bridgeToArtifactory.getRepoDescriptor(repoKey);
        if (repoDescOpt.isEmpty()) {
            bridgeToArtifactory.createBuildRepo(repoKey);
            upsertBuildInfoRepoToProject(repoKey, project);
            return;
        }
        BuildRepoDescriptor repoDesc = repoDescOpt.get();
        if (PACKAGE_TYPE_BUILD_INFO.equals(repoDesc.getType()) && repoDesc.isLocalOrFederated()) {
            return;
        }
        throw new IllegalArgumentException("Repository key " + repoKey + " is already taken");
    }

    @VisibleForTesting
    void assignBuildInfoRepoToProject(
            @NonNull String repoKey,
            @NonNull String projectKey) {
        bridgeToArtifactory.doAsSystem(() -> upsertBuildInfoRepoToProject(repoKey, projectKey));
    }

    private void upsertBuildInfoRepoToProject(
            @NonNull String repoKey,
            @NonNull String projectKey) {
        ProjectResourceEntity resource = buildProjectResourceForBuildInfoRepo(repoKey);
        bridgeToArtifactory.upsertProjectResource(projectKey, REPO, resource, true);
    }

    @NonNull
    private static ProjectResourceEntity buildProjectResourceForBuildInfoRepo(@NonNull String repoKey) {
        return ProjectResourceEntity.builder()
                .environment(DEFAULT_ENVIRONMENT)
                .resourceName(repoKey)
                .build();
    }

    private String extractBuildInfoRepoKeyFromRepos(
            @Nullable String projectKey,
            boolean checkProjectExists) {
        String prefix = StringUtils.EMPTY;
        if (isDefaultProject(projectKey)) {
            prefix = "artifactory";
        } else if (checkProjectExists) {
            projectKey = isBlank(projectKey) ? DEFAULT_PROJECT : projectKey;
            Optional<ProjectSlimEntity> projectSpec = bridgeToArtifactory.getProjectSpec(projectKey);
            if (projectSpec.isPresent()) {
                prefix = projectSpec.get().getProjectKey();
            }
        } else {
            prefix = projectKey;
        }
        if (isEmpty(prefix)) {
            throw new IllegalArgumentException("Project " + projectKey + " was not found.");
        }
        final String finalPrefix = prefix;
        log.debug("Looking for build-info repository for project {}.", projectKey);
        String buildRepoKey = getAllBuildInfoRepos().stream()
                .filter(repo -> repo.startsWith(finalPrefix + BUILD_INFO_REPO_KEY_SUFFIX))
                .findAny()
                .orElse(null);
        if (buildRepoKey == null) {
            return createProjectRelatedBuildInfoRepo(projectKey);
        }
        return buildRepoKey;
    }
}
