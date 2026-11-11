package com.jfrog.build.command.ui;

import com.jfrog.build.model.BuildProps;
import com.jfrog.build.model.api.diff.BuildParams;
import com.jfrog.build.model.api.BuildPropsModel;
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
public class BuildGetEnvBuildPropsUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;

    @NonNull
    public PagingModel<BuildPropsModel> getEnvBuildProps(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @Nullable String buildRepo) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        BuildParams buildParams = new BuildParams(null, buildNumber, null, null,
                buildStarted, buildName, normalisedRepo);
        buildParams.setIsEnvProps(true);
        List<BuildProps> buildPropsData = moduleService.getBuildPropsData(buildParams);
        List<BuildPropsModel> models = isEmpty(buildPropsData)
                ? List.of()
                : buildPropsData.stream().map(BuildPropsModel::from).toList();
        return new PagingModel<>(0, models);
    }
}
