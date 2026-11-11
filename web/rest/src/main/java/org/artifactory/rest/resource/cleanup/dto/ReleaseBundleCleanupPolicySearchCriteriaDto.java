package org.artifactory.rest.resource.cleanup.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import org.artifactory.rest.common.provider.BlockFloatToIntJsonDeserializer;

import java.util.List;

/**
 * @author Chaitanya Vootla
 */
@Builder(toBuilder = true)
public record ReleaseBundleCleanupPolicySearchCriteriaDto(
        Boolean includeAllProjects,
        List<String> includedProjects,
        List<ReleaseBundle> releaseBundles,
        List<String> excludePromotedEnvironments,
        Boolean excludeAllPromotedEnvironments,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer createdBeforeInMonths,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer createdBeforeInDays) {

    @Builder(toBuilder = true)
    public record ReleaseBundle(
            String name,
            String projectKey
    ) {}

    private static final int DEFAULT_DAYS = 365 * 2; // Default to 2 years

    public ReleaseBundleCleanupPolicySearchCriteriaDto {
        if (isAllNullOrZero(createdBeforeInMonths, createdBeforeInDays)) {
            createdBeforeInDays = DEFAULT_DAYS;
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