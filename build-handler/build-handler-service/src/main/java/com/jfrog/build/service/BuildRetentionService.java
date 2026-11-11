package com.jfrog.build.service;

import com.google.common.collect.Iterables;
import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.api.BuildRetentionWorkItem;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.exception.http.NotFoundException;
import org.jfrog.build.api.BuildRetention;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.jfrog.build.model.BuildRunComparators.getBuildStartDateComparator;
import static com.jfrog.build.util.BuildServiceUtils.convertToLocalDate;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;

@Slf4j
@RequiredArgsConstructor
public class BuildRetentionService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildDeletionService deletionService;
    private final BuildPermissionService permissionService;
    private final BuildReadService readService;
    private final BuildSysConfigService sysConfigService;

    public MutableStatusHolder discardOldBuilds(
            String buildName,
            String buildRepo,
            BuildRetention buildRetention,
            boolean async) {
        MutableStatusHolder status = bridgeToArtifactory.newStatusHolder();
        if (!sysConfigService.isBuildRetentionEnabled()) {
            log.debug("Unable to execute Build Retention for build '{}' from '{}' repo. Build Retention is disabled.",
                    buildName, buildRepo);
            status.setActivateLogging(false);
            status.warn("Unable to execute Build Retention for build '" + buildName +
                    "'. Build Retention is disabled.", SC_BAD_REQUEST, log);
            return status;
        }
        if (buildRetention.getMinimumBuildDate() != null) {
            discardOldBuildsByDate(buildName, buildRepo, buildRetention, status, async);
        }
        if (buildRetention.getCount() > -1) {
            discardOldBuildsByCount(buildName, buildRepo, buildRetention, status, async);
        }
        return status;
    }

    private void discardOldBuildsByDate(
            String buildName,
            String buildRepo,
            BuildRetention buildRetention,
            MutableStatusHolder status,
            boolean async) {
        LocalDate minimumBuildDate = convertToLocalDate(buildRetention.getMinimumBuildDate());
        log.debug("Discarding builds older than {}", minimumBuildDate);
        if (!permissionService.canDeleteBuildName(buildName, buildRepo)) {
            warnUserNotPermittedToDeleteBuilds(buildName, buildRepo);
            return;
        }
        Set<BuildRun> buildRuns = readService.searchBuildsByNameInternally(buildName, buildRepo);
        for (BuildRun buildRun : buildRuns) {
            log.debug("Checking if build '{}' '{}' from {} repo should be discarded by date",
                    buildRun.getName(), buildRun.getNumber(), buildRun.getBuildRepo());
            if (isBuildPromotedOrPinned(buildRetention, buildRun)) {
                continue;
            }
            if (convertToLocalDate(buildRun.getStartedDate()).isBefore(minimumBuildDate)) {
                log.info("Removing build '{}' that was started at '{}' from {} repo",
                        buildRun.getName(), buildRun.getStarted(), buildRun.getBuildRepo());
                doDeleteBuild(buildRun, status, buildRetention.isDeleteBuildArtifacts(), async);
            }
        }
        log.debug("Done discarding old builds by date");
    }

    private void discardOldBuildsByCount(
            String buildName,
            String buildRepo,
            BuildRetention discard,
            MutableStatusHolder status,
            boolean async) {
        if (discard.getCount() < 0) {
            log.debug("Negative count received '{}', ignoring retention count", discard.getCount());
            return;
        }
        log.debug("Discarding old build by count ({})", discard.getCount());
        if (!permissionService.canDeleteBuildName(buildName, buildRepo)) {
            warnUserNotPermittedToDeleteBuilds(buildName, buildRepo);
            return;
        }
        Set<BuildRun> buildRuns = readService.searchBuildsByNameInternally(buildName, buildRepo);
        if (buildRuns.size() < discard.getCount()) {
            log.debug("Found {} build runs which is less than {}. Not discarding any builds",
                    buildRuns.size(), discard.getCount());
            return;
        }
        List<BuildRun> discardCandidates = newArrayList();
        for (BuildRun candidate : buildRuns) {
            log.debug("Checking if build '{}' '{}' from {} repo should be discarded by count",
                    candidate.getName(), candidate.getNumber(), candidate.getBuildRepo());
            if (isBuildPromotedOrPinned(discard, candidate)) {
                continue;
            }
            discardCandidates.add(candidate);
        }
        Comparator<BuildRun> comparator = Collections.reverseOrder(getBuildStartDateComparator());
        Collections.sort(discardCandidates, comparator);
        Iterable<BuildRun> buildInfosToDiscard = Iterables.skip(discardCandidates, discard.getCount());
        for (BuildRun buildRun : buildInfosToDiscard) {
            log.info("Removing build '{}' #'{}' from {} repo",
                    buildRun.getName(), buildRun.getNumber(), buildRun.getBuildRepo());
            doDeleteBuild(buildRun, status, discard.isDeleteBuildArtifacts(), async);
        }
        log.debug("Done discarding old builds by count");
    }

    private void warnUserNotPermittedToDeleteBuilds(String buildName, String buildRepo) {
        log.warn("User '{}' is not permitted to delete builds under {} : {}. Skipping deletion of old builds.",
                bridgeToArtifactory.getCurrentUsername(), buildRepo, buildName);
    }

    private void doDeleteBuild(
            @NonNull BuildRun build,
            @NonNull MutableStatusHolder status,
            boolean deleteArtifacts,
            boolean async) {
        if (async) {
            deletionService.deleteBuildAsync(new BuildRetentionWorkItem(build, deleteArtifacts));
            return;
        }
        try {
            // retention is the only flow allowed to delete the build-info JSON idempotently,
            // tolerating a concurrent republish (overwrite) that already removed it.
            deletionService.deleteBuild(build, status, deleteArtifacts, true);
        } catch (NotFoundException e) {
            log.info("No build to discard: {}/{} from {}", build.getName(), build.getNumber(), build.getBuildRepo());
        } catch (Exception e) {
            String name = build.getName();
            String number = build.getNumber();
            String repo = build.getBuildRepo();
            String message = format("Failed deleting build %s/%s from %s: %s", name, number, repo, e.getMessage());
            status.error(message, e, log);
            log.error(message, e);
        }
    }

    private boolean isBuildPromotedOrPinned(BuildRetention buildRetention, BuildRun buildRun) {
        if (buildToDiscardWasPromoted(buildRun)) {
            log.info("Build '{}' '{}' from {} repo will not be discarded since it has been promoted in it's past.",
                    buildRun.getName(), buildRun.getNumber(), buildRun.getBuildRepo());
            return true;
        }
        if (buildToDiscardIsPinned(buildRun, buildRetention)) {
            log.info("Build '{}' '{}' from {} repo will not be discarded since it is pinned", buildRun.getName(),
                    buildRun.getNumber(), buildRun.getBuildRepo());
            return true;
        }
        return false;
    }

    private boolean buildToDiscardIsPinned(BuildRun buildRun, BuildRetention buildRetention) {
        return buildRetention.getBuildNumbersNotToBeDiscarded().contains(buildRun.getNumber());
    }

    private boolean buildToDiscardWasPromoted(BuildRun buildRun) {
        return isNotBlank(buildRun.getReleaseStatus());
    }
}
