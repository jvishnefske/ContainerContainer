package com.jfrog.build.command;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;

import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

import static com.jfrog.build.util.BuildServiceUtils.shouldDecodeParams;
import static java.lang.String.format;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static org.artifactory.exception.factory.ExceptionFactory.cancelException;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildDeleteCommand {

    private final BuildRepoService repoService;
    private final BuildReadService readService;
    private final BuildDeletionService deletionService;

    @NonNull
    public String deleteBuilds(
            @NonNull HttpServletRequest request,
            @Nullable String buildName,
            @Nullable String buildRepo,
            @Nullable String projectKey,
            @Nullable List<String> buildNumbers,
            boolean deleteArtifacts,
            boolean deleteAllBuilds) {
        if (isBlank(buildName)) {
            throw badRequestException("Please state the name of the build to be removed");
        }
        if (!deleteAllBuilds && isEmpty(buildNumbers)) {
            throw badRequestException("Please provide at least one build number to delete");
        }
        if (shouldDecodeParams(request)) {
            buildName = decode(buildName, UTF_8);
        }
        buildRepo = repoService.getPreferredBuildRepo(buildRepo, projectKey);
        if (deleteAllBuilds) {
            return deleteBuildByName(buildName, buildRepo, deleteArtifacts);
        }

        Set<BuildRun> builds = readService.searchBuildsByNameAndNumbersInternal(buildName, buildNumbers, buildRepo);
        if (builds.isEmpty()) {
            if (!readService.buildNameExists(buildName, buildRepo)) {
                throw notFoundException("Unable to find build '%s'", buildName);
            }
            throw notFoundException("Unable to find the given build numbers");
        }
        return deleteBuildsByNumbers(buildName, buildNumbers, builds, deleteArtifacts);
    }

    @NonNull
    private String deleteBuildByName(
            @NonNull String buildName,
            @NonNull String buildRepo,
            boolean deleteArtifacts) {
        MutableStatusHolder status = deletionService.newStatusHolder();
        deletionService.deleteAllBuildsByName(buildName, buildRepo, status, deleteArtifacts);
        verifyDeletionResults(buildName, status, new StringBuilder());
        return format("All builds '%s' under '%s' have been deleted successfully", buildName, buildRepo);
    }

    @NonNull
    private String deleteBuildsByNumbers(
            @NonNull String buildName,
            @NonNull List<String> buildNumbers,
            @NonNull Set<BuildRun> builds,
            boolean deleteArtifacts) {
        int initialCount = buildNumbers.size();
        MutableStatusHolder status = deletionService.newStatusHolder();
        String messageDeleted = deleteBuildsByNumbers(buildName, buildNumbers, builds, status, deleteArtifacts);
        if (buildNumbers.size() == initialCount) {
            throw notFoundException("Unable to find the given build numbers");
        }
        StringBuilder message = new StringBuilder(messageDeleted);
        processNonDeletedBuildNumbers(buildNumbers, message);
        verifyDeletionResults(buildName, status, message);
        return message.toString();
    }

    @NonNull
    private String deleteBuildsByNumbers(
            @NonNull String buildName,
            @NonNull List<String> buildNumbers,
            @NonNull Set<BuildRun> builds,
            @NonNull MutableStatusHolder status,
            boolean deleteArtifacts) {
        StringJoiner message = new StringJoiner(", ", "The following builds have been deleted successfully: ", ".\n");
        for (BuildRun existingBuild : builds) {
            String buildNumber = existingBuild.getNumber();
            if (buildNumbers.contains(buildNumber)) {
                deletionService.deleteBuild(existingBuild, status, deleteArtifacts);
                buildNumbers.remove(buildNumber);
                message.add("'" + buildName + "#" + buildNumber + "'");
            }
        }
        return message.toString();
    }

    private void processNonDeletedBuildNumbers(
            @NonNull List<String> buildNumbers,
            @NonNull StringBuilder message) {
        if (!buildNumbers.isEmpty()) {
            StringJoiner warn = new StringJoiner(", ", "Warning - the following builds could not be removed: ", ".\n");
            buildNumbers.forEach(buildNumber -> warn.add("'" + buildNumber + "'"));
            message.append(warn);
        }
    }

    private void verifyDeletionResults(
            @NonNull String buildName,
            @NonNull MutableStatusHolder status,
            @NonNull StringBuilder message) {
        verifyNoDeletionError(status);
        verifyNoDeletionWarning(status, message);
        deletionService.logBuildDeleteAccessEvent(buildName);
    }

    private void verifyNoDeletionWarning(
            @NonNull MutableStatusHolder status,
            @NonNull StringBuilder message) {
        if (status.isWarning()) {
            throw internalServerException(message.append(
                    "Warnings have been produced while removing certain builds. "
                            + "Please review the system logs for further information").toString());
        }
    }

    private void verifyNoDeletionError(@NonNull MutableStatusHolder status) {
        if (status.isError()) {
            int statusCode = status.getErrors().getFirst().getStatusCode();
            throw cancelException("Errors have occurred while removing certain builds. "
                    + "Please review the system logs for further information", statusCode);
        }
    }
}
