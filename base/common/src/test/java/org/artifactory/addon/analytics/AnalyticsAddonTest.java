package org.artifactory.addon.analytics;

import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnalyticsAddonTest extends ArtifactoryHomeBoundJUnitTest {

    private AnalyticsAddon analyticsAddon;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        analyticsAddon = Mockito.spy(AnalyticsAddon.class);
        autoCloseable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void trackSuccessfulPackageDownload() {
        analyticsAddon.trackSuccessfulPackageDownload(RepoType.Docker);
    }

    @Test
    void trackPackageDownloadRedirect() {
        analyticsAddon.trackPackageDownloadRedirect(RepoType.Docker);
    }

    @Test
    void trackSuccessfulPackageUpload() {
        analyticsAddon.trackSuccessfulPackageUpload(RepoType.Docker);
    }

    @Test
    void trackSuccessfulHeadRequest() {
        analyticsAddon.trackSuccessfulPackageHeadRequest(RepoType.Docker);
    }

    @Test
    void getPackageUsageData() {
        PackageUsageCounter packageUsageCounter = analyticsAddon.getPackageUsageData();
        assertNotNull(packageUsageCounter);
    }
}