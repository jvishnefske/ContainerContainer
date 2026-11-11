package org.artifactory.policy;

import lombok.Builder;
import org.jfrog.access.common.ResourceType;

import java.util.List;
import java.util.Set;

@Builder(toBuilder = true)
public record PolicyRunViewRequest(
        Set<String> itemTypes, String projectKey, Long from, Long to,
        String policyKey, Long page, Long pageSize, String sortBy, String sortOrder,
        String excludeBlankExecutions,
        Set<String> status, List<String> projectKeyList,
        PolicyType policyType,
        ResourceType policyResourceType,
        Set<Integer> runIds) {
}
