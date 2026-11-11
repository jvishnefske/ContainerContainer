package com.jfrog.build.model.api.cleanup;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Fields were selected according to the needs of the build retention/cleanup feature.
 */
@AllArgsConstructor
@Value
public class BuildNameEntry {
    String buildName;
    String buildRepo;
    String projectKey;
    String projectName;
}
