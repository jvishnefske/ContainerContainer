package org.artifactory.addon.bridge;

import com.google.common.base.Suppliers;
import com.jfrog.bh.acl.consumer.model.callhome.BundleReport;
import com.jfrog.bh.acl.producer.model.BundleInputBuild;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BundleArtifactoryConsumerBridgeEmptyImplTest {

    private BundleArtifactoryConsumerBridgeEmptyImpl bridgeToArtifactory;

    @BeforeEach
    public void setUp() {
        bridgeToArtifactory = new BundleArtifactoryConsumerBridgeEmptyImpl();
    }

    @Test
    void getServiceId_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getServiceId();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getRunningVersion_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getRunningVersion();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getJdbcHelper_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getJdbcHelper();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getDatabaseType_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getDatabaseType();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getNextDatabaseId_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getNextDatabaseId();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getAccessClient_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getAccessClient();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getRepositoryConfig_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getRepositoryConfig("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void repositoryPathExists_ThrowException() {
        Executable target = () -> bridgeToArtifactory.repositoryPathExists("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void hasPermission_ThrowException() {
        Executable target = () -> bridgeToArtifactory.hasPermission(PermissionScopeResourceType.INTERNAL,
                "string", "string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void createBundleRepository_ThrowException() {
        Executable target = () -> bridgeToArtifactory.createBundleRepository("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void collectBundleSourceItemsByAql_ThrowException() {
        Executable target = () -> bridgeToArtifactory.collectBundleSourceItemsByAql("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void collectBundleSourceItemsByFiles_ThrowException() {
        Executable target = () -> bridgeToArtifactory.collectBundleSourceItemsByFiles(Set.of());
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void copyFile_ThrowException() {
        Executable target = () -> bridgeToArtifactory.copyFile("string", "string",
                false);
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void readFile_ThrowException() {
        Executable target = () -> bridgeToArtifactory.readFile("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void storeFile_ThrowException() {
        Executable target = () -> bridgeToArtifactory.storeFile("string", new byte[] {});
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void deleteFile_ThrowException() {
        Executable target = () -> bridgeToArtifactory.deleteFile("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void deleteFiles_ThrowException() {
        Executable target = () -> bridgeToArtifactory.deleteFiles(Set.of());
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void deleteFilesWithPrune_ThrowException() {
        Executable target = () -> bridgeToArtifactory.deleteFiles(Set.of(), true);
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void collectBundleSourceItemsByPackage_ThrowException() {
        Executable target = () -> bridgeToArtifactory.collectBundleSourceItemsByPackage("packageName", "packageVersion",
                "packageType", "repository");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getFileInfo_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getFileInfo("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getProperties_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getProperties("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void addProperties_ThrowException() {
        Executable target = () -> bridgeToArtifactory.addProperties("string", Map.of());
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getBuild_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getBuild("string", "string", "string", "string", false);
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getBuildProperty_ThrowException() {
        Executable target =
                () -> bridgeToArtifactory.getBuildProperty("string", "string", "string", "string",
                        "string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void scheduleBundleCleanup_ThrowException() {
        Executable target = () -> bridgeToArtifactory.scheduleBundleCleanup("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void deScheduleBundleCleanup_ThrowException() {
        Executable target = () -> bridgeToArtifactory.deScheduleBundleCleanup("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void scheduleBundleXrayScan_ThrowException() {
        Executable target = () -> bridgeToArtifactory.scheduleBundleXrayScan("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void scheduleBundlePromotionXray_ThrowException() {
        Executable target = () -> bridgeToArtifactory.scheduleBundlePromotionXray("string");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void processAsync_ThrowException() {
        Supplier supplier = Suppliers.ofInstance(null);
        Executable target = () -> bridgeToArtifactory.processAsync(supplier);
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void submitCallHomeReport_ThrowException() {
        Executable target = () -> bridgeToArtifactory.submitCallHomeReport(BundleReport.builder().build());
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getLicenseType_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getLicenseType();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getXrayClient_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getXrayClient();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void isXrayEnabled_ThrowException() {
        Executable target = () -> bridgeToArtifactory.isXrayEnabled();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getXrayVersion_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getXrayVersion();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void isAllowPromoteWhenXrayUnavailable_ThrowException() {
        Executable target = () -> bridgeToArtifactory.isAllowPromoteWhenXrayUnavailable();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getBlockUnfinishedScansTimeoutSeconds_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getBlockUnfinishedScansTimeoutSeconds();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getArtifactoryId_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getArtifactoryId();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void scheduleBundleMigrations_ThrowException() {
        Executable target = () -> bridgeToArtifactory.scheduleBundleMigrations();
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void attachReleaseBundleToBuild_ThrowException() {
        BundleInputBuild build = mock(BundleInputBuild.class);
        Executable target = () -> bridgeToArtifactory.attachReleaseBundleToBuild(build, "a", "b", "c");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void detachReleaseBundleToBuild_ThrowException() {
        Executable target = () -> bridgeToArtifactory.detachReleaseBundleFromBuilds("a", "b", "c");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getBuildInfoRepoPath_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getBuildInfoRepoPath("a", "b", "c", "d");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void searchEvidence_ThrowException() {
        Executable target = () -> bridgeToArtifactory.searchEvidence("a", "b", "c");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void createEvidenceBySubject_ThrowException() {
        InputStream content = new ByteArrayInputStream(new byte[] {});
        Executable target = () -> bridgeToArtifactory.createEvidenceBySubject("a", content);
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getPackageId_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getPackageId("repoPath");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void repositoryExists_ThrowException() {
        Executable target = () -> bridgeToArtifactory.repositoryExists("repoKey");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void createRepositories_ThrowException() {
        Executable target = () -> bridgeToArtifactory.createRepositories(Map.of());
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getDefaultDeploymentRepoKey_ThrowException() {
        Executable target = () -> bridgeToArtifactory.getDefaultDeploymentRepoKey("repoKey");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getArtifactSourceRepoKeyFromVirtualRepo_ThrowException() {
        Executable target =
                () -> bridgeToArtifactory.getArtifactSourceRepoKeyFromVirtualRepo("repoKey", "artifact/path");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void isVirtualRepo_ThrowException() {
        Executable target = () -> bridgeToArtifactory.isVirtualRepo("repoKey");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void logMetrics_ThrowException() {
        Executable target = () -> bridgeToArtifactory.logMetrics(Map.of(), "metricName", 1);
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void reportBundleLocallyDeletedFromTarget_ThrowException() {
        Executable target = () -> bridgeToArtifactory.reportBundleLocallyDeletedFromTarget(
                "repo", "rbv2atw", "3.0.0", "serviceId", "signature");
        assertThrows(UnsupportedOperationException.class, target);
    }

    @Test
    void getConflictsGuard_ReturnsNonNullDummyGuard() throws InterruptedException {
        // When
        ConflictsGuard<Object> conflictsGuard = bridgeToArtifactory.getConflictsGuard("testCategory");

        // Then
        assertNotNull(conflictsGuard);

        // Test that getLock returns null
        assertNull(conflictsGuard.getLock("key"));

        // Test that tryToLock always returns true
        assertTrue(conflictsGuard.tryToLock("key", 1, TimeUnit.SECONDS));

        // Test that unlock is a no-op (doesn't throw exception)
        conflictsGuard.unlock("key");

        // Test that forceUnlock is a no-op (doesn't throw exception)
        conflictsGuard.forceUnlock("key");

        // Test that isLocked always returns false
        assertFalse(conflictsGuard.isLocked("key"));

        // Test that size returns 0
        assertEquals(0, conflictsGuard.size());

        // Test that keySet returns empty set
        assertNotNull(conflictsGuard.keySet());
        assertTrue(conflictsGuard.keySet().isEmpty());
    }
}
