package com.jfrog.build.model.api.cleanup;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor
@Value
public class BuildNumberEntry {
    String number;
    long started;
    String createdBy;
    List<BuildBundleEntry> releaseBundles;
}
