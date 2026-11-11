package org.artifactory.rest.resource.cleanup.dto;

import lombok.Builder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@Builder(toBuilder = true)
public record PackageCleanupPolicyDto(
        String key,
        @Size(max = 256, message = "Description length must be no more than 256 characters")
        String description,
        String cronExp,
        String projectKey,
        String itemType,
        Integer durationInMinutes,
        boolean enabled,
        boolean skipTrashcan,
        @Valid
        PackageCleanupPolicySearchCriteriaDto searchCriteria) {
}