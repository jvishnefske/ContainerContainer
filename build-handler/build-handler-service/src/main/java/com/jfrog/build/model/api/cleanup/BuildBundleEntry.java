package com.jfrog.build.model.api.cleanup;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class BuildBundleEntry {
    String projectKey;
    String name;
    String version;
}
