package org.artifactory.storage.db.policy.dao;

import lombok.Builder;
import org.artifactory.policy.PolicyType;

import javax.annotation.Nullable;

import java.util.Set;

@Builder(toBuilder = true)
public record PolicyRunInput(
        Set<String> itemTypes,
        long from,
        long to,
        @Nullable
        String policyKey,
        @Nullable
        Set<String> policyKeys,
        long page,
        long pageSize,
        String sortBy,
        String sortOrder,
        String excludeBlankExecutions,
        Set<String> status,
        Set<Integer> runIds,
        PolicyType policyType
) {
}
