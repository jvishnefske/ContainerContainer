package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleDependency;
import com.jfrog.build.model.api.diff.BuildParams;
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

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Slf4j
@RequiredArgsConstructor
public class BuildGetDependencyDiffUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;
    private final BuildDependencyService dependencyService;

    @NonNull
    public PagingModel<ModuleDependencyModel> getDependencyDiff(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String moduleId,
            @Nullable String comparedBuildNumber,
            @Nullable String comparedBuildDate,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        BuildParams buildParams = new BuildParams(
                moduleId,
                buildNumber,
                comparedBuildNumber,
                comparedBuildDate,
                buildStarted,
                buildName,
                normalisedRepo
        );
        List<ModuleDependency> moduleDependencies = dependencyService.getModuleDependencyForDiffWithPaging(buildParams);
        List<ModuleDependencyModel> models = isEmpty(moduleDependencies) ? List.of()
                : moduleDependencies.stream().map(moduleService::convertToModuleDependencyModel).toList();
        return new PagingModel<>(0, models);
    }
}
