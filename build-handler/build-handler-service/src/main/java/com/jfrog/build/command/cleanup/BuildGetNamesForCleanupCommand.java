package com.jfrog.build.command.cleanup;

import com.google.common.annotations.VisibleForTesting;
import com.jfrog.build.acl.model.BuildNameAndRepo;
import com.jfrog.build.model.api.cleanup.BuildNameEntry;
import com.jfrog.build.model.api.cleanup.BuildNamesResponse;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildProjectService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.access.client.project.ProjectEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.jfrog.build.acl.util.BuildConstants.BUILD_INFO_REPO_KEY_SUFFIX;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;

@Slf4j
@RequiredArgsConstructor
public class BuildGetNamesForCleanupCommand {

    private final BuildRepoService repoService;
    private final BuildProjectService projectService;
    private final BuildDatabaseService databaseService;

    @NonNull
    public BuildNamesResponse getBuildNames(
            @Nullable String buildName,
            @Nullable String projectKeysRaw,
            long offset,
            long limit) {
        verifyNameParam(buildName);
        Map<String, ProjectEntity> projectsByKeys = projectService.getProjectsByKeys();
        List<String> repoKeys = getRepoKeysByProjects(projectsByKeys, projectKeysRaw);
        List<BuildNameEntry> names = getBuildNames(repoKeys, projectsByKeys, buildName, offset, limit);
        return new BuildNamesResponse(names);
    }

    private void verifyNameParam(@Nullable String buildName) {
        if (isBlank(buildName)) {
            return;
        }
        if (buildName.startsWith("*")) {
            throw badRequestException("Wildcard is not allowed at the beginning: %s", buildName);
        }
        String buildNameTrimmed = buildName;
        if (buildNameTrimmed.endsWith("*")) {
            buildNameTrimmed = buildNameTrimmed.substring(0, buildNameTrimmed.length() - 1);
        }
        if (isBlank(buildNameTrimmed)) {
            throw badRequestException("Prefix with wildcard must include at least one character: %s", buildName);
        }
    }

    @NonNull
    private List<String> getRepoKeysByProjects(
            @NonNull Map<String, ProjectEntity> projectsByKeys,
            @Nullable String projectKeysRaw) {
        if (isBlank(projectKeysRaw)) {
            projectsByKeys.keySet().forEach(projectService::verifyProjectAdmin);
            return List.of();
        }
        return getBuildInfoRepoKeys(projectKeysRaw, projectsByKeys);
    }

    @NonNull
    private List<String> getBuildInfoRepoKeys(
            @NonNull String projectKeysRaw,
            @NonNull Map<String, ProjectEntity> projectsByKeys) {
        String[] projectKeysArr = projectKeysRaw.split(",");
        List<String> projectKeys = Arrays.stream(projectKeysArr).map(String::trim).toList();
        projectKeys.forEach(projectKey -> verifyProjectExists(projectKey, projectsByKeys));
        projectKeys.forEach(projectService::verifyProjectAdmin);
        return projectKeys.stream()
                .map(projectKey -> repoService.getBuildInfoRepoKeyByProject(projectKey, false))
                .toList();
    }

    private void verifyProjectExists(
            @NonNull String projectKey,
            @NonNull Map<String, ProjectEntity> projectsByKeys) {
        if (!projectsByKeys.containsKey(projectKey)) {
            throw notFoundException("Project does not exist: %s", projectKey);
        }
    }

    @NonNull
    private List<BuildNameEntry> getBuildNames(
            @NonNull List<String> repoKeys,
            @NonNull Map<String, ProjectEntity> projectsByKeys,
            @Nullable String buildName,
            long offset,
            long limit) {
        return databaseService.getBuildNames(repoKeys, buildName, offset, limit).stream()
                .map(nameAndRepo -> getBuildName(nameAndRepo, projectsByKeys))
                .toList();
    }

    @NonNull
    @VisibleForTesting
    BuildNameEntry getBuildName(
            @NonNull BuildNameAndRepo buildNameAndRepo,
            @NonNull Map<String, ProjectEntity> projectsByKeys) {
        String projectKey = convertRepoKeyToProjectKey(buildNameAndRepo.getRepo());
        String projectName = resolveProjectName(projectKey, buildNameAndRepo, projectsByKeys);
        return new BuildNameEntry(
                buildNameAndRepo.getName(),
                buildNameAndRepo.getRepo(),
                projectKey,
                projectName
        );
    }

    @NonNull
    @VisibleForTesting
    String convertRepoKeyToProjectKey(@NonNull String repositoryKey) {
        if (!repositoryKey.contains(BUILD_INFO_REPO_KEY_SUFFIX)) {
            throw badRequestException("Invalid build info repository key: %s", repositoryKey);
        }
        int lastExtIdx = repositoryKey.lastIndexOf(BUILD_INFO_REPO_KEY_SUFFIX);
        String prefix = repositoryKey.substring(0, lastExtIdx);
        if ("artifactory".equals(prefix)) {
            return DEFAULT_PROJECT;
        }
        return prefix;
    }

    @NonNull
    private String resolveProjectName(
            @NonNull String projectKey,
            @NonNull BuildNameAndRepo buildNameAndRepo,
            @NonNull Map<String, ProjectEntity> projectsByKeys) {
        return ofNullable(projectsByKeys.get(projectKey)).map(ProjectEntity::getName).orElseGet(() -> {
            log.info("Retrieving Build {} for deleted project key {}", buildNameAndRepo, projectKey);
            return projectKey;
        });
    }
}
