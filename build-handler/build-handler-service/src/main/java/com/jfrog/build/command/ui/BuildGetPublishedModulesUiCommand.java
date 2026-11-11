package com.jfrog.build.command.ui;

import com.jfrog.build.model.api.PublishedModule;
import com.jfrog.build.model.api.BuildModule;
import com.jfrog.build.model.api.PagingModel;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class BuildGetPublishedModulesUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;

    @Nullable
    public PagingModel<BuildModule> getPublishedModules(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        List<PublishedModule> modules =
                moduleService.getPublishedModules(buildNumber, buildStarted, buildName, normalisedRepo);
        if (modules == null) {
            return null;
        }
        List<BuildModule> buildModules = modules.stream().map(BuildModule::new).toList();
        return new PagingModel<>(0, buildModules);
    }
}
