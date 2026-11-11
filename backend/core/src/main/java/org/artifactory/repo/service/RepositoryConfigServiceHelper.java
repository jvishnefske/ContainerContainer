package org.artifactory.repo.service;

import jakarta.annotation.Nonnull;
import lombok.experimental.UtilityClass;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.util.bundles.BundleConfigurationUtil;

import static org.artifactory.descriptor.repo.RepoType.BuildInfo;
import static org.artifactory.descriptor.repo.RepoType.PipeInfo;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_REPO_LAYOUT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.EXAMPLE_REPO_KEY;

@UtilityClass
public class RepositoryConfigServiceHelper {

    public static LocalRepoConfig generateExampleRepoLocalConfig() {
        LocalRepoConfig exampleLocalRepoConfig = LocalRepoConfig.create(EXAMPLE_REPO_KEY, RepoType.Generic);
        exampleLocalRepoConfig.getBaseConfig().setDescription("Example artifactory repository");
        exampleLocalRepoConfig.getBaseConfig().setRepoLayoutRef(DEFAULT_REPO_LAYOUT);
        exampleLocalRepoConfig.getRepoTypeConfig().setHandleReleases(true);
        exampleLocalRepoConfig.getRepoTypeConfig().setHandleSnapshots(true);
        return exampleLocalRepoConfig;
    }

    public static boolean isInfoRepo(RepoType type) {
        return BuildInfo.equals(type) || PipeInfo.equals(type);
    }

    public static boolean isHiddenRepo(@Nonnull String repoName) {
        return SystemRepos.isObservabilityRepo(repoName) ||
                SystemRepos.isInTransitRepo(repoName) ||
                SystemRepos.isFullSyncRepo(repoName) ||
                SystemRepos.isGridInfoRepo(repoName) ||
                BundleConfigurationUtil.isReleaseBundlesV2DistributionRepoKey(repoName);
    }

    public static boolean isHiddenRepo(@Nonnull String repoName, boolean includeShippingOrBillingLogsRepos) {
        if (includeShippingOrBillingLogsRepos && SystemRepos.isShippingOrBillingLogsRepo(repoName)) {
            return false;
        }
        return isHiddenRepo(repoName);
    }

    public static boolean isBuildInfoRepo(RepoType type) {
        return BuildInfo.equals(type);
    }
}