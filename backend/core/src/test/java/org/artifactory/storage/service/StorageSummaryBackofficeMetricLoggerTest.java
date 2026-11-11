package org.artifactory.storage.service;

import jakarta.annotation.Nonnull;
import lombok.SneakyThrows;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.MetricsLoggerAddon;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.backoffice.ArtifactoryBackofficeMetricLogger;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.util.AolUtils;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;
import org.jfrog.metrics.backoffice.model.Metric;
import org.jfrog.storage.binstore.BinariesInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorageSummaryBackofficeMetricLoggerTest {
    private AutoCloseable autoCloseable;
    @Mock
    private MetricsLoggerAddon metricsLogger;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    private MockedStatic<AolUtils> mockStaticAolUtils;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        mockStaticAolUtils = mockStatic(AolUtils.class);
        bindContext();
    }

    @AfterEach
    @SneakyThrows
    void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryContextThreadBinder.unbind();
        mockStaticAolUtils.close();
        autoCloseable.close();
    }

    @Test
    @Order(2)
    @SneakyThrows
    void logMetrics() {
        when(AolUtils.isAol()).thenReturn(true);
        StorageSummaryBackofficeMetricLogger x =
                new StorageSummaryBackofficeMetricLogger(new ArtifactoryBackofficeMetricLogger());
        StorageSummaryInfo storageSummaryInfo = getStorageSummaryInfo();

        x.logMetrics(storageSummaryInfo);
        int times = storageSummaryInfo.getRepoStorageSummaries().size() * 2;
        verify(metricsLogger, times(times)).log(any(Metric.class));
    }

    @Test
    @Order(1)
    @SneakyThrows
    void logMetricsWithError() {
        when(AolUtils.isAol()).thenReturn(true);
        doThrow(BackofficeMetricsException.class).when(metricsLogger).log(any(Metric.class));
        StorageSummaryBackofficeMetricLogger x =
                new StorageSummaryBackofficeMetricLogger(new ArtifactoryBackofficeMetricLogger());
        StorageSummaryInfo storageSummaryInfo = getStorageSummaryInfo();

        assertThrows(BackofficeMetricsException.class, () -> x.logMetrics(storageSummaryInfo));
    }

    private static @Nonnull StorageSummaryInfo getStorageSummaryInfo() {
        Map<String, RepoStorageSummaryInfo> repoStorageSummaries = Map.of(
                "repoA", new RepoStorageSummaryInfo("repoA", RepoStorageSummaryInfo.RepositoryType.LOCAL, 32L, 1034,
                        1034L * 1024, "typeA", "projectKeyA"),
                "repoB", new RepoStorageSummaryInfo("repoB", RepoStorageSummaryInfo.RepositoryType.LOCAL, 42L, 1044,
                        1024L * 1024, "typeB", "projectKeyB"),
                "repoC", new RepoStorageSummaryInfo("repoC", RepoStorageSummaryInfo.RepositoryType.LOCAL, 52L, 1054,
                        1054L * 1024, "typeC", "projectKeyC"),
                "repoNA", new RepoStorageSummaryInfo("repoNA", RepoStorageSummaryInfo.RepositoryType.LOCAL, 52L, 1054,
                        1054L * 1024, "NA", "projectKeyNA"),
                "repoDI", new RepoStorageSummaryInfo("repoDI", RepoStorageSummaryInfo.RepositoryType.LOCAL, 62L, 1064,
                        1064L * 1024,
                        RepoType.BuildInfo.getDisplayName(), "projectKeyDI")
        );
        BinariesInfo binariesInfo = new BinariesInfo(1024, 1024 * 1024);
        Map<String, Long> projectsTotalSizes = Map.of("projectA", 1024L, "projectB", 2048L);
        Map<String, Long> projectsTotalFilesCount = Map.of("projectA", 100L, "projectB", 200L);
        return new StorageSummaryInfo(repoStorageSummaries, binariesInfo, projectsTotalSizes,
                projectsTotalFilesCount);
    }

    @ParameterizedTest
    @MethodSource("booleanProvider")
    @SneakyThrows
    void isEnabled(boolean isAol, boolean isSelfHosted, boolean expected) {
        when(AolUtils.isAol()).thenReturn(isAol);
        when(artifactorySystemProperties.getBooleanProperty(ConstantValues.storagePerRepoMetricsEnabled))
                .thenReturn(isSelfHosted);
        StorageSummaryBackofficeMetricLogger x =
                new StorageSummaryBackofficeMetricLogger(new ArtifactoryBackofficeMetricLogger());
        StorageSummaryInfo storageSummaryInfo = getStorageSummaryInfo();

        // Check if the method returns the expected value
        assertEquals(expected, x.isEnabled());
        // Check if the log honors enable/disable flag
        x.logMetrics(storageSummaryInfo);
        int times = expected ? storageSummaryInfo.getRepoStorageSummaries().size() * 2 : 0;
        verify(metricsLogger, times(times)).log(any(Metric.class));
    }

    private static Stream<Arguments> booleanProvider() {
        return Stream.of(
                Arguments.of(true, true, true),  // log enabled because of SaaS
                Arguments.of(true, false, true), // log enabled because of SaaS
                Arguments.of(false, true, true), // log enabled cause SH allows it
                Arguments.of(false, false, false)// log disabled because SH disallows it
        );
    }

    private void bindContext() {
        SecurityService securityService = mock(SecurityService.class);
        ArtifactoryApplicationContext contextMock = mock(ArtifactoryApplicationContext.class);

        File file = mock(File.class);
        when(file.toPath()).thenReturn(null);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        when(artifactoryHome.getTempWorkDir()).thenReturn(file);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        ArtifactoryHome.bind(artifactoryHome);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(contextMock.getSecurityService()).thenReturn(securityService);
        ArtifactoryContextThreadBinder.bind(contextMock);
        when(contextMock.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(MetricsLoggerAddon.class)).thenReturn(metricsLogger);
    }
}