package org.artifactory.rest.resource.archive.dto;

import lombok.Builder;
import org.artifactory.rest.resource.retention.dto.PackageRetentionPolicySearchCriteriaDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@Builder(toBuilder = true)
public record PackageArchivePolicyDto(
        String key,
        @Size(max = 256, message = "Description length must be no more than 256 characters")
        String description,
        String cronExp,
        String projectKey,
        Integer durationInMinutes,
        Integer expirationTimeInMonths,
        boolean enabled,
        boolean skipTrashcan,
        @Valid
        PackageRetentionPolicySearchCriteriaDto searchCriteria) {
}