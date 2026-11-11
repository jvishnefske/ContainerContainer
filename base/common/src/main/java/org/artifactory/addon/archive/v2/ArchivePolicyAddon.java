package org.artifactory.addon.archive.v2;

import org.artifactory.addon.license.EdgeBlockedAddon;
import org.artifactory.addon.retention.common.RunSummaryResponse;
import org.artifactory.addon.retentioncommon.RetentionPolicyAddon;
import org.artifactory.entitlement.Entitlement;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.policy.archive.packages.PackageArchivePolicy;
import org.artifactory.policy.cleanup.CleanupPolicyRunView;
import org.artifactory.policy.cleanup.CleanupPolicyView;
import org.artifactory.policy.restore.packages.PackageRestoreWarmRequest;
import org.artifactory.policy.restore.packages.PackageRestoreColdRequest;
import org.artifactory.policy.restore.packages.PackageRestoreWarmResponse;
import org.artifactory.policy.restore.packages.PackageRestoreColdResponse;
import org.artifactory.policy.restore.packages.PackageRestoreCandidate;
import org.artifactory.rest.exception.MissingRestAddonException;

import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Kunal Mazumdar
 */
@EdgeBlockedAddon
@Entitlement(EntitlementType.JFRT)
public interface ArchivePolicyAddon extends RetentionPolicyAddon {

    default List<PackageArchivePolicy> getPackagePolicies(String projectKey) {
        throw new MissingRestAddonException();
    }

    default CleanupPolicyView getArchivePolicyViews(Set<String> itemTypes, String policyKeyFilter, String projectKey) {
        throw new MissingRestAddonException();
    }

    default PackageArchivePolicy getPackagePolicy(String policyKey) {
        throw new MissingRestAddonException();
    }

    default PackageArchivePolicy addPackagePolicy(PackageArchivePolicy policy, String projectKey) {
        throw new MissingRestAddonException();
    }

    default PackageArchivePolicy updatePackagePolicy(PackageArchivePolicy updatedPolicy) {
        throw new MissingRestAddonException();
    }

    default int runPolicy(PackageArchivePolicy policy, boolean dryRun) {
        throw new MissingRestAddonException();
    }

    default PackageRestoreWarmResponse initiatePackageRestoreInWarm(PackageRestoreWarmRequest packageRestoreRequest,
            String projectKey) {
        throw new MissingRestAddonException();
    }

    default List<PackageRestoreCandidate> getRunNodes(String policyKey, int runId) {
        throw new MissingRestAddonException();
    }

    default PackageRestoreColdResponse initiatePackageRestoreInCold(
            PackageRestoreColdRequest packageRestoreRequest) {
        throw new MissingRestAddonException();
    }

    default CleanupPolicyRunView getRestoreRunsView(String projectKey, Long from, Long to,
            String policyKey, Long page, Long pageSize, String sortBy, String sortOrder, Set<String> status) {
        throw new MissingRestAddonException();
    }

    default PackageArchivePolicy getPackagePolicyWithRepoAvailability(String policyKey, Boolean checkRepoAvailability) {
        throw new MissingRestAddonException();
    }

    default Optional<RunSummaryResponse> getRunSummary(Integer triggerId){
        throw new MissingRestAddonException();
    }

    /**
     * Smart archiving consumption for the given time range. Use {@code format=csv} or {@code format=json}.
     *
     * @param timezone         IANA timezone identifier (e.g. {@code Asia/Kolkata}) used to interpret
     *                         {@code fromMillis}/{@code toMillis} as local dates and recompute correct UTC boundaries
     * @param totalConsumption when {@code true} the response includes {@code totalConsumptionBytes} calculated
     *                         across all historical runs (not limited to the selected date range). Pass {@code true}
     *                         only on initial page load; subsequent requests should pass {@code false} to skip
     *                         the extra aggregation query.
     */
    default Response getSmartArchivingConsumption(Long fromMillis, Long toMillis, String format, String timezone,
            Boolean totalConsumption) {
        throw new MissingRestAddonException();
    }
}