package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.BuildRepositoryKeyModel;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;

@Slf4j
@RequiredArgsConstructor
public class BuildGetRepositoryKeyUiCommand {

    private final BuildRepoService repoService;

    @NonNull
    public BuildRepositoryKeyModel getBuildInfoRepositoryKey(@Nullable String projectKey) {
        String resolvedProjectKey = isBlank(projectKey) ? DEFAULT_PROJECT : projectKey;
        try {
            String buildInfoRepo = repoService.getBuildInfoRepoKeyByProject(resolvedProjectKey, true);
            return new BuildRepositoryKeyModel(buildInfoRepo);
        } catch (IllegalStateException e) {
            throw notFoundException("%s", e.getMessage());
        }
    }
}
