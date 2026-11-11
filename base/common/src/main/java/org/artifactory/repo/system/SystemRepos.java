package org.artifactory.repo.system;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RepoConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class and enum represent the system repos
 * Most system repositories also have their own interceptors in order to block or allow copy/move/delete actions
 * Or any other actions we would like to intercept regarding a specific repo
 * For example {@link org.artifactory.repo.interceptor.SupportBundleRepoInterceptor}
 * Furthermore there are also rebuild functions that the repositories sometimes have here
 * {@link org.artifactory.repo.service.RepositoryServiceImpl} in repositories cache.
 * For UI, there is a RootFetcherStrategy to decide which repos to show.
 * For system repo there is a fetch service and logic regarding the fetch strategy can be added there.
 * look at org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.fetch.SystemRepoFetchService
 *
 * @author Tomer Slutsky
 */
public class SystemRepos {
    public static final String USAGE_REPO_KEY = "jfrog-usage-logs";
    public static final String BILLING_REPO_KEY = "jfrog-billing-logs";
    public static final String LOG_SHIPPING_REPO_KEY = "jfrog-logs";
    public static final String BUILDINFO_REPO_KEY = "artifactory-build-info";
    public static final String PIPEINFO_REPO_KEY = "artifactory-pipe-info";
    public static final String TRASH_REPO_KEY = "auto-trashcan";
    public static final String SUPPORT_BUNDLE_REPO_KEY = "jfrog-support-bundle";
    public static final String IN_TRANSIT_REPO_KEY = "_intransit";
    public static final String EDGE_UPLOADS_REPO_KEY = "artifactory-edge-uploads";
    public static final String RELEASE_BUNDLE_REPO_KEY = "release-bundles";
    public static final String FULL_SYNC_REPO_KEY = "jfrog-full-sync-info";
    public static final String GRID_INFO_REPO_KEY = "jfrog-grid-info";

    public static boolean isObservabilityRepo(String repoKey) {
        return USAGE_REPO_KEY.equalsIgnoreCase(repoKey) ||
                isShippingOrBillingLogsRepo(repoKey);
    }

    public static boolean isShippingOrBillingLogsRepo(String repoKey) {
        return LOG_SHIPPING_REPO_KEY.equalsIgnoreCase(repoKey) ||
                BILLING_REPO_KEY.equalsIgnoreCase(repoKey);
    }

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents the full sync repo
     */
    public static boolean isFullSyncRepo(String repoKey) {
        return FULL_SYNC_REPO_KEY.equalsIgnoreCase(repoKey);
    }

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents the Grid info system repo
     */
    public static boolean isGridInfoRepo(String repoKey) {
        return GRID_INFO_REPO_KEY.equalsIgnoreCase(repoKey);
    }

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents the support bundle
     */
    public static boolean isSupportBundle(String repoKey) {
        return SUPPORT_BUNDLE_REPO_KEY.equals(repoKey);
    }

    /**
     * @param repoKey The repo key to check
     * @return True if the repo key represents the _intransit repo
     */
    public static boolean isInTransitRepo(@Nullable String repoKey) {
        return IN_TRANSIT_REPO_KEY.equals(repoKey);
    }

    @AllArgsConstructor
    @Getter
    public enum SystemRepoConfig {
        BUILDINFO(BUILDINFO_REPO_KEY),
        EDGE_UPLOADS(EDGE_UPLOADS_REPO_KEY),
        IN_TRANSIT(IN_TRANSIT_REPO_KEY, false, false, false, true),
        PIPEINFO(PIPEINFO_REPO_KEY),
        RELEASE_BUNDLE(RELEASE_BUNDLE_REPO_KEY),
        SUPPORT_BUNDLE(SUPPORT_BUNDLE_REPO_KEY),
        LOG_SHIPPING(LOG_SHIPPING_REPO_KEY, true, false, false, false),
        USAGE(USAGE_REPO_KEY, false, true, false, true),
        BILLING(BILLING_REPO_KEY, true, false, false, false),
        TRASHCAN(TRASH_REPO_KEY),
        FULL_SYNC(FULL_SYNC_REPO_KEY, false, false, false, true),
        GRID_INFO(GRID_INFO_REPO_KEY, false, false, false, true);

        private static final Map<String, SystemRepoConfig> BY_REPO_KEY = new HashMap<>();
        private static final Map<String, SystemRepoConfig> INVISIBLE_REPO_KEYS = new HashMap<>();

        static {
            for (SystemRepoConfig systemRepoConfig : values()) {
                BY_REPO_KEY.put(systemRepoConfig.repoKey, systemRepoConfig);
                if (!systemRepoConfig.visible) {
                    INVISIBLE_REPO_KEYS.put(systemRepoConfig.repoKey, systemRepoConfig);
                }
            }
        }

        private final String repoKey;
        private final boolean visible;
        private final boolean downloadThroughAPI;
        private final boolean importAllowed;
        private final boolean deleteOnlyBySuperUser;

        SystemRepoConfig(String repoKey) {
            this.repoKey = repoKey;
            this.visible = true;
            this.downloadThroughAPI = false;
            this.importAllowed = true;
            this.deleteOnlyBySuperUser = false;
        }

        public static boolean isImportAllowed(String repoKey) {
            return findFirst(repoKey).map(c -> c.importAllowed).orElse(true);
        }

        public static boolean isDeleteOnlyBySuperUser(String repoKey) {
            return findFirst(repoKey).map(c -> c.deleteOnlyBySuperUser).orElse(false);
        }

        public static SystemRepoConfig valueOfRepoKey(String repoKey) {
            return BY_REPO_KEY.get(repoKey);
        }

        public static SystemRepoConfig getSystemRepo(String repoKey) {
            return findFirst(repoKey).orElseThrow(() -> new IllegalArgumentException(
                    "Could not find the requested system repo key " + repoKey));
        }

        public static boolean isSystemRepo(String repoKey) {
            return repoKey != null && BY_REPO_KEY.containsKey(repoKey.toLowerCase());
        }

        public static boolean isBuildInfoTypeRepo(RepoConfig repoConfig) {
            if (repoConfig == null) {
                return false;
            }
            RepoType packageType = repoConfig.getPackageType();
            return packageType != null && packageType.isBuildInfo();
        }

        public static boolean isInvisibleSystemRepo(String repoKey) {
            return repoKey != null && INVISIBLE_REPO_KEYS.containsKey(repoKey.toLowerCase());
        }

        public static List<String> getExcludedFromImportSystemRepos() {
            return Arrays.stream(SystemRepoConfig.values())
                    .filter(systemRepoConfig -> !systemRepoConfig.importAllowed)
                    .map(SystemRepoConfig::getRepoKey)
                    .toList();
        }

        private static Optional<SystemRepoConfig> findFirst(String repoKey) {
            return Arrays.stream(SystemRepoConfig.values())
                    .filter(curr -> curr.getRepoKey().equalsIgnoreCase(repoKey))
                    .findFirst();
        }
    }
}
