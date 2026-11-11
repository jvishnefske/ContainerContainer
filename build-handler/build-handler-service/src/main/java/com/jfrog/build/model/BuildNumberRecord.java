package com.jfrog.build.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@AllArgsConstructor
public class BuildNumberRecord {

    String buildRepo;
    String buildName;
    String buildNumber;
    long started;
    String createdBy;
    List<BuildNumberBundleRecord> bundles;
}
