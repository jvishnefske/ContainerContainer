package com.jfrog.build.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import com.jfrog.build.acl.model.GeneralBuild;

import java.util.List;

@Value
@Builder
@AllArgsConstructor
public class BuildVersionSearchResults {

    List<GeneralBuild> builds;
    long offset;
}