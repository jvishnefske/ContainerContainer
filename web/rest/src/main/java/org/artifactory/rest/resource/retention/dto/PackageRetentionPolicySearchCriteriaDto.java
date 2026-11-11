package org.artifactory.rest.resource.retention.dto;

import lombok.Builder;
import org.artifactory.policy.RepoAvailabilityStatus;

import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Chaitanya Vootla
 */
@Builder(toBuilder = true)
public record PackageRetentionPolicySearchCriteriaDto(
        List<String> packageTypes,
        List<String> repos,
        List<String> excludedRepos,
        List<String> includedPackages,
        List<String> excludedPackages,
        Boolean includeAllProjects,
        List<String> includedProjects,
        Boolean includeAllStages,
        List<String> includedStages,
        @PositiveOrZero
        Integer createdBeforeInDays,
        @PositiveOrZero
        Integer lastDownloadedBeforeInDays,
        @PositiveOrZero
        Integer createdBeforeInMonths,
        @PositiveOrZero
        Integer lastDownloadedBeforeInMonths,
        @PositiveOrZero
        Integer keepLastNVersions,
        Map<String, List<String>> includedProperties,
        Map<String, List<String>> excludedProperties,
        List<RepoAvailabilityStatus> reposWithStatus,
        List<String> includedPackageVersions,
        List<String> excludedPackageVersions,
        Set<String> includedFolderPaths,
        Set<String> excludedFolderPaths) {
    private static final int DEFAULT_DAYS = 365 * 2; // 2 years

    public PackageRetentionPolicySearchCriteriaDto {
        boolean hasInclusionBasedPropertyCondition = includedProperties != null && !includedProperties.isEmpty();
        if (isAllNullOrZero(keepLastNVersions, createdBeforeInMonths, lastDownloadedBeforeInMonths, createdBeforeInDays,
                lastDownloadedBeforeInDays ) &&
                !hasInclusionBasedPropertyCondition) {
            createdBeforeInDays = DEFAULT_DAYS;
            lastDownloadedBeforeInDays = DEFAULT_DAYS;
            keepLastNVersions = 0;
        }
    }

    private boolean isAllNullOrZero(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return false;
            }
        }
        return true;
    }
}