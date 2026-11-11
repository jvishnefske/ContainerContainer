package org.artifactory.addon.archive.v2;

import org.artifactory.policy.PolicyRunViewRequest;
import org.artifactory.policy.archive.packages.PackageArchivePolicy;
import org.artifactory.policy.restore.packages.PackageRestoreColdRequest;
import org.artifactory.policy.restore.packages.PackageRestoreWarmRequest;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Kunal Mazumdar
 */
class ArchivePolicyAddonDefaultImplTest {

    private final ArchivePolicyAddon addon = new ArchivePolicyAddonDefaultImpl();

    @Test
    void testIsDefault() {
        assertTrue(addon.isDefault());
    }

    @Test
    void testAssertThrowsMissingRestAddonExceptions() {
        assertThrows(MissingRestAddonException.class, addon::getSupportedPackages);
        assertThrows(MissingRestAddonException.class, () -> addon.getPackagePolicies(null));
        assertThrows(MissingRestAddonException.class, () -> addon.getPackagePolicy("someKey"));
        PackageArchivePolicy policy = PackageArchivePolicy.builder().build();
        assertThrows(MissingRestAddonException.class, () -> addon.addPackagePolicy(policy, "default"));
        assertThrows(MissingRestAddonException.class, () -> addon.updatePackagePolicy(policy));
        assertThrows(MissingRestAddonException.class, () -> addon.deletePackagePolicy("someKey"));
        assertThrows(MissingRestAddonException.class, () -> addon.runPolicy(policy, false));
        assertThrows(MissingRestAddonException.class, () -> addon.performRunAction(1, "someAction"));
        assertThrows(MissingRestAddonException.class, addon::performStopAllAction);
        assertThrows(MissingRestAddonException.class, () -> addon.handlePolicyEnablement("someKey", true));
        assertThrows(MissingRestAddonException.class, () -> addon.getPolicyViews(Set.of(), "someKey", "default"));
        assertThrows(MissingRestAddonException.class, () -> addon.getRunEntryOnTriggerId("policyKey", 1));
        assertThrows(MissingRestAddonException.class, () -> addon.downloadReport("policyKey", 1));
        assertThrows(MissingRestAddonException.class, () -> addon.handleDisablePolicies("someKey"));
        PolicyRunViewRequest policyRunViewRequest = PolicyRunViewRequest.builder().policyKey("oldKey").build();
        assertThrows(MissingRestAddonException.class, () ->
                addon.getArchivePolicyRunsView(policyRunViewRequest.toBuilder().policyKey("someKey").build()));
        assertThrows(MissingRestAddonException.class, () -> addon.filterCleanupPackages(null, null));
        assertThrows(MissingRestAddonException.class,
                () -> addon.getArchivePolicyViews(Set.of("package"), "someKey", "default"));
        PackageRestoreWarmRequest warmRequest = PackageRestoreWarmRequest.builder().build();
        PackageRestoreColdRequest coldRequest = PackageRestoreColdRequest.builder().build();
        assertThrows(MissingRestAddonException.class, () -> addon.initiatePackageRestoreInCold(coldRequest));
        assertThrows(MissingRestAddonException.class, () -> addon.initiatePackageRestoreInWarm(warmRequest, "default"));
        assertThrows(MissingRestAddonException.class, () -> addon.getRunSummary(123));
    }
}