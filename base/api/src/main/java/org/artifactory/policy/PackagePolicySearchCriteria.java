package org.artifactory.policy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author punyap
 */
@Jacksonized
@Builder(toBuilder = true)
@Getter
@Setter
public class PackagePolicySearchCriteria {
    List<String> packageTypes;
    List<String> repos;
    List<String> excludedRepos;
    List<String> includedPackages;
    List<String> excludedPackages;
    Boolean includeAllProjects;
    List<String> includedProjects;
    Boolean includeAllStages;
    List<String> includedStages;
    Integer createdBeforeInMonths;
    Integer lastDownloadedBeforeInMonths;
    Integer createdBeforeInDays;
    Integer lastDownloadedBeforeInDays;
    Integer keepLastNVersions;
    List<RepoAvailabilityStatus> reposWithStatus;
    Map<String, List<String>> includedProperties;
    Map<String, List<String>> excludedProperties;
    List<String> includedPackageVersions;
    List<String> excludedPackageVersions;
    Set<String> includedFolderPaths;
    Set<String> excludedFolderPaths;


    public List<Integer> getTimeBasedConditions() {
        return Stream.of(createdBeforeInMonths, lastDownloadedBeforeInMonths, createdBeforeInDays,
                lastDownloadedBeforeInDays).filter(Objects::nonNull).toList();
    }

    public boolean bothMonthsAndDaysExists() {
        boolean isCreatedBeforeInBoth = createdBeforeInDays != null && createdBeforeInMonths != null;
        boolean isLastDownloadedBeforeInBoth =
                lastDownloadedBeforeInDays != null && lastDownloadedBeforeInMonths != null;
        return isCreatedBeforeInBoth || isLastDownloadedBeforeInBoth;
    }

    public List<Integer> getVersionsBasedConditions() {
        return Stream.of(keepLastNVersions).filter(Objects::nonNull).toList();
    }

    public boolean hasInclusionBasedPropertyConditions() {
        if (includedProperties == null || includedProperties.isEmpty()) {
            return false;
        }
        return includedProperties.values().stream().filter(Objects::nonNull).anyMatch(list -> !list.isEmpty());
    }

    public boolean hasExclusionBasedPropertyConditions() {
        if (excludedProperties == null || excludedProperties.isEmpty()) {
            return false;
        }
        return excludedProperties.values().stream().filter(Objects::nonNull).anyMatch(list -> !list.isEmpty());
    }
}