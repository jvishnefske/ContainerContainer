package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Paginated response for the build-content view: the releasables (packages and standalone artifacts) plus the total
 * count before pagination.
 */
@Value
@Builder
@Jacksonized
public class BuildContentResponse {

    List<BuildReleasableModel> releasables;
    long total;
}
