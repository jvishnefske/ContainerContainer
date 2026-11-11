package org.artifactory.policy.cleanup.bundle;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Chaitanya Vootla
 */

@Jacksonized
@Builder(toBuilder = true)
public record BundleCleanupPolicySearchCriteria(
        Boolean includeAllProjects,
        List<String> includedProjects,
        List<ReleaseBundle> releaseBundles,
        List<String> excludePromotedEnvironments,
        Integer createdBeforeInMonths,
        Integer createdBeforeInDays) {

    public List<Integer> getTimeBasedConditions() {
        return Stream.of(createdBeforeInMonths, createdBeforeInDays).filter(Objects::nonNull).toList();
    }

    public boolean bothTimeBasedConditionsExist() {
        return createdBeforeInMonths != null && createdBeforeInDays != null;
    }

    @Builder(toBuilder = true)
    public record ReleaseBundle(
            String name,
            String projectKey
    ) {
    }
}