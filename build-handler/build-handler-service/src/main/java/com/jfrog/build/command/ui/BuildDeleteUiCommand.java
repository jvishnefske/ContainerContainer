package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildCoordinate;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.exception.http.ForbiddenException;

import java.util.List;
import java.util.stream.Collectors;

import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@RequiredArgsConstructor
public class BuildDeleteUiCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildDeletionService deletionService;
    private final BuildPermissionService permissionService;

    @NonNull
    public String deleteBuilds(@Nullable List<BuildCoordinate> buildsCoordinates) {
        if (isEmpty(buildsCoordinates)) {
            return EMPTY;
        }
        MutableStatusHolder status = deletionService.newStatusHolder();
        for (BuildCoordinate coordinate : buildsCoordinates) {
            deleteSpecificBuild(coordinate, status);
        }
        return buildResultMessage(buildsCoordinates, status);
    }

    private void deleteSpecificBuild(
            @NonNull BuildCoordinate coordinate,
            @NonNull MutableStatusHolder status) {
        String buildName = coordinate.getBuildName();
        String buildNumber = coordinate.getBuildNumber();
        String buildRepo = repoService.normaliseBuildRepoName(coordinate.getBuildRepo());
        String buildStarted = formatBuildTime(coordinate.getDate());
        try {
            if (isBlank(buildNumber) && isBlank(buildStarted)) {
                deletionService.deleteAllBuildsByName(List.of(coordinate), status, false);
            } else {
                permissionService.assertDeletePermissions(buildName, buildNumber, buildStarted, buildRepo);
                log.debug("Trying to delete build {}/{}/{}-{}", buildRepo, buildName, buildNumber, buildStarted);
                BuildRun buildRun = readService.getBuildRun(buildName, buildNumber, buildStarted, buildRepo);
                deletionService.deleteBuild(buildRun, status, false);
            }
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            status.error(format("Exception occurred while deleting build '%s' #%s", buildName, buildNumber), e, log);
        }
    }

    @NonNull
    private String buildResultMessage(
            @NonNull List<BuildCoordinate> buildsCoordinates,
            @NonNull MutableStatusHolder status) {
        if (status.isError()) {
            return status.getErrors().stream()
                    .map(StatusEntry::getMessage)
                    .collect(Collectors.joining("\n"));
        }
        if (buildsCoordinates.size() > 1) {
            return "Successfully removed " + buildsCoordinates.size() + " builds";
        }
        BuildCoordinate coordinate = buildsCoordinates.getFirst();
        return "Successfully removed " + coordinate.getBuildName() + " #" + coordinate.getBuildNumber() + " build";
    }
}
