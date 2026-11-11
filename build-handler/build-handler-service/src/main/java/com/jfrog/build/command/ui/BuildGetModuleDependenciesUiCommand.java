package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.ModuleDependencyModel;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildDependencyService;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetModuleDependenciesUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;
    private final BuildDependencyService dependencyService;

    @NonNull
    public PagingModel<ModuleDependencyModel> getModuleDependencies(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String moduleId,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        List<ModuleDependency> moduleDependencies =
                dependencyService.getModuleDependency(moduleId, buildName, buildNumber, buildStarted, normalisedRepo);
        if (moduleDependencies == null) {
            throw notFoundException("Failed to fetch module dependencies");
        }
        List<ModuleDependencyModel> models = moduleDependencies.stream()
                .map(moduleService::convertToModuleDependencyModel).toList();
        return new PagingModel<>(models.size(), models);
    }
}
