package com.jfrog.build.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class BuildNumberBundleRecord {

    String bundleRepo;
    String bundleName;
    String bundleVersion;
}
