package org.artifactory.rest.services.cleanup.mapper;

import lombok.experimental.UtilityClass;
import org.artifactory.policy.PackagePolicySearchCriteria;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicyConfig;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicyDto;
import org.artifactory.rest.resource.cleanup.dto.PackageCleanupPolicySearchCriteriaDto;
import org.artifactory.util.CollectionUtils;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class PackageCleanupPolicyDtoMapper {

    public PackageCleanupPolicy fromDto(@NonNull PackageCleanupPolicyDto dto) {
        PackageCleanupPolicyConfig packageCleanupPolicyConfig = PackageCleanupPolicyConfig.builder()
                .skipTrashcan(dto.skipTrashcan())
                .durationInMinutes(dto.durationInMinutes())
                .searchCriteria(fromCriteriaDto(dto.searchCriteria()))
                .build();
        return PackageCleanupPolicy.builder()
                .key(dto.key())
                .type(PolicyType.CLEANUP)
                .itemType(PolicyItemType.PACKAGE)
                .config(packageCleanupPolicyConfig)
                .enabled(dto.enabled())
                .description(dto.description())
                .cronExp(dto.cronExp())
                .build();
    }

    private PackageCleanupPolicyDto.PackageCleanupPolicyDtoBuilder buildBaseDtoBuilder(PackageCleanupPolicy policy) {
        return PackageCleanupPolicyDto.builder()
                .key(policy.getKey())
                .itemType(policy.getItemType().getCode())
                .skipTrashcan(policy.getConfig().skipTrashcan())
                .durationInMinutes(policy.getConfig().durationInMinutes())
                .description(policy.getDescription())
                .enabled(policy.isEnabled())
                .cronExp(policy.getCronExp());
    }

    public PackageCleanupPolicyDto toDto(@NonNull PackageCleanupPolicy policy) {
        return buildBaseDtoBuilder(policy)
                .searchCriteria(toCriteriaDto(policy.getConfig().searchCriteria()))
                .build();
    }

    public PackageCleanupPolicyDto toViewDto(@NonNull PackageCleanupPolicy policy) {
        return buildBaseDtoBuilder(policy)
                .searchCriteria(toViewCriteriaDto(policy.getConfig().searchCriteria()))
                .build();
    }

    private PackageCleanupPolicySearchCriteriaDto.PackageCleanupPolicySearchCriteriaDtoBuilder buildBaseCriteriaDto(
            PackagePolicySearchCriteria criteria) {

        return PackageCleanupPolicySearchCriteriaDto.builder()
                .repos(criteria.getRepos())
                .excludedRepos(criteria.getExcludedRepos())
                .includedPackages(criteria.getIncludedPackages())
                .excludedPackages(criteria.getExcludedPackages())
                .includeAllProjects(criteria.getIncludeAllProjects())
                .includedProjects(criteria.getIncludedProjects())
                .includeAllStages(determineIncludeAllStages(criteria.getIncludeAllStages(),
                        criteria.getIncludedStages()))
                .includedStages(criteria.getIncludedStages())
                .packageTypes(criteria.getPackageTypes())
                .createdBeforeInMonths(criteria.getCreatedBeforeInMonths())
                .lastDownloadedBeforeInMonths(criteria.getLastDownloadedBeforeInMonths())
                .includedProperties(criteria.getIncludedProperties())
                .excludedProperties(criteria.getExcludedProperties())
                .createdBeforeInDays(criteria.getCreatedBeforeInDays())
                .lastDownloadedBeforeInDays(criteria.getLastDownloadedBeforeInDays())
                .keepLastNVersions(criteria.getKeepLastNVersions())
                .includedPackageVersions(addIncludedPackageVersions(criteria.getIncludedPackageVersions()))
                .excludedPackageVersions(addExcludedPackageVersions(criteria.getExcludedPackageVersions()))
                .includedFolderPaths(criteria.getIncludedFolderPaths())
                .excludedFolderPaths(criteria.getExcludedFolderPaths());
    }

    public PackageCleanupPolicySearchCriteriaDto toCriteriaDto(PackagePolicySearchCriteria criteria) {
        return buildBaseCriteriaDto(criteria).build();
    }

    public PackageCleanupPolicySearchCriteriaDto toViewCriteriaDto(PackagePolicySearchCriteria criteria) {
        return buildBaseCriteriaDto(criteria)
                .reposWithStatus(criteria.getReposWithStatus())
                .build();
    }

    public PackagePolicySearchCriteria fromCriteriaDto(PackageCleanupPolicySearchCriteriaDto criteriaDto) {
        PackagePolicySearchCriteria.PackagePolicySearchCriteriaBuilder builder = PackagePolicySearchCriteria.builder()
                .repos(criteriaDto.repos())
                .excludedRepos(criteriaDto.excludedRepos())
                .includedPackages(criteriaDto.includedPackages())
                .excludedPackages(criteriaDto.excludedPackages())
                .includeAllProjects(criteriaDto.includeAllProjects())
                .includedProjects(criteriaDto.includedProjects())
                .includeAllStages(determineIncludeAllStages(criteriaDto.includeAllStages(),
                        criteriaDto.includedStages()))
                .includedStages(criteriaDto.includedStages())
                .packageTypes(criteriaDto.packageTypes())
                .keepLastNVersions(criteriaDto.keepLastNVersions())
                .includedProperties(criteriaDto.includedProperties())
                .lastDownloadedBeforeInMonths(criteriaDto.lastDownloadedBeforeInMonths())
                .createdBeforeInMonths(criteriaDto.createdBeforeInMonths())
                .lastDownloadedBeforeInDays(criteriaDto.lastDownloadedBeforeInDays())
                .createdBeforeInDays(criteriaDto.createdBeforeInDays())
                .excludedProperties(criteriaDto.excludedProperties())
                .includedPackageVersions(addIncludedPackageVersions(criteriaDto.includedPackageVersions()))
                .excludedPackageVersions(addExcludedPackageVersions(criteriaDto.excludedPackageVersions()))
                .includedFolderPaths(trimFolderPaths(criteriaDto.includedFolderPaths()))
                .excludedFolderPaths(trimFolderPaths(criteriaDto.excludedFolderPaths()));

        return builder.build();
    }

    private static List<String> addExcludedPackageVersions(List<String> excludedPackageVersions) {
        return excludedPackageVersions != null
                ? excludedPackageVersions
                : List.of();
    }

    private static List<String> addIncludedPackageVersions(List<String> includedPackageVersions) {
        return includedPackageVersions != null && !includedPackageVersions.isEmpty()
                ? includedPackageVersions
                : List.of("**");
    }

    private static Set<String> trimFolderPaths(Set<String> paths) {
        if (paths == null) {
            return null;
        }
        return paths.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    private static Boolean determineIncludeAllStages(Boolean includeAllStages, List<String> includeStages){
        // if both includeAllStages and includedStages are null, default to includeAllStages=true
        return includeAllStages == null && CollectionUtils.isNullOrEmpty(includeStages) ? Boolean.TRUE :
                includeAllStages;
    }
}