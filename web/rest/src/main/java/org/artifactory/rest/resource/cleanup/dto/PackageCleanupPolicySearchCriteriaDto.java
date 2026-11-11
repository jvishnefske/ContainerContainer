package org.artifactory.rest.resource.cleanup.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import org.artifactory.policy.RepoAvailabilityStatus;
import org.artifactory.rest.common.provider.BlockFloatToIntJsonDeserializer;

import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author morm
 */
@Builder(toBuilder = true)
public record PackageCleanupPolicySearchCriteriaDto(
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
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer createdBeforeInMonths,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer lastDownloadedBeforeInMonths,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer createdBeforeInDays,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer lastDownloadedBeforeInDays,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer keepLastNVersions,
        List<RepoAvailabilityStatus> reposWithStatus,
        Map<String, List<String>> includedProperties,
        Map<String, List<String>> excludedProperties,
        List<String> includedPackageVersions,
        List<String> excludedPackageVersions,
        Set<String> includedFolderPaths,
        Set<String> excludedFolderPaths) {
    private static final int DEFAULT_DAYS = 365 * 2; // 2 years

    public PackageCleanupPolicySearchCriteriaDto {
        boolean hasInclusionBasedPropertyCondition = includedProperties != null && !includedProperties.isEmpty();

        // Set defaults only if all three condition types are absent
        if (isAllNullOrZero(keepLastNVersions, createdBeforeInMonths, lastDownloadedBeforeInMonths, createdBeforeInDays,
                lastDownloadedBeforeInDays) &&
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