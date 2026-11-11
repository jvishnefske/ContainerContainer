package org.artifactory.rest.services.cleanup.mapper;

import lombok.experimental.UtilityClass;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicy;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicyConfig;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicySearchCriteria;
import org.artifactory.rest.resource.cleanup.dto.ReleaseBundleCleanupPolicyDto;
import org.artifactory.rest.resource.cleanup.dto.ReleaseBundleCleanupPolicySearchCriteriaDto;
import org.springframework.lang.NonNull;

@UtilityClass
public class ReleaseBundleCleanupPolicyDtoMapper {
    public BundleCleanupPolicy fromDto(@NonNull ReleaseBundleCleanupPolicyDto dto) {
        BundleCleanupPolicyConfig releaseBundleCleanupPolicyConfig = BundleCleanupPolicyConfig.builder()
                .durationInMinutes(dto.durationInMinutes())
                .searchCriteria(fromCriteriaDto(dto.searchCriteria()))
                .build();
        return BundleCleanupPolicy.<BundleCleanupPolicyConfig>builder()
                .key(dto.key())
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.RELEASE_BUNDLE)
                .config(releaseBundleCleanupPolicyConfig)
                .enabled(dto.enabled())
                .description(dto.description())
                .cronExp(dto.cronExp())
                .build();
    }

    public BundleCleanupPolicySearchCriteria fromCriteriaDto(
            ReleaseBundleCleanupPolicySearchCriteriaDto criteriaDto) {
        return BundleCleanupPolicySearchCriteria.builder()
                .includeAllProjects(criteriaDto.includeAllProjects())
                .includedProjects(criteriaDto.includedProjects())
                .excludePromotedEnvironments(criteriaDto.excludePromotedEnvironments())
                .createdBeforeInMonths(criteriaDto.createdBeforeInMonths())
                .createdBeforeInDays(criteriaDto.createdBeforeInDays())
                .releaseBundles(criteriaDto.releaseBundles().stream()
                        .map(bundleDto -> BundleCleanupPolicySearchCriteria.ReleaseBundle.builder()
                                .name(bundleDto.name())
                                .projectKey(bundleDto.projectKey())
                                .build()
                        )
                        .toList()
                )
                .build();
    }

    public ReleaseBundleCleanupPolicyDto toDto(@NonNull BundleCleanupPolicy policy) {
        return ReleaseBundleCleanupPolicyDto.builder()
                .key(policy.getKey())
                .itemType(policy.getItemType().getCode())
                .durationInMinutes(policy.getConfig().durationInMinutes())
                .description(policy.getDescription())
                .enabled(policy.isEnabled())
                .cronExp(policy.getCronExp())
                .searchCriteria(toCriteriaDto(policy.getConfig().searchCriteria()))
                .build();
    }

    public ReleaseBundleCleanupPolicySearchCriteriaDto toCriteriaDto(BundleCleanupPolicySearchCriteria criteria) {
        return ReleaseBundleCleanupPolicySearchCriteriaDto.builder()
                .includeAllProjects(criteria.includeAllProjects())
                .includedProjects(criteria.includedProjects())
                .excludePromotedEnvironments(criteria.excludePromotedEnvironments())
                .createdBeforeInMonths(criteria.createdBeforeInMonths())
                .createdBeforeInDays(criteria.createdBeforeInDays())
                .releaseBundles(criteria.releaseBundles().stream().map(bundle ->
                        ReleaseBundleCleanupPolicySearchCriteriaDto.ReleaseBundle.builder()
                                .name(bundle.name())
                                .projectKey(bundle.projectKey())
                                .build()
                ).toList())
                .build();
    }
}
