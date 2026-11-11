package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.ModuleArtifact;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.model.api.ModuleArtifactModel;
import com.jfrog.build.model.api.PagingModel;
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
public class BuildGetArtifactDiffUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;

    @NonNull
    public PagingModel<ModuleArtifactModel> getArtifactsDiff(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String moduleId,
            @Nullable String comparedBuildNumber,
            @Nullable String comparedBuildDate,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        BuildParams buildParams = new BuildParams(moduleId, buildNumber, comparedBuildNumber, comparedBuildDate,
                buildStarted, buildName, normalisedRepo);
        List<ModuleArtifact> moduleArtifacts = moduleService.getModuleArtifactsForDiffWithPaging(buildParams);
        List<ModuleArtifactModel> models = isEmpty(moduleArtifacts) ? List.of()
                : moduleArtifacts.stream().map(moduleService::convertToModuleArtifactModel).toList();
        return new PagingModel<>(0, models);
    }
}
