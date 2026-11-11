package com.jfrog.build.model.api;

import com.jfrog.build.acl.model.BuildId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@AllArgsConstructor
public class BuildIdSearchResults {
    List<BuildId> builds;
    long offset;
}