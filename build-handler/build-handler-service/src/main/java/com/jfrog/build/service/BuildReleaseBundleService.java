package com.jfrog.build.service;

import com.jfrog.bh.acl.producer.model.BundleInputBuild;
import com.jfrog.build.dao.BuildsDao;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

import static com.jfrog.build.acl.util.BuildInfoUtils.parseBuildTime;
import static java.lang.String.format;
import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;

@Slf4j
@RequiredArgsConstructor
public class BuildReleaseBundleService {

    private final BuildsDao buildsDao;

    public void attachReleaseBundle(
            @NonNull BundleInputBuild build,
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version) {
        long startedMillis = parseBuildTime(build.getBuildStarted());
        try {
            long buildId = buildsDao.findBuildId(build.getBuildName(), build.getBuildNumber(), startedMillis);
            if (buildId != 0) {
                buildsDao.insertBuildRelToReleaseBundle(buildId, repoKey, name, version);
            } else {
                log.warn("Skip attaching RB to not found build - {}", formatBuildForLogs(build));
            }
        } catch (SQLException e) {
            String message = format("Failed to attach RB (repo: %s, name: %s, version: %s) to build (%s)",
                    repoKey, name, version, formatBuildForLogs(build));
            throw internalServerException(message, e);
        }
    }

    public void detachReleaseBundle(
            @NonNull String repoKey,
            @NonNull String name,
            @NonNull String version) {
        try {
            buildsDao.deleteBuildRelToReleaseBundle(repoKey, name, version);
        } catch (SQLException e) {
            String message = format("Failed to detach RB (repo: %s, name: %s, version: %s) "
                    + "from builds", repoKey, name, version);
            throw internalServerException(message, e);
        }
    }

    @NonNull
    private String formatBuildForLogs(@NonNull BundleInputBuild build) {
        return format("repo: %s, name: %s, number: %s, started: %s",
                build.getBuildRepo(), build.getBuildName(), build.getBuildNumber(), build.getBuildStarted());
    }
}
