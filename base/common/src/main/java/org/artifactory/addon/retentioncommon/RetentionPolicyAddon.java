package org.artifactory.addon.retentioncommon;

import org.artifactory.common.addon.Addon;
import org.artifactory.policy.PolicyRunViewRequest;
import org.artifactory.policy.cleanup.CleanupPolicyRunView;
import org.artifactory.policy.cleanup.CleanupPolicyRunViewEntry;
import org.artifactory.policy.cleanup.CleanupPolicyView;
import org.artifactory.policy.cleanup.packages.FilterCleanupPackageResult;
import org.artifactory.policy.cleanup.packages.FilterCleanupPackages;
import org.artifactory.policy.cleanup.packages.PackageCleanupSupportOptions;
import org.artifactory.rest.exception.MissingRestAddonException;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Set;

/**
 * @author Kunal Mazumdar
 */
public interface RetentionPolicyAddon extends Addon {

    default List<PackageCleanupSupportOptions> getSupportedPackages() {
        throw new MissingRestAddonException();
    }

    default void deletePackagePolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default void performRunAction(int runId, String action) {
        throw new MissingRestAddonException();
    }

    default void performStopAllAction() {
        throw new MissingRestAddonException();
    }

    default void handlePolicyEnablement(String policyKey, boolean enabled) {
        throw new MissingRestAddonException();
    }

    default void handleDisablePolicies(String projectKey) {
        throw new MissingRestAddonException();
    }

    default void handleDisableAllPolicies(String projectKey) {
        throw new MissingRestAddonException();
    }

    default CleanupPolicyView getPolicyViews(Set<String> itemTypes, String policyKey, String projectKey) {
        throw new MissingRestAddonException();
    }

    default CleanupPolicyRunView getArchivePolicyRunsView(PolicyRunViewRequest policyRunViewRequest) {
        throw new MissingRestAddonException();
    }

    default FilterCleanupPackageResult filterCleanupPackages(FilterCleanupPackages filterCleanupPackages,
            String repoName) {
        throw new MissingRestAddonException();
    }

    default CleanupPolicyRunViewEntry getRunEntryOnTriggerId(String policyKey, int triggerId) {
        throw new MissingRestAddonException();
    }

    default Response downloadReport(String policyKey, Integer runId) {
        throw new MissingRestAddonException();
    }
}