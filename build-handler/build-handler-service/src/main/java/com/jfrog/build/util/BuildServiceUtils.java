package com.jfrog.build.util;

import com.jfrog.build.acl.model.BuildRepoDescriptor;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.artifactory.exception.http.BadRequestException;

import java.time.LocalDate;
import java.util.Date;

import static com.jfrog.build.acl.util.BuildConstants.PACKAGE_TYPE_RB;
import static com.jfrog.build.acl.util.BuildConstants.TRASH_REPO_KEY;
import static java.time.ZoneId.systemDefault;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.Strings.CS;
import static org.apache.http.HttpHeaders.USER_AGENT;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;

@Slf4j
@UtilityClass
public class BuildServiceUtils {

    public static boolean isDefaultProject(@Nullable String projectKey) {
        return isBlank(projectKey) || DEFAULT_PROJECT.equalsIgnoreCase(projectKey);
    }

    @NonNull
    public static String normalizeProjectKey(@Nullable String projectKey) {
        return isBlank(projectKey) ? DEFAULT_PROJECT : projectKey;
    }

    public static boolean isUnassignedContext(@Nullable String projectKey) {
        return isBlank(projectKey) || "unassigned".equalsIgnoreCase(projectKey);
    }

    /**
     * Rejects build coordinates whose name or number resolves to a hidden/system path segment (i.e. begins
     * with a '.'), for example a build named ".jfrog". Such a name would be stored under a hidden directory in
     * the build-info repository ({@code artifactory-build-info/.jfrog/<number>-<started>.json}), evading normal
     * visibility and authorization and enabling ingestion of synthetic build records (RTDEV-94003).
     * <p>
     * This is intentionally enforced only on ingestion/publish and is <b>not</b> wired into the build json repo
     * path generation, so that already-stored builds remain readable and deletable (allowing cleanup of any
     * hidden build that may have been planted before this guard).
     */
    public static void validateBuildCoordinatesNotHidden(@Nullable String buildName, @Nullable String buildNumber) {
        assertNotHiddenSegment(buildName, "name");
        assertNotHiddenSegment(buildNumber, "number");
    }

    private static void assertNotHiddenSegment(@Nullable String value, @NonNull String coordinate) {
        // Leading whitespace is stripped first because the path encoder does not encode a leading space,
        // so " .jfrog" would still land under a hidden ".jfrog" segment once trimmed by downstream handling.
        if (value != null && StringUtils.stripStart(value, null).startsWith(".")) {
            throw new BadRequestException("Build " + coordinate + " must not start with '.'");
        }
    }

    /**
     * It is not expected that any build artifacts/dependencies are coming
     * from a Release Bundle or Trash Can repositories.
     */
    public static boolean isRestrictedRepository(@NonNull BuildRepoDescriptor repoDescriptor) {
        return PACKAGE_TYPE_RB.equals(repoDescriptor.getType())
                || TRASH_REPO_KEY.equals(repoDescriptor.getKey());
    }

    /**
     * For backward compatability, if the build info version is less or equals to 2.0.11
     * then we need to decode the request parameters since we used a different encoding technique in the past,
     * otherwise we simply let Jersey do the decoding for us.
     *
     * @return True if we need to manually decode the request params, false otherwise.
     */
    public static boolean shouldDecodeParams(@NonNull HttpServletRequest request) {
        String userAgent = request.getHeader(USER_AGENT);
        // If the request didn't come from build info let Jersey do the work.
        if (isBlank(userAgent) || !userAgent.startsWith("ArtifactoryBuildClient/")) {
            return false;
        }
        String buildInfoVersion = CS.removeStart(userAgent, "ArtifactoryBuildClient/");
        boolean snapshotCondition = CS.contains(buildInfoVersion, "SNAPSHOT");
        DefaultArtifactVersion cutoverVersion = new DefaultArtifactVersion("2.0.11");
        DefaultArtifactVersion currentVersion = new DefaultArtifactVersion(buildInfoVersion);
        boolean newVersionCondition = cutoverVersion.compareTo(currentVersion) < 0;
        // Build info version is SNAPSHOT or newer than 2.0.11 we also let Jersey do the work.
        return !snapshotCondition && !newVersionCondition;
    }

    @NonNull
    public static LocalDate convertToLocalDate(@NonNull Date date) {
        return date.toInstant().atZone(systemDefault()).toLocalDate();
    }
}