package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * A package or standalone artifact in a build's content view. Artifacts with the same
 * (packageType, name, version) are grouped into a single releasable.
 */
@Value
@Builder
@Jacksonized
public class BuildReleasableModel {

    String packageType;
    String packageId;
    String name;
    String version;
    String downloadPath;
    BuildSourceModel source;
}
