package org.artifactory.policy.cleanup.builds;

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
public record BuildCleanupPolicySearchCriteria(
        Boolean includeAllProjects,
        List<String> includedProjects,
        List<Build> includedBuilds,
        Boolean excludeBuildsWithReleaseBundles,
        Integer createdBeforeInMonths,
        Integer createdBeforeInDays) {

    @Builder(toBuilder = true)
    public record Build(
            String name,
            String projectKey
    ) {
    }

    public boolean bothMonthsAndDaysExists() {
        return createdBeforeInDays != null && createdBeforeInMonths != null;
    }

    public List<Integer> getTimeBasedConditions() {
        return Stream.of(createdBeforeInMonths, createdBeforeInDays).filter(Objects::nonNull).toList();
    }
}