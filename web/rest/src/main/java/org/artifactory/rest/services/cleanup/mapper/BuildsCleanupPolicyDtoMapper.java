package org.artifactory.rest.services.cleanup.mapper;

import lombok.experimental.UtilityClass;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicy;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicyConfig;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicySearchCriteria;
import org.artifactory.rest.resource.cleanup.dto.BuildsCleanupPolicyDto;
import org.artifactory.rest.resource.cleanup.dto.BuildsCleanupPolicySearchCriteriaDto;
import org.springframework.lang.NonNull;

@UtilityClass
public class BuildsCleanupPolicyDtoMapper {
    public BuildCleanupPolicy fromDto(@NonNull BuildsCleanupPolicyDto dto) {
        BuildCleanupPolicyConfig buildsCleanupPolicyConfig = BuildCleanupPolicyConfig.builder()
                .durationInMinutes(dto.durationInMinutes())
                .deleteArtifacts(dto.deleteArtifacts())
                .skipTrashcan(dto.skipTrashcan())
                .searchCriteria(fromCriteriaDto(dto.searchCriteria()))
                .build();
        return BuildCleanupPolicy.<BuildCleanupPolicyConfig>builder()
                .key(dto.key())
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.BUILD)
                .config(buildsCleanupPolicyConfig)
                .enabled(dto.enabled())
                .description(dto.description())
                .cronExp(dto.cronExp())
                .build();
    }

    public BuildCleanupPolicySearchCriteria fromCriteriaDto(
            BuildsCleanupPolicySearchCriteriaDto criteriaDto) {
        return BuildCleanupPolicySearchCriteria.builder()
                .includeAllProjects(criteriaDto.includeAllProjects())
                .includedProjects(criteriaDto.includedProjects())
                .includedBuilds(criteriaDto.includedBuilds().stream()
                        .map(bundleDto -> BuildCleanupPolicySearchCriteria.Build.builder()
                                .name(bundleDto.name())
                                .projectKey(bundleDto.projectKey())
                                .build()
                        )
                        .toList()
                )
                .excludeBuildsWithReleaseBundles(criteriaDto.excludeBuildsWithReleaseBundles())
                .createdBeforeInMonths(criteriaDto.createdBeforeInMonths())
                .createdBeforeInDays(criteriaDto.createdBeforeInDays())
                .build();
    }

    public BuildsCleanupPolicyDto toDto(@NonNull BuildCleanupPolicy policy) {
        return BuildsCleanupPolicyDto.builder()
                .key(policy.getKey())
                .itemType(policy.getItemType().getCode())
                .skipTrashcan(policy.getConfig().skipTrashcan())
                .deleteArtifacts(policy.getConfig().deleteArtifacts())
                .durationInMinutes(policy.getConfig().durationInMinutes())
                .description(policy.getDescription())
                .enabled(policy.isEnabled())
                .cronExp(policy.getCronExp())
                .searchCriteria(toCriteriaDto(policy.getConfig().searchCriteria()))
                .build();
    }

    public BuildsCleanupPolicySearchCriteriaDto toCriteriaDto(BuildCleanupPolicySearchCriteria criteria) {
        return BuildsCleanupPolicySearchCriteriaDto.builder()
                .includeAllProjects(criteria.includeAllProjects())
                .includedProjects(criteria.includedProjects())
                .includedBuilds(criteria.includedBuilds().stream()
                        .map(bundleDto -> BuildsCleanupPolicySearchCriteriaDto.Builds.builder()
                                .name(bundleDto.name())
                                .projectKey(bundleDto.projectKey())
                                .build()
                        )
                        .toList()
                )
                .excludeBuildsWithReleaseBundles(criteria.excludeBuildsWithReleaseBundles())
                .createdBeforeInDays(criteria.createdBeforeInDays())
                .createdBeforeInMonths(criteria.createdBeforeInMonths())
                .build();
    }
}
