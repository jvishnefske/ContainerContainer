package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.service.PackageAnalyticsService;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.backoffice.ArtifactoryBackofficeMetricLogger;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;

import java.util.Map;

import static com.jfrog.ph.acl.exception.PackageBadRequestException.BAD_REQUEST;

public class PackageAnalyticsServiceImpl implements PackageAnalyticsService {

    private final AddonsManager addonsManager;
    private final ArtifactoryBackofficeMetricLogger backofficeMetricLogger;

    public PackageAnalyticsServiceImpl(AddonsManager addonsManager,
            ArtifactoryBackofficeMetricLogger backofficeMetricLogger) {
        this.addonsManager = addonsManager;
        this.backofficeMetricLogger = backofficeMetricLogger;
    }

    @Override
    public void onSuccessfulPackageDownload(PackageType packageType, String repoKey, String packageName,
            String version) {
        addonsManager.addonByType(OnBoardingAddon.class).userDownloadedPackage(
                RepoType.fromType(packageType.name()), repoKey, packageName, version);
    }

    @Override
    public void onSuccessfulPackageUpload(PackageType packageType, String repoKey, String packageName, String version) {
        addonsManager.addonByType(OnBoardingAddon.class).userUploadedPackage(
                RepoType.fromType(packageType.name()), repoKey, packageName, version);
    }

    @Override
    public void userCreatedToken(PackageType packageType, String userName) {
        addonsManager.addonByType(AnalyticsAddon.class)
                .userCreatedToken(RepoType.fromType(packageType.name()), userName);
    }

    @Override
    public void trackSuccessfulPackageUpload(PackageType packageType) {
        addonsManager.addonByType(AnalyticsAddon.class)
                .trackSuccessfulPackageUpload(RepoType.fromType(packageType.name()));
    }

    @Override
    public void trackSuccessfulPackageDownload(PackageType packageType) {
        addonsManager.addonByType(AnalyticsAddon.class)
                .trackSuccessfulPackageDownload(RepoType.fromType(packageType.name()));
    }

    @Override
    public void logMetric(Map<String, String> labels, String metricName, long metricValue) throws PackageException {
        try {
            backofficeMetricLogger.logMetric(labels, metricName, metricValue);
        } catch (BackofficeMetricsException e) {
            throw new PackageException(HttpStatus.SC_BAD_REQUEST, BAD_REQUEST, e.getMessage());
        }
    }
}
