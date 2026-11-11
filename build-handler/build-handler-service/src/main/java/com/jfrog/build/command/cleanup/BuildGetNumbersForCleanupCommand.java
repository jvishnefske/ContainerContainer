package com.jfrog.build.command.cleanup;

import com.jfrog.build.model.BuildNumberBundleRecord;
import com.jfrog.build.model.BuildNumberRecord;
import com.jfrog.build.model.api.cleanup.BuildBundleEntry;
import com.jfrog.build.model.api.cleanup.BuildNumberEntry;
import com.jfrog.build.model.api.cleanup.BuildNumbersResponse;
import com.jfrog.build.model.api.BuildNumbersModel;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildProjectService;
import com.jfrog.build.service.BuildRepoService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;

@Slf4j
@RequiredArgsConstructor
public class BuildGetNumbersForCleanupCommand {

    private static final long BUILD_NUMBERS_LIMIT_MAX = 5000;
    private static final long BUILD_NUMBERS_LIMIT_DEFAULT = 1000;
    private static final long BUILD_NUMBERS_BUNDLES_LIMIT_MAX = 1000;
    private static final long BUILD_NUMBERS_BUNDLES_LIMIT_DEFAULT = 20;

    private final BuildRepoService repoService;
    private final BuildProjectService projectService;
    private final BuildDatabaseService databaseService;

    @NonNull
    public BuildNumbersResponse getBuildNumbers(@NonNull BuildNumbersModel numbers) {
        validateNumbersModel(numbers);
        log.info("Received build numbers request: {}", numbers);
        Long bundlesLimit = numbers.getReleaseBundlesLimit();
        String repoKey = repoService.getBuildInfoRepoKeyByProject(numbers.getProject(), false);
        List<BuildNumberRecord> buildNumbers = databaseService.getBuildNumbers(
                repoKey,
                numbers.getBuildName(),
                numbers.getStartedBefore(),
                isTrue(numbers.getExcludeInReleaseBundles()),
                isNull(numbers.getStartedAfter()) ? 0 : numbers.getStartedAfter(),
                isNull(numbers.getLimit()) ? BUILD_NUMBERS_LIMIT_DEFAULT : numbers.getLimit(),
                isNull(bundlesLimit) ? BUILD_NUMBERS_BUNDLES_LIMIT_DEFAULT : bundlesLimit
        );
        List<BuildNumberEntry> buildNumbersEntries = buildNumbers.stream().map(this::newBuildNumberEntry).toList();
        long nextStartedAfter = buildNumbersEntries.isEmpty() ? 0 : buildNumbersEntries.getLast().getStarted();
        return new BuildNumbersResponse(repoKey, numbers.getBuildName(), buildNumbersEntries, nextStartedAfter);
    }

    private void validateNumbersModel(@NonNull BuildNumbersModel numbers) {
        if (isBlank(numbers.getBuildName())) {
            throw badRequestException("Name is required");
        }
        if (isBlank(numbers.getProject())) {
            throw badRequestException("Project is required");
        }
        if (numbers.getStartedBefore() == null) {
            throw badRequestException("Started before is required");
        }
        Long limit = numbers.getLimit();
        if (limit != null && (limit < 1 || limit > BUILD_NUMBERS_LIMIT_MAX)) {
            throw badRequestException("Limit must be between 1 and %d", BUILD_NUMBERS_LIMIT_MAX);
        }
        Long bundlesLimit = numbers.getReleaseBundlesLimit();
        if (bundlesLimit != null && (bundlesLimit < 1 || bundlesLimit > BUILD_NUMBERS_BUNDLES_LIMIT_MAX)) {
            throw badRequestException("Bundles limit must be between 1 and %d", BUILD_NUMBERS_BUNDLES_LIMIT_MAX);
        }
        projectService.verifyProjectAdmin(numbers.getProject());
    }

    @NonNull
    private BuildNumberEntry newBuildNumberEntry(@NonNull BuildNumberRecord buildNumber) {
        return new BuildNumberEntry(
                buildNumber.getBuildNumber(),
                buildNumber.getStarted(),
                buildNumber.getCreatedBy(),
                buildNumber.getBundles().stream().map(this::newBuildBundleEntry).toList()
        );
    }

    @NonNull
    private BuildBundleEntry newBuildBundleEntry(@NonNull BuildNumberBundleRecord bundle) {
        return new BuildBundleEntry(
                projectService.extractProjectKeyFromReleaseBundleRepoKey(bundle.getBundleRepo()),
                bundle.getBundleName(),
                bundle.getBundleVersion()
        );
    }
}
