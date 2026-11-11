package org.artifactory.ph;

import com.jfrog.ph.acl.exception.PackageException;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.common.repo.PackageType;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.backoffice.ArtifactoryBackofficeMetricLogger;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PackageAnalyticsServiceImplTest {

    @Mock
    private AddonsManager addonsManager;
    @Mock
    private OnBoardingAddon onBoardingAddon;
    @Mock
    private AnalyticsAddon analyticsAddon;
    @Mock
    private ArtifactoryBackofficeMetricLogger backofficeMetricLogger;

    private PackageAnalyticsServiceImpl packageAnalyticsService;
    private AutoCloseable autoCloseable;
    private static final String REPO_NAME = "repo_name";
    private static final String PACKAGE_TYPE = "terraform_example";
    private static final String PACKAGE_NAME = "package_name";
    private static final String PACKAGE_VERSION = "1.0.0";
    private static final String USER_NAME = "admin";

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(addonsManager.addonByType(OnBoardingAddon.class)).thenReturn(onBoardingAddon);
        when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);
        packageAnalyticsService = new PackageAnalyticsServiceImpl(addonsManager, backofficeMetricLogger);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testOnSuccessfulPackageDownload() {
        packageAnalyticsService.onSuccessfulPackageDownload(PackageType.NPM, REPO_NAME, PACKAGE_NAME, PACKAGE_VERSION);
        verify(onBoardingAddon).userDownloadedPackage(
                RepoType.fromType(PackageType.NPM.name()), REPO_NAME, PACKAGE_NAME, PACKAGE_VERSION);
        verifyNoMoreInteractions(onBoardingAddon, analyticsAddon, backofficeMetricLogger);
    }

    @Test
    void testOnSuccessfulPackageUpload() {
        packageAnalyticsService.onSuccessfulPackageUpload(PackageType.TERRAFORM, REPO_NAME, PACKAGE_NAME,
                PACKAGE_VERSION);
        verify(onBoardingAddon).userUploadedPackage(
                RepoType.fromType(PackageType.TERRAFORM.name()), REPO_NAME, PACKAGE_NAME, PACKAGE_VERSION);
        verifyNoMoreInteractions(onBoardingAddon, analyticsAddon, backofficeMetricLogger);
    }

    @Test
    void testUserCreatedToken() {
        packageAnalyticsService.userCreatedToken(PackageType.MAVEN, USER_NAME);
        verify(analyticsAddon).userCreatedToken(RepoType.fromType(PackageType.MAVEN.name()), USER_NAME);
        verifyNoMoreInteractions(onBoardingAddon, analyticsAddon, backofficeMetricLogger);
    }

    @Test
    void testTrackSuccessfulPackageUpload() {
        packageAnalyticsService.trackSuccessfulPackageUpload(PackageType.TERRAFORM);
        verify(analyticsAddon).trackSuccessfulPackageUpload(RepoType.fromType(PackageType.TERRAFORM.name()));
        verifyNoMoreInteractions(onBoardingAddon, analyticsAddon, backofficeMetricLogger);
    }

    @Test
    void testTrackSuccessfulPackageDownload() {
        packageAnalyticsService.trackSuccessfulPackageDownload(PackageType.MAVEN);
        verify(analyticsAddon).trackSuccessfulPackageDownload(RepoType.fromType(PackageType.MAVEN.name()));
        verifyNoMoreInteractions(onBoardingAddon, analyticsAddon, backofficeMetricLogger);
    }

    @Test
    void testLogMetricSuccess() throws PackageException, BackofficeMetricsException {
        Map<String, String> labels = Map.of(REPO_NAME, "r1");
        packageAnalyticsService.logMetric(labels, "my_metric", 42L);
        verify(backofficeMetricLogger).logMetric(labels, "my_metric", 42L);
    }

    @Test
    void testLogMetricThrowsPackageException() throws BackofficeMetricsException {
        Map<String, String> labels = Map.of();
        doThrow(new BackofficeMetricsException("FAIL TO CREATE METRIC"))
                .when(backofficeMetricLogger).logMetric(labels, "metric", 1L);
        PackageException ex = Assertions.assertThrows(
                PackageException.class,
                () -> packageAnalyticsService.logMetric(labels, "metric", 1L)
        );
        Assertions.assertEquals(HttpStatus.SC_BAD_REQUEST, ex.getStatus());
    }
}
