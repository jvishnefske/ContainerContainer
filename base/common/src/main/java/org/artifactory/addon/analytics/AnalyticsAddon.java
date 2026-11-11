package org.artifactory.addon.analytics;

import org.artifactory.common.addon.Addon;
import org.artifactory.descriptor.repo.RepoType;

/**
 * This addon is collecting usage analytics and specific API interactions.
 *
 * @author BarakH
 */
public interface AnalyticsAddon extends Addon {

    /**
     * This method should be called when a user successfully generated a token.
     * e.g. when a user accomplished docker login process.
     */
    default void userCreatedToken(RepoType repoType, String userName) {
    }

    /**
     * Track successful package download by {@link RepoType}
     */
    default void trackSuccessfulPackageDownload(RepoType repoType) {
    }

    /**
     * Track successful package redirected download by {@link RepoType}
     */
    default void trackPackageDownloadRedirect(RepoType repoType) {
    }

    /**
     * Track successful package upload by {@link RepoType}
     */
    default void trackSuccessfulPackageUpload(RepoType repoType) {
    }

    /**
     * Track successful package head request by {@link RepoType}
     */
    default void trackSuccessfulPackageHeadRequest(RepoType repoType) {
    }

    /**
     * @return The Artifactory node package usage counter.
     * Note the data is getting flushed into the usage.log from time to time.
     */
    default PackageUsageCounter getPackageUsageData() {
        return new PackageUsageCounter();
    }
}
