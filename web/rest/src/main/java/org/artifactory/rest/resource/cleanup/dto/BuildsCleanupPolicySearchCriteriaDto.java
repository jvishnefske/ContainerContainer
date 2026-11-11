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
public record BuildsCleanupPolicySearchCriteriaDto(
        Boolean includeAllProjects,
        List<String> includedProjects,
        List<Builds> includedBuilds,
        Boolean excludeBuildsWithReleaseBundles,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer createdBeforeInMonths,
        @PositiveOrZero
        @JsonDeserialize(using = BlockFloatToIntJsonDeserializer.class)
        Integer createdBeforeInDays) {

    @Builder(toBuilder = true)
    public record Builds(
            String name,
            String projectKey
    ) {}

    private static final int DEFAULT_DAYS = 365 * 2; // 2 Years

    public BuildsCleanupPolicySearchCriteriaDto {
        if (isAllNullOrZero(createdBeforeInDays, createdBeforeInMonths)) {
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