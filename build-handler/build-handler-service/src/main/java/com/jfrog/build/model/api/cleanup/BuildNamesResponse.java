package com.jfrog.build.model.api.cleanup;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor
@Value
public class BuildNamesResponse {
    List<BuildNameEntry> builds;
}
