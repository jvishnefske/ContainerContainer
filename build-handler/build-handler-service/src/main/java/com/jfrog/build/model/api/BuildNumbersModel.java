package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BuildNumbersModel {
    private String project;
    private String buildName;
    private Long startedBefore;
    private Boolean excludeInReleaseBundles;
    private Long startedAfter;
    private Long limit;
    private Long releaseBundlesLimit;
}
