package org.artifactory.addon.analytics;

import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PackageUsageCounterTest extends ArtifactoryHomeBoundJUnitTest {

    private PackageUsageCounter packageUsageCounter;

    @BeforeEach
    void setUp() {
        setStringSystemProperty(ConstantValues.consumptionUsageMetricsEnabled, "TRUE");
        packageUsageCounter = new PackageUsageCounter();
    }

    @Test
    void testTrackSuccessfulPackageDownload() {
        packageUsageCounter.trackSuccessfulPackageDownload(RepoType.Docker);
        packageUsageCounter.trackSuccessfulPackageDownload(RepoType.Maven);
        packageUsageCounter.trackSuccessfulPackageDownload(RepoType.Maven);
        Assertions.assertEquals(1,
                packageUsageCounter.getSuccessfulPackagesDownloads().get(RepoType.Docker).get());
        Assertions.assertEquals(2,
                packageUsageCounter.getSuccessfulPackagesDownloads().get(RepoType.Maven).get());
        Assertions.assertEquals(0, packageUsageCounter.getSuccessfulPackagesDownloads().get(RepoType.Alpine).get());

    }

    @Test
    void testTrackSuccessfulPackageUpload() {
        packageUsageCounter.trackSuccessfulPackageUpload(RepoType.Docker);
        packageUsageCounter.trackSuccessfulPackageUpload(RepoType.Maven);
        packageUsageCounter.trackSuccessfulPackageUpload(RepoType.Maven);
        Assertions.assertEquals(1,
                packageUsageCounter.getSuccessfulPackagesUploads().get(RepoType.Docker).get());
        Assertions.assertEquals(2,
                packageUsageCounter.getSuccessfulPackagesUploads().get(RepoType.Maven).get());
        Assertions.assertEquals(0, packageUsageCounter.getSuccessfulPackagesUploads().get(RepoType.Alpine).get());
    }

    @Test
    void testTrackSuccessfulPackageRedirect() {
        packageUsageCounter.trackPackageDownloadRedirect(RepoType.Docker);
        packageUsageCounter.trackPackageDownloadRedirect(RepoType.Maven);
        packageUsageCounter.trackPackageDownloadRedirect(RepoType.Maven);
        Assertions.assertEquals(1,
                packageUsageCounter.getRedirectedPackagesDownloads().get(RepoType.Docker).get());
        Assertions.assertEquals(2,
                packageUsageCounter.getRedirectedPackagesDownloads().get(RepoType.Maven).get());
        Assertions.assertEquals(0, packageUsageCounter.getRedirectedPackagesDownloads().get(RepoType.Alpine).get());

    }

    @Test
    void testTrackSuccessfulPackageHeadRequest() {
        packageUsageCounter.trackSuccessfulPackageHeadRequest(RepoType.Docker);
        packageUsageCounter.trackSuccessfulPackageHeadRequest(RepoType.Maven);
        packageUsageCounter.trackSuccessfulPackageHeadRequest(RepoType.Maven);
        Assertions.assertEquals(1,
                packageUsageCounter.getSuccessfulPackagesHeadRequests().get(RepoType.Docker).get());
        Assertions.assertEquals(2,
                packageUsageCounter.getSuccessfulPackagesHeadRequests().get(RepoType.Maven).get());
        Assertions.assertEquals(0, packageUsageCounter.getSuccessfulPackagesHeadRequests().get(RepoType.Alpine).get());
    }
}