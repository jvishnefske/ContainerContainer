package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class ProjectBuildsMetadata {

    String buildInfoRepo;
    int buildsCount;
}
