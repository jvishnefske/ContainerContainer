/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.jfrog.build.acl.util;

import com.jfrog.build.acl.model.BuildCoordinate;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildRun;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.build.api.Build;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jfrog.build.acl.encode.BuildRepoPathEncoder.encode;
import static org.artifactory.exception.factory.ExceptionFactory.badRequestException;
import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildConstants.BUILD_INFO_REPO_KEY_SUFFIX;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;

/**
 * Utility class to work with build info common operation.
 *
 * @author Yossi Shaul
 */
public abstract class BuildInfoUtils {

    private static final DateTimeFormatter BUILD_FORMATTER = DateTimeFormat.forPattern(Build.STARTED_FORMAT);
    private static final Pattern BUILD_COORDINATES_PATTERN = Pattern.compile("^(.*)\\/(.*)-([\\d]*)\\.json$");

    /**
     * @param time Time in millis to format
     * @return Formatted time using the {@link Build#STARTED_FORMAT} time format (ISO time format).
     */
    public static String formatBuildTime(long time) {
        return BUILD_FORMATTER.print(time);
    }

    /**
     * @param time Time in millis to format
     * @return Formatted time using the {@link Build#STARTED_FORMAT} time format (ISO time format).
     */
    public static String formatBuildTime(String time) {
        return BUILD_FORMATTER.print(Long.parseLong(time));
    }

    /**
     * @param buildTimeFormat Build time with {@link Build#STARTED_FORMAT} format
     * @return Time in millis represented by the string
     */
    public static long parseBuildTime(String buildTimeFormat) {
        return BUILD_FORMATTER.parseDateTime(buildTimeFormat).getMillis();
    }

    /**
     * Returns the project key of the associated build info repo accepted parameter
     * @param projectBuildInfoRepoKey the build info repo, formatted as <u>projectKey</u>-build-info
     * @return the projectKey if <u>projectBuildInfoRepoKey</u> is a properly formatted build info repo name, empty
     * string otherwise.
     */
    public static String getAssociatedProjectKey(String projectBuildInfoRepoKey) {
        if (isBlank(projectBuildInfoRepoKey)) {
            return StringUtils.EMPTY;
        }
        if (!projectBuildInfoRepoKey.contains("-build-info")) {
            return StringUtils.EMPTY;
        }
        return projectBuildInfoRepoKey.split("-build-info")[0];
    }

    /**
     * Note that the default project is identified with the `startsWith` and not `equals`,
     * because during the introduction of Build-Info package type, some customers already
     * had the `artifactory-build-info` repository (see RTDEV-7355).
     */
    @NonNull
    public static String getProjectKeyFromBuildInfoRepoKey(@NonNull String repoKey) {
        return repoKey.startsWith(BUILDINFO_REPO_KEY) ? DEFAULT_PROJECT
                : repoKey.split(BUILD_INFO_REPO_KEY_SUFFIX)[0];
    }

    @NonNull
    public static BuildRepoPath generateBuildJsonRepoPath(@NonNull BuildRun build) {
        validateBuildRef(build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo());
        return generateBuildJsonRepoPath(build.getName(), build.getNumber(), build.getStarted(), build.getBuildRepo());
    }

    @NonNull
    public static BuildRepoPath generateBuildJsonRepoPath(Build build, String repoKey) {
        validateBuildRef(build.getName(), build.getNumber(), build.getStarted(), repoKey);
        return generateBuildJsonRepoPath(build.getName(), build.getNumber(), build.getStarted(), repoKey);
    }

    /**
     * Generates a repo path for a {@link Build} model according to its coordinates in Build Info repo layout.
     * Converts Build start time from ISO8601 date formatted (2018-04-03T12:00:13.862+0300) to millis
     */
    @NonNull
    public static BuildRepoPath generateBuildJsonRepoPath(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted,
            @NonNull String buildRepo) {
        String path = createBuildPath(buildName, buildNumber, buildStarted);
        return new BuildRepoPath(buildRepo, path);
    }

    private static void validateBuildRef(
            @Nullable String buildName,
            @Nullable String buildNumber,
            @Nullable String buildStarted,
            @Nullable String buildRepo) {
        if (isBlank(buildName) || isBlank(buildNumber) || isBlank(buildStarted)) {
            throw badRequestException("Build model must contain build name, number and started timestamp");
        }
        if (isBlank(buildRepo)) {
            throw badRequestException("Build JSON must have a Build-Info repoKey");
        }
    }

    @NonNull
    private static String createBuildPath(
            @NonNull String buildName,
            @NonNull String buildNumber,
            @NonNull String buildStarted) {
        String dirName = encode(buildName, false);
        String fileName = encode(buildNumber, true) + "-" + parseBuildTime(buildStarted) + ".json";
        return dirName + "/" + fileName;
    }

    @Nullable
    public static BuildCoordinate extractCoordinatesFromPath(
            @NonNull String buildCoordinates,
            @NonNull String buildRepo) {
        Matcher matcher = BUILD_COORDINATES_PATTERN.matcher(buildCoordinates);
        if (matcher.matches()) {
            String buildName = matcher.group(1);
            String buildNumber = matcher.group(2);
            long buildTime = Long.parseLong(matcher.group(3));
            return new BuildCoordinate(buildName, buildNumber, buildTime, buildRepo);
        }
        return null;
    }
}
