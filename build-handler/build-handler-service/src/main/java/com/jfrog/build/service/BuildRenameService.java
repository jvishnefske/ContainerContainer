package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.tx.BuildTxRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.async.Async;
import org.jfrog.build.api.Build;

import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.PROP_BUILD_NAME;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.Strings.CS;
import static org.artifactory.exception.factory.ExceptionFactory.notFoundException;

@Slf4j
@RequiredArgsConstructor
public class BuildRenameService {

    private final BuildBridgeToArtifactory bridgeToArtifactory;

    private final BuildTxRunner txRunner;
    private final BuildReadService readService;
    private final BuildCreationService creationService;
    private final BuildDeletionService deletionService;
    private final BuildPermissionService permissionService;

    /**
     * Renames structure, content and properties of build info objects in an asynchronous manner.
     */
    @Async(transactional = true)
    public void renameBuildsAsync(String from, String to, String buildRepo) {
        renameBuilds(from, to, buildRepo);
        try {
            bridgeToArtifactory.renameProperty(PROP_BUILD_NAME, from, to);
        } catch (Exception e) {
            log.error("Failed to Rename artifact build name properties from '{}' to '{}'.", from, to);
        }
    }

    public void renameBuilds(
            String from,
            String to,
            String buildRepo) {
        Set<BuildRun> buildsToRename = readService.searchBuildsByNameInternally(from, buildRepo);
        if (buildsToRename.isEmpty()) {
            log.error("Could not find builds by the name '{}'. No builds were renamed.", from);
            return;
        }
        Set<BuildRun> allowedBuildsToRename = buildsToRename.stream()
                .filter(build -> permissionService.canReadBuild(
                        build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo()))
                .filter(build -> permissionService.canDeleteBuild(
                        build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo()))
                .filter(build -> permissionService.canUploadBuild(
                        build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo()))
                .filter(build -> !readService.isImmutableBuild(build.getName(), build.getNumber(), build.getStarted()))
                .collect(toSet());
        if (allowedBuildsToRename.size() != buildsToRename.size() && log.isErrorEnabled()) {
            log.error("The user: '{}' lacks the required permissions to rename build '{}' from build-info repo {}",
                    bridgeToArtifactory.getCurrentUsername(), from, buildRepo);
        }
        for (BuildRun buildToRename : buildsToRename) {
            try {
                txRunner.runTx(() -> renameBuild(buildToRename, to));
                log.info(
                        "Renamed build number '{}' #{} at build-info repository '{}' from '{}' to '{}'.",
                        buildToRename.getNumber(), buildToRename.getStarted(), buildRepo, buildToRename.getName(), to);
            } catch (Exception e) {
                log.error("Failed to rename build: '{}' #{} from '{}' that started at {}: {}", buildToRename.getName(),
                        buildToRename.getNumber(), buildRepo, buildToRename.getStarted(), e.getMessage());
                log.error("", e);
            }
        }
    }

    private void renameBuild(
            BuildRun buildRun,
            String to) {
        BuildWrapper buildWrapper = readService.getBuildInternally(buildRun);
        if (buildWrapper == null || !buildWrapper.isBuildExist()) {
            throw notFoundException("Cannot rename non existent build %s", buildRun);
        }
        if (shouldRename(buildRun, to, buildWrapper.getBuild())) {
            deletionService.deleteBuild(buildRun, bridgeToArtifactory.newStatusHolder(), false);
            creationService.createBuild(buildWrapper.getBuild(), buildWrapper.getBuildRepo());
        }
    }

    private boolean shouldRename(
            BuildRun buildRun,
            String to,
            Build buildJson) {
        boolean changed = false;
        if (!CS.equals(buildJson.getName(), to)) {
            buildJson.setName(to);
            changed = true;
        }
        if (!CS.equals(buildRun.getName(), to)) {
            changed = true;
        }
        if (!changed) {
            log.info("Build {} already named {} nothing to do!", buildRun, to);
        }
        return changed;
    }
}
