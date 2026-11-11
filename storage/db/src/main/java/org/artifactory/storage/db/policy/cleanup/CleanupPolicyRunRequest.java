package org.artifactory.storage.db.policy.cleanup;

import lombok.Builder;
import org.artifactory.policy.PolicyType;

import java.util.Set;

@Builder(toBuilder = true)
public record CleanupPolicyRunRequest(
        Set<String> itemTypes,
        Long from,
        Long to,
        String policyKeyFilter,
        Set<String> policyKeys,
        Long page,
        Long pageSize,
        String sortBy,
        String sortOrder,
        String excludeBlankExecutions,
        Set<String> status,
        Set<Integer> runIds,
        PolicyType policyType
) {
    // Backward compatible constructor for existing Cleanup module usage.
    public CleanupPolicyRunRequest(Set<String> itemTypes, Long from, Long to, String policyKeyFilter,
            Set<String> policyKeys, Long page, Long pageSize, String sortBy, String sortOrder,
            String excludeBlankExecutions, Set<String> status, Set<Integer> runIds) {
        this(itemTypes, from, to, policyKeyFilter, policyKeys, page, pageSize, sortBy, sortOrder,
                excludeBlankExecutions, status, runIds, null);
    }
}
