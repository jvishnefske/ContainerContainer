package org.artifactory.engine;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.leadartifactdetector.LeadArtifactDetectorAddon;
import org.artifactory.addon.onboarding.OnBoardingAddon;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.request.ArtifactoryRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class UploadServicePackageUsageTest extends ArtifactoryHomeBoundJUnitTest {

    private UploadServiceImpl uploadService;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private LeadArtifactDetectorAddon leadArtifactDetectorAddon;
    @Mock
    private AnalyticsAddon analyticsAddon;
    @Mock
    private OnBoardingAddon onBoardingAddon;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        uploadService = Mockito.spy(new UploadServiceImpl());
        uploadService.setAddonsManager(addonsManager);
        Mockito.when(addonsManager.addonByType(LeadArtifactDetectorAddon.class)).thenReturn(leadArtifactDetectorAddon);
        Mockito.when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);
        Mockito.when(addonsManager.addonByType(OnBoardingAddon.class)).thenReturn(onBoardingAddon);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testTrackSuccessfulPackageUploadIsSkipped() {
        LocalRepoConfig repoConfig = Mockito.mock(LocalRepoConfig.class);
        Mockito.when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        Mockito.when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        ArtifactoryRequest artifactoryRequest = Mockito.mock(ArtifactoryRequest.class);
        Mockito.when(artifactoryRequest.shouldSkipPackageUsageTrack()).thenReturn(true);

        uploadService.notifyOnSuccessfulPackageUpload(localRepo, artifactoryRequest,
                RepoPathFactory.create("repo/image/tag/manifest.json"));
        Mockito.verifyNoMoreInteractions(analyticsAddon);
    }

    @Test
    void testNotifyOnSuccessfulPackageUploadIsTrackingUpload() {
        LocalRepoConfig repoConfig = Mockito.mock(LocalRepoConfig.class);
        Mockito.when(repoConfig.getPackageType()).thenReturn(RepoType.Docker);
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        Mockito.when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        ArtifactoryRequest artifactoryRequest = Mockito.mock(ArtifactoryRequest.class);
        Mockito.when(artifactoryRequest.shouldSkipPackageUsageTrack()).thenReturn(false);
        RepoPath repoPath = RepoPathFactory.create("repo/image/tag/manifest.json");
        Mockito.when(leadArtifactDetectorAddon.isPackageConsumptionLead(RepoType.Docker, repoPath)).thenReturn(true);
        uploadService.notifyOnSuccessfulPackageUpload(localRepo, artifactoryRequest,
                repoPath);
        Mockito.verify(analyticsAddon).trackSuccessfulPackageUpload(RepoType.Docker);
        Mockito.verifyNoMoreInteractions(analyticsAddon);
    }

    @Test
    void testNotifyOnSuccessfulPackageUploadWithMaven() {
        LocalRepoConfig repoConfig = Mockito.mock(LocalRepoConfig.class);
        Mockito.when(repoConfig.getPackageType()).thenReturn(RepoType.Maven);
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        Mockito.when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        Mockito.when(localRepo.getKey()).thenReturn("repo");
        ArtifactoryRequest artifactoryRequest = Mockito.mock(ArtifactoryRequest.class);
        Mockito.when(artifactoryRequest.shouldSkipPackageUsageTrack()).thenReturn(false);
        RepoPath repoPath = RepoPathFactory.create("repo/some/path/to.pom");
        Mockito.when(leadArtifactDetectorAddon.isPackageConsumptionLead(RepoType.Maven, repoPath)).thenReturn(true);
        uploadService.notifyOnSuccessfulPackageUpload(localRepo, artifactoryRequest,
                repoPath);
        Mockito.verify(analyticsAddon).trackSuccessfulPackageUpload(RepoType.Maven);
        Mockito.verify(onBoardingAddon).userUploadedPackage(RepoType.Maven, "repo", repoPath.getName(), "");
        Mockito.verifyNoMoreInteractions(analyticsAddon);
        Mockito.verifyNoMoreInteractions(onBoardingAddon);
    }

    @Test
    void testNotifyOnSuccessfulPackageUploadWithTerraformBackend() {
        LocalRepoConfig repoConfig = Mockito.mock(LocalRepoConfig.class);
        Mockito.when(repoConfig.getPackageType()).thenReturn(RepoType.TerraformBackend);
        LocalRepo localRepo = Mockito.mock(LocalRepo.class);
        Mockito.when(localRepo.getRepoConfig()).thenReturn(repoConfig);
        Mockito.when(localRepo.getKey()).thenReturn("repo");
        ArtifactoryRequest artifactoryRequest = Mockito.mock(ArtifactoryRequest.class);
        Mockito.when(artifactoryRequest.shouldSkipPackageUsageTrack()).thenReturn(false);
        RepoPath repoPath = RepoPathFactory.create("repo/some/path/state.latest.json");
        Mockito.when(leadArtifactDetectorAddon.isPackageConsumptionLead(RepoType.TerraformBackend, repoPath))
                .thenReturn(true);
        uploadService.notifyOnSuccessfulPackageUpload(localRepo, artifactoryRequest,
                repoPath);
        Mockito.verify(analyticsAddon).trackSuccessfulPackageUpload(RepoType.TerraformBackend);
    }
}
