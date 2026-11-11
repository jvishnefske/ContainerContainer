package org.artifactory.addon.analytics;

import lombok.Getter;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;

import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PackageUsageData is holding all the packages downloads and uploads events counted by package types.
 *
 * @author BarakH
 */
public class PackageUsageCounter {

    private final boolean packageConsumptionMetricsEnabled;
    @Getter
    private final EnumMap<RepoType, AtomicInteger> successfulPackagesDownloads;
    @Getter
    private final EnumMap<RepoType, AtomicInteger> successfulPackagesHeadRequests;
    @Getter
    private final EnumMap<RepoType, AtomicInteger> redirectedPackagesDownloads;
    @Getter
    private final EnumMap<RepoType, AtomicInteger> successfulPackagesUploads;

    public PackageUsageCounter() {
        successfulPackagesDownloads = new EnumMap<>(RepoType.class);
        redirectedPackagesDownloads = new EnumMap<>(RepoType.class);
        successfulPackagesUploads = new EnumMap<>(RepoType.class);
        successfulPackagesHeadRequests = new EnumMap<>(RepoType.class);
        for (RepoType repoType : RepoType.values()) {
            successfulPackagesDownloads.put(repoType, new AtomicInteger(0));
            redirectedPackagesDownloads.put(repoType, new AtomicInteger(0));
            successfulPackagesUploads.put(repoType, new AtomicInteger(0));
            successfulPackagesHeadRequests.put(repoType, new AtomicInteger(0));
        }
        packageConsumptionMetricsEnabled = ConstantValues.consumptionUsageMetricsEnabled.getBoolean();
    }

    public void trackSuccessfulPackageDownload(RepoType repoType) {
        if (!packageConsumptionMetricsEnabled) {
            return;
        }
        getSuccessfulPackagesDownloads().get(repoType).incrementAndGet();
    }

    public void trackPackageDownloadRedirect(RepoType repoType) {
        if (!packageConsumptionMetricsEnabled) {
            return;
        }
        getRedirectedPackagesDownloads().get(repoType).incrementAndGet();
    }

    public void trackSuccessfulPackageUpload(RepoType repoType) {
        if (!packageConsumptionMetricsEnabled) {
            return;
        }
        getSuccessfulPackagesUploads().get(repoType).incrementAndGet();
    }

    public void trackSuccessfulPackageHeadRequest(RepoType repoType) {
        if (!packageConsumptionMetricsEnabled) {
            return;
        }
        getSuccessfulPackagesHeadRequests().get(repoType).incrementAndGet();
    }

}