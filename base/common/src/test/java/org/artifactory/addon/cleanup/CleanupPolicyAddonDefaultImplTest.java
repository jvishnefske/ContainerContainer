package org.artifactory.addon.cleanup;

import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
class CleanupPolicyAddonDefaultImplTest {

    private final CleanupPolicyAddon addon = new CleanupPolicyAddonDefaultImpl();

    @Test
    void testIsDefault() {
        assertTrue(addon.isDefault());
    }

    @Test
    void testAssertThrowsMissingRestAddonExceptions() {
        assertThrows(MissingRestAddonException.class, addon::getSupportedPackages);
        assertThrows(MissingRestAddonException.class, () -> addon.getPackagePolicies(null));
        assertThrows(MissingRestAddonException.class, () -> addon.getPackagePolicy("someKey"));
        PackageCleanupPolicy policy = PackageCleanupPolicy.builder().build();
        assertThrows(MissingRestAddonException.class, () -> addon.addPackagePolicy(policy, "default"));
        assertThrows(MissingRestAddonException.class, () -> addon.updatePackagePolicy(policy));
        assertThrows(MissingRestAddonException.class, () -> addon.deletePackagePolicy("someKey"));
        assertThrows(MissingRestAddonException.class, () -> addon.runPolicy("someKey", false));
        assertThrows(MissingRestAddonException.class, () -> addon.performRunAction(1, "someAction"));
        assertThrows(MissingRestAddonException.class, addon::performStopAllAction);
        assertThrows(MissingRestAddonException.class, () -> addon.handlePolicyEnablement("someKey", true));
        Set<String> items = Set.of();
        assertThrows(MissingRestAddonException.class, () -> addon.getPolicyViews(items, "someKey", "default"));
        assertThrows(MissingRestAddonException.class,
                () -> addon.getCleanupPolicyRunsView(items, null, 1L, 2L, "someKey",
                        1L, 1L, null, null, null, null, null));
        assertThrows(MissingRestAddonException.class, () -> addon.downloadReport("policyKey", 1));
        assertThrows(MissingRestAddonException.class, () -> addon.handleDisablePolicies("someKey"));
        assertThrows(MissingRestAddonException.class, () -> addon.getBundlePolicy("someKey"));
        assertThrows(MissingRestAddonException.class, () -> addon.deleteBundlePolicy("someKey"));
        assertThrows(MissingRestAddonException.class, () -> addon.updateBundlePolicy(null));
        assertThrows(MissingRestAddonException.class, () -> addon.handleBundleCleanupPolicyEnablement("someKey", true));
        assertThrows(MissingRestAddonException.class, () -> addon.getBuildsPolicy("someKey"));
        assertThrows(MissingRestAddonException.class, () -> addon.deleteBuildsPolicy("someKey"));
        assertThrows(MissingRestAddonException.class, () -> addon.updateBuildsPolicy(null));
        assertThrows(MissingRestAddonException.class, () -> addon.handleBuildsCleanupPolicyEnablement("someKey", true));
        assertThrows(MissingRestAddonException.class, () -> addon.runBuildPolicy("someKey", true));
        assertThrows(MissingRestAddonException.class, () -> addon.getRunSummary("someKey", 123));
    }
}