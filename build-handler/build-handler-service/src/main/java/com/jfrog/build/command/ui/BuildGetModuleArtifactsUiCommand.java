package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static java.util.Objects.isNull;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetModuleArtifactsUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;

    @NonNull
    public PagingModel<ModuleArtifactModel> getModuleArtifacts(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String moduleId,
            @Nullable String buildRepo) {
        validateIsNumber(buildStarted);
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        List<ModuleArtifact> moduleArtifacts = moduleService.getModuleArtifact(
                buildName, buildNumber, moduleId, buildStarted, normalisedRepo
        );
        if (isNull(moduleArtifacts)) {
            throw notFoundException("Failed to fetch module artifacts");
        }
        List<ModuleArtifactModel> models = moduleArtifacts.stream()
                .map(moduleService::convertToModuleArtifactModel).toList();
        return new PagingModel<>(0, models);
    }

    private void validateIsNumber(@NonNull String buildStarted) {
        try {
            Long.parseLong(buildStarted);
        } catch (NumberFormatException e) {
            throw badRequestException("Build started is not a number");
        }
    }
}
