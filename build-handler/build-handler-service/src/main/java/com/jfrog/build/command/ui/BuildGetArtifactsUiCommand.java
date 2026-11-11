package com.jfrog.build.command.ui;

import com.jfrog.build.model.BuildNodeRecord;
import com.jfrog.build.model.api.BuildArtifactModel;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetArtifactsUiCommand {

    private final BuildRepoService repoService;
    private final BuildModuleService moduleService;

    @NonNull
    public List<BuildArtifactModel> getBuildArtifacts(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @Nullable String buildRepo,
            @Nullable String publishedEpoch) {
        String normalisedRepo = repoService.normaliseBuildRepoName(buildRepo);
        List<BuildNodeRecord> artifacts = isNotBlank(publishedEpoch)
                ? moduleService.getBuildArtifacts(buildName, buildNumber, normalisedRepo, parseEpoch(publishedEpoch))
                : moduleService.getBuildArtifacts(buildName, buildNumber, normalisedRepo);
        return artifacts.stream().map(BuildArtifactModel::new).toList();
    }

    private long parseEpoch(@NonNull String epoch) {
        try {
            return Long.parseLong(epoch);
        } catch (NumberFormatException e) {
            throw badRequestException("Published epoch is not a number");
        }
    }
}
