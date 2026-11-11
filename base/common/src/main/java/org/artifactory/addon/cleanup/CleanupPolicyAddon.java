package org.artifactory.addon.cleanup;

import org.artifactory.addon.cleanup.statistics.CleanupPackageStats;
import org.artifactory.addon.license.EdgeBlockedAddon;
import org.artifactory.addon.retention.common.RunSummaryResponse;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicy;
import org.artifactory.policy.cleanup.CleanupPolicyRunView;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicy;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.rest.exception.MissingRestAddonException;
import org.artifactory.addon.retentioncommon.RetentionPolicyAddon;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Oleksandr Legenkiy
 */
@EdgeBlockedAddon
@Entitlement(EntitlementType.JFRT)
public interface CleanupPolicyAddon extends RetentionPolicyAddon {

    default List<PackageCleanupPolicy> getPackagePolicies(String projectKey) {
        throw new MissingRestAddonException();
    }

    default PackageCleanupPolicy getPackagePolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default PackageCleanupPolicy addPackagePolicy(PackageCleanupPolicy policy, String projectKey) {
        throw new MissingRestAddonException();
    }

    default PackageCleanupPolicy updatePackagePolicy(PackageCleanupPolicy updatedPolicy) {
        throw new MissingRestAddonException();
    }

    default CleanupPolicyRunView getCleanupPolicyRunsView(Set<String> itemTypes, String projectKey, Long from, Long to,
            String policyKey, Long page, Long pageSize, String sortBy, String sortOrder, String excludeBlankExecutions,
            Set<String> status, List<String> projectKeyList) {
        throw new MissingRestAddonException();
    }

    default int runPolicy(String policyKey, boolean dryRun) {
        throw new MissingRestAddonException();
    }

    default CleanupPackageStats getCleanupPackageStats() {
        throw new MissingRestAddonException();
    }

    default boolean isActivePolicyWithEnabledTrashcanPresent() {
        return false;
    }

    /**
     * Downloads the report for the specified cleanup run and policy.
     *
     * @param policyKey The unique identifier of the policy.
     * @param runId     The unique identifier of the cleanup run.
     * @return A Response object containing the report data.
     */
    default Response downloadReport(String policyKey, Integer runId) {
        throw new MissingRestAddonException();
    }

    /*
     * --- RELEASE BUNDLE RELATED METHODS BELOW ---
     */

    default BundleCleanupPolicy addReleaseBundlePolicy(BundleCleanupPolicy policy,
            String projectKey) {
        throw new MissingRestAddonException();
    }

    default int runReleaseBundlePolicy(String policyKey, boolean dryRun) {
        throw new MissingRestAddonException();
    }
    default BundleCleanupPolicy getBundlePolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default List<BundleCleanupPolicy> getBundlePolicies(String projectKey) {
        throw new MissingRestAddonException();
    }

    default BundleCleanupPolicy updateBundlePolicy(BundleCleanupPolicy updatedPolicy) {
        throw new MissingRestAddonException();
    }
    default void deleteBundlePolicy(String policyKey) {
        throw new MissingRestAddonException();
    }
    default void handleBundleCleanupPolicyEnablement(String policyKey, boolean enabled) {
        throw new MissingRestAddonException();
    }
    default PackageCleanupPolicy getPackagePolicyWithRepoAvailability(String policyKey, Boolean checkRepoAvailability) {
        throw new MissingRestAddonException();
    }

    /*
     * --- BUILDS RELATED METHODS BELOW ---
     */

    default BuildCleanupPolicy addBuildsPolicy(BuildCleanupPolicy policy, String projectKey) {
        throw new MissingRestAddonException();
    }

    default BuildCleanupPolicy getBuildsPolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default BuildCleanupPolicy updateBuildsPolicy(BuildCleanupPolicy updatedPolicy) {
        throw new MissingRestAddonException();
    }

    default void deleteBuildsPolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default void handleBuildsCleanupPolicyEnablement(String policyKey, boolean enabled) {
        throw new MissingRestAddonException();
    }

    default int runBuildPolicy(String policyKey, boolean dryRun) {
        throw new MissingRestAddonException();
    }

    default Optional<RunSummaryResponse> getRunSummary(String policyKey, Integer triggerId){
        throw new MissingRestAddonException();
    }
}
