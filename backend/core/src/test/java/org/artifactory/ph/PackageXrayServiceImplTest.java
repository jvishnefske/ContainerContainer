package org.artifactory.ph;

import com.jfrog.ph.acl.model.PackageXrayGateStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.xray.ArtifactXrayInfo;
import org.artifactory.addon.xray.XrayAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Shahaf Golan
 */
class PackageXrayServiceImplTest {

    private static final String REPO_KEY = "test-repo";
    private static final String PATH = "path/to/artifact";

    private AddonsManager addonsManager;
    private RepositoryService repositoryService;
    private XrayAddon xrayAddon;
    private ArtifactXrayInfo artifactXrayInfo;
    private PackageXrayServiceImpl packageXrayService;

    @BeforeEach
    void setUp() {
        addonsManager = mock(AddonsManager.class);
        repositoryService = mock(RepositoryService.class);
        xrayAddon = mock(XrayAddon.class);
        artifactXrayInfo = mock(ArtifactXrayInfo.class);
        when(addonsManager.addonByType(XrayAddon.class))
                .thenReturn(xrayAddon);
        packageXrayService =
                new PackageXrayServiceImpl(addonsManager, repositoryService);
    }

    @Test
    void isXrayEnabledForRepo_delegatesToXrayAddon() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);

        assertTrue(packageXrayService.isXrayEnabledForRepo(REPO_KEY, false));
        Mockito.verify(xrayAddon).isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY);
    }

    @Test
    void isXrayEnabledForRepo_returnsFalse() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(false);
        assertFalse(packageXrayService.isXrayEnabledForRepo(REPO_KEY, false));
    }

    @Test
    void isXrayEnabledForRepo_forceCheckEntitlement_skillsEntitled() {
        RepoDescriptor descriptor = mockRepoDescriptor(RepoType.Skills);
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(descriptor);
        when(addonsManager.isEntitled(EntitlementType.AI_CATALOG))
                .thenReturn(true);
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);

        assertTrue(packageXrayService.isXrayEnabledForRepo(REPO_KEY, true));
    }

    @Test
    void isXrayEnabledForRepo_forceCheckEntitlement_skillsNotEntitled() {
        RepoDescriptor descriptor = mockRepoDescriptor(RepoType.Skills);
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(descriptor);
        when(addonsManager.isEntitled(EntitlementType.AI_CATALOG))
                .thenReturn(false);

        assertFalse(
                packageXrayService.isXrayEnabledForRepo(REPO_KEY, true));
        Mockito.verify(xrayAddon, Mockito.never())
                .isXrayConfPerRepoAndIntegrationEnabled(any());
    }

    @Test
    void isXrayEnabledForRepo_forceCheckEntitlement_nonSkillsRepo() {
        RepoDescriptor descriptor = mockRepoDescriptor(RepoType.Docker);
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(descriptor);

        assertFalse(
                packageXrayService.isXrayEnabledForRepo(REPO_KEY, true));
        Mockito.verify(addonsManager, Mockito.never())
                .isEntitled(any());
    }

    @Test
    void isXrayEnabledForRepo_forceCheckEntitlement_nullDescriptor() {
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(null);

        assertFalse(
                packageXrayService.isXrayEnabledForRepo(REPO_KEY, true));
    }

    @Test
    void getArtifactXrayGateStatus_skillsRepoNotEntitled_returnsNotInEntitlement() {
        RepoDescriptor descriptor = mockRepoDescriptor(RepoType.Skills);
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(descriptor);
        when(addonsManager.isEntitled(EntitlementType.AI_CATALOG))
                .thenReturn(false);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, true);

        assertEquals(PackageXrayGateStatus.NOT_IN_ENTITLEMENT, status);
    }

    @Test
    void getArtifactXrayGateStatus_skillsRepoEntitled_proceedsToXrayCheck() {
        RepoDescriptor descriptor = mockRepoDescriptor(RepoType.Skills);
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(descriptor);
        when(addonsManager.isEntitled(EntitlementType.AI_CATALOG))
                .thenReturn(true);
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);
        when(xrayAddon.getArtifactXrayInfo(any()))
                .thenReturn(artifactXrayInfo);
        when(artifactXrayInfo.isArtifactProcessed()).thenReturn(true);
        when(artifactXrayInfo.isBlocked()).thenReturn(false);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, true);

        assertEquals(PackageXrayGateStatus.APPROVED, status);
    }

    @Test
    void getArtifactXrayGateStatus_nonSkillsRepo_returnsNotInEntitlement() {
        RepoDescriptor descriptor = mockRepoDescriptor(RepoType.Docker);
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(descriptor);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, true);

        assertEquals(PackageXrayGateStatus.NOT_IN_ENTITLEMENT, status);
        Mockito.verify(addonsManager, Mockito.never())
                .isEntitled(any());
    }

    @Test
    void getArtifactXrayGateStatus_nullDescriptor_returnsNotInEntitlement() {
        when(repositoryService.repoDescriptorByKey(REPO_KEY))
                .thenReturn(null);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, true);

        assertEquals(PackageXrayGateStatus.NOT_IN_ENTITLEMENT, status);
        Mockito.verify(addonsManager, Mockito.never())
                .isEntitled(any());
    }

    @Test
    void getArtifactXrayGateStatus_checkEntitlementFalse_skipsEntitlementCheck() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);
        when(xrayAddon.getArtifactXrayInfo(any()))
                .thenReturn(artifactXrayInfo);
        when(artifactXrayInfo.isArtifactProcessed()).thenReturn(true);
        when(artifactXrayInfo.isBlocked()).thenReturn(false);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, false);

        assertEquals(PackageXrayGateStatus.APPROVED, status);
        Mockito.verify(repositoryService, Mockito.never())
                .repoDescriptorByKey(any());
    }

    @Test
    void getArtifactXrayGateStatus_xrayDisabled_returnsXrayDisabledForRepo() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(false);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, false);

        assertEquals(PackageXrayGateStatus.XRAY_DISABLED_FOR_REPO, status);
    }

    @Test
    void getArtifactXrayGateStatus_scanInProgress_returnsScanInProgress() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);
        when(xrayAddon.getArtifactXrayInfo(any()))
                .thenReturn(artifactXrayInfo);
        when(artifactXrayInfo.isArtifactProcessed()).thenReturn(false);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, false);

        assertEquals(PackageXrayGateStatus.SCAN_IN_PROGRESS, status);
    }

    @Test
    void getArtifactXrayGateStatus_blocked_returnsBlocked() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);
        when(xrayAddon.getArtifactXrayInfo(any()))
                .thenReturn(artifactXrayInfo);
        when(artifactXrayInfo.isArtifactProcessed()).thenReturn(true);
        when(artifactXrayInfo.isBlocked()).thenReturn(true);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, false);

        assertEquals(PackageXrayGateStatus.BLOCKED, status);
    }

    @Test
    void getArtifactXrayGateStatus_blockedUnscanned_returnsScanInProgress() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);
        when(xrayAddon.getArtifactXrayInfo(any()))
                .thenReturn(artifactXrayInfo);
        when(artifactXrayInfo.isArtifactProcessed()).thenReturn(true);
        when(artifactXrayInfo.isBlocked()).thenReturn(true);
        when(artifactXrayInfo.getBlockReason()).thenReturn("Artifact not yet scanned");

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, false);

        assertEquals(PackageXrayGateStatus.SCAN_IN_PROGRESS, status);
    }

    @Test
    void getArtifactXrayGateStatus_approved_returnsApproved() {
        when(xrayAddon.isXrayConfPerRepoAndIntegrationEnabled(REPO_KEY))
                .thenReturn(true);
        when(xrayAddon.getArtifactXrayInfo(any()))
                .thenReturn(artifactXrayInfo);
        when(artifactXrayInfo.isArtifactProcessed()).thenReturn(true);
        when(artifactXrayInfo.isBlocked()).thenReturn(false);

        PackageXrayGateStatus status =
                packageXrayService.getArtifactXrayGateStatus(
                        REPO_KEY, PATH, false);

        assertEquals(PackageXrayGateStatus.APPROVED, status);
    }

    private RepoDescriptor mockRepoDescriptor(RepoType repoType) {
        RepoDescriptor descriptor = mock(RepoDescriptor.class);
        when(descriptor.getType()).thenReturn(repoType);
        return descriptor;
    }
}
