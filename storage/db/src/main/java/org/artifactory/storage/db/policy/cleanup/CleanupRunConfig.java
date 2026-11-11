package org.artifactory.storage.db.policy.cleanup;

import lombok.Builder;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder(toBuilder = true)
public record CleanupRunConfig(
        long created,
        String createdBy,
        Long modified,
        String modifiedBy,
        String cronExp,
        String description,
        Boolean skipTrashCan,
        Integer durationInMinutes,
        List<String> packageTypes,
        List<String> repos,
        List<String> excludedRepos,
        List<String> includedPackages,
        List<String> excludedPackages,
        Boolean includeAllProjects,
        List<String> includedProjects,
        Integer createdBeforeInMonths,
        Integer lastDownloadedBeforeInMonths,
        Integer createdBeforeInDays,
        Integer lastDownloadedBeforeInDays,
        Integer keepLastNVersions,
        Map<String, List<String>> includedProperties,
        Map<String, List<String>> excludedProperties,
        List<String> releaseBundles,
        List<String> excludePromotedEnvironments,
        List<String> buildPatterns,
        Boolean deleteBuildArtifacts,
        List<String> includedPackageVersions,
        List<String> excludedPackageVersions,
        Boolean includeAllStages,
        List<String> includedStages,
        Set<String> includedFolderPaths,
        Set<String> excludedFolderPaths
)
{
}
