package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class BuildGetPrevBuildsUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;

    @NonNull
    public List<GeneralBuildInfo> getPrevBuilds(
            @NonNull String buildName,
            @NonNull String buildDate,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        return moduleService.getPrevBuildsList(buildName, buildDate, normalisedRepo).stream()
                .map(this::toGeneralBuildInfo)
                .toList();
    }

    @NonNull
    private GeneralBuildInfo toGeneralBuildInfo(@NonNull GeneralBuild build) {
        return GeneralBuildInfo.builder()
                .buildNumber(build.getBuildNumber())
                .buildName(build.getBuildName())
                .buildStat(build.getStatus())
                .time(build.getBuildDate())
                .build();
    }
}
