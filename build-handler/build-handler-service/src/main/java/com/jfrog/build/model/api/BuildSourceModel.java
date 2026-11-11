package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Build source information for a releasable in the build-content view.
 */
@Value
@Builder
@Jacksonized
public class BuildSourceModel {

    String buildName;
    String buildNumber;
    String buildRepository;
    String buildStarted;
    String moduleName;
    String moduleType;
    String sourceType;
}
