package org.artifactory.policy.cleanup.packages;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import org.artifactory.policy.RepoAvailabilityStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author Oleksandr Legenkiy
 */

@Jacksonized
@Builder(toBuilder = true)
public record PackageCleanupPolicySearchCriteria(
        List<String> packageTypes,
        List<String> repos,
        List<String> excludedRepos,
        List<String> includedPackages,
        List<String> excludedPackages,
        Boolean includeAllProjects,
        List<String> includedProjects,
        Boolean includeAllStages,
        List<String> includedStages,
        Integer createdBeforeInMonths,
        Integer lastDownloadedBeforeInMonths,
        Integer keepLastNVersions,
        List<RepoAvailabilityStatus> reposWithStatus,
        Map<String, List<String>> includedProperties,
        Map<String, List<String>> excludedProperties,
        List<String> includedPackageVersions,
        List<String> excludedPackageVersions) {

    public List<Integer> getTimeBasedConditions() {
        return Stream.of(createdBeforeInMonths, lastDownloadedBeforeInMonths).filter(Objects::nonNull).toList();
    }

    public List<Integer> getVersionsBasedConditions() {
        return Stream.of(keepLastNVersions).filter(Objects::nonNull).toList();
    }
}