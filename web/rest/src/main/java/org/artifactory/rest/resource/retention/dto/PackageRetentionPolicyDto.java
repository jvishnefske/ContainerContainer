package org.artifactory.rest.resource.retention.dto;

import lombok.Builder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@Builder(toBuilder = true)
public record PackageRetentionPolicyDto(
        String key,
        @Size(max = 256, message = "Description length must be no more than 256 characters")
        String description,
        String cronExp,
        String projectKey,
        Integer durationInMinutes,
        boolean enabled,
        boolean skipTrashcan,
        @Valid
        PackageRetentionPolicySearchCriteriaDto searchCriteria) {
}