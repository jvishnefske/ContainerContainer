package org.artifactory.rest.resource.cleanup.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder(toBuilder = true)
public record BuildsCleanupPolicyDto (
        String key,
        @Size(max = 256, message = "Description length must be no more than 256 characters")
        String description,
        String cronExp,
        String projectKey,
        String itemType,
        Integer durationInMinutes,
        boolean enabled,
        boolean deleteArtifacts,
        boolean skipTrashcan,
        @Valid
        BuildsCleanupPolicySearchCriteriaDto searchCriteria) {
}
