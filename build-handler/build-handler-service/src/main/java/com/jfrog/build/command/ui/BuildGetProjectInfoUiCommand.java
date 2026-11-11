package com.jfrog.build.command.ui;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.model.api.ProjectBuildsMetadata;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import static com.jfrog.build.util.BuildServiceUtils.isDefaultProject;
import static com.jfrog.build.util.BuildServiceUtils.normalizeProjectKey;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.forbiddenException;

@RequiredArgsConstructor
public class BuildGetProjectInfoUiCommand {

    private static final String NOT_SUPPORTED_FOR_EDGE = "Feature is not supported on edge server.";

    private final BuildBridgeToArtifactory bridgeToArtifactory;
    private final BuildDatabaseService databaseService;
    private final BuildRepoService repoService;

    @NonNull
    public ProjectBuildsMetadata getProjectInfo(@Nullable String projectKey) {
        if (bridgeToArtifactory.isEdgeInstance() && !isDefaultProject(projectKey)) {
            throw badRequestException(NOT_SUPPORTED_FOR_EDGE);
        }
        projectKey = normalizeProjectKey(projectKey);
        if (!bridgeToArtifactory.isProjectAdmin(projectKey)) {
            throw forbiddenException("Unauthorized");
        }
        String buildRepoKey = repoService.getBuildInfoRepoKeyByProject(projectKey, true);
        int buildsCount = databaseService.getBuildsCountFromSpecificRepo(buildRepoKey);
        return ProjectBuildsMetadata.builder()
                .buildInfoRepo(buildRepoKey)
                .buildsCount(buildsCount)
                .build();
    }
}
