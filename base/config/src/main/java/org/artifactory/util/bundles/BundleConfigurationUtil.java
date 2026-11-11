package org.artifactory.util.bundles;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.RepoPath;

import java.util.regex.Pattern;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.Strings.CS;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;

@Slf4j
@UtilityClass
public class BundleConfigurationUtil {

    public static final String APP_VERSIONS_REPO = "application-versions";
    public static final String DEFAULT_RBV2_REPO = "release-bundles-v2";
    public static final String DEFAULT_RBV2_DISTRIBUTION_REPO = "release-bundles-v2-jfds";
    public static final String BUNDLE_HANDLER_THREAD_MARKER = "BUNDLE_HANDLER_THREAD_MARKER";
    public static final String BUNDLE_MANIFEST_FILE_NAME = "release-bundle.json.evd";
    public static final String BUNDLE_INTERNAL_EXTENSION = ".json.evd";
    public static final String DRAFT_BUNDLE_INTERNAL_EXTENSION = ".json.draft";

    public static final Pattern BUNDLE_MANIFEST_FILE_NAME_PATTERN =
            Pattern.compile(".+/release-bundle\\.json\\.evd$");
    public static final Pattern DISTRIBUTION_MANIFEST_FILE_NAME_PATTERN =
            Pattern.compile(".+/distribution-(\\w+@\\w+)(?:-\\d+)?\\.json\\.evd$");

    /**
     * Does the key represent a Global ReleaseBundles repository?
     */
    public static boolean isReleaseBundlesV2DefaultRepoKey(@Nullable String repoKey) {
        return nonNull(repoKey) && repoKey.equals(DEFAULT_RBV2_REPO);
    }

    public static boolean isReleaseBundlesV2DistributionRepoKey(@Nullable String repoKey) {
        return nonNull(repoKey) && repoKey.endsWith(DEFAULT_RBV2_DISTRIBUTION_REPO);
    }

    /**
     * Does the key represent the Global (project-less) Application Versions repository?
     * This is the app-versions counterpart of the global "release-bundles-v2" repo and is used
     * on nodes without projects (e.g. Edge) where application versions are stored project-less.
     */
    public static boolean isAppVersionsDefaultRepoKey(@Nullable String repoKey) {
        return nonNull(repoKey) && repoKey.equals(APP_VERSIONS_REPO);
    }

    /**
     * Does the key represent a Global or Project ReleaseBundles repository?
     */
    public static boolean isReleaseBundlesV2RepoKey(@Nullable String repoKey) {
        return isReleaseBundlesV2BaseRepoKey(repoKey) || isReleaseBundlesV2DistributionRepoKey(repoKey);
    }

    public static boolean isReleaseBundlesV2BaseRepoKey(@Nullable String repoKey) {
        return nonNull(repoKey) && (repoKey.endsWith(DEFAULT_RBV2_REPO) || repoKey.endsWith(APP_VERSIONS_REPO));
    }

    @NonNull
    public static String extractProjectKey(@NonNull String bundleRepoKey) {
        if (!isReleaseBundlesV2BaseRepoKey(bundleRepoKey)) {
            throw new IllegalArgumentException("Invalid release bundle v2 repository key: " + bundleRepoKey);
        }
        if (isReleaseBundlesV2DefaultRepoKey(bundleRepoKey) || isAppVersionsDefaultRepoKey(bundleRepoKey)) {
            return DEFAULT_PROJECT;
        }
        String suffix = bundleRepoKey.contains(DEFAULT_RBV2_REPO) ? DEFAULT_RBV2_REPO : APP_VERSIONS_REPO;
        return CS.removeEnd(bundleRepoKey, "-" + suffix);
    }

    public static boolean isHiddenReleaseBundlePath(@Nullable RepoPath repoPath) {
        return nonNull(repoPath) && isHiddenReleaseBundlePath(repoPath.getRepoKey(), repoPath.getPath());
    }

    public static boolean isHiddenReleaseBundlePath(@Nullable String repoKey, @Nullable String path) {
        return isReleaseBundlesV2RepoKey(repoKey) && nonNull(path)
                && (path.endsWith(BUNDLE_INTERNAL_EXTENSION) || path.endsWith(DRAFT_BUNDLE_INTERNAL_EXTENSION));
    }

    public static boolean isReleaseBundleNavigationDisabled() {
        return !ConstantValues.lifecycleManifestVisible.getBoolean();
    }
}
