package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildCoordinate;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;

import java.util.List;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildDeleteAllUiCommand {

    private final BuildRepoService repoService;
    private final BuildDeletionService deletionService;

    @NonNull
    public String deleteAllBuilds(@Nullable List<BuildCoordinate> buildsCoordinates) {
        if (isEmpty(buildsCoordinates)) {
            throw badRequestException("Invalid request, non build names were specified");
        }
        normaliseBuildsCoordinates(buildsCoordinates);
        MutableStatusHolder status = deletionService.newStatusHolder();
        deletionService.deleteAllBuildsByName(buildsCoordinates, status, false);
        return resultMessage(buildsCoordinates);
    }

    private void normaliseBuildsCoordinates(@NonNull List<BuildCoordinate> buildsCoordinates) {
        for (BuildCoordinate buildCoordinate : buildsCoordinates) {
            buildCoordinate.setBuildRepo(repoService.normaliseBuildRepoName(buildCoordinate.getBuildRepo()));
        }
    }

    @NonNull
    private String resultMessage(@NonNull List<BuildCoordinate> buildsCoordinates) {
        return buildsCoordinates.size() > 1 ? "Successfully removed " + buildsCoordinates.size() + " build projects"
                : "Successfully removed " + buildsCoordinates.getFirst().getBuildName() + " build project";
    }
}
