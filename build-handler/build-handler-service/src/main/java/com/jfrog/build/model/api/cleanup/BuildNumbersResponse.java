package com.jfrog.build.model.api.cleanup;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor
@Value
public class BuildNumbersResponse {
    String repo;
    String buildName;
    List<BuildNumberEntry> buildNumbers;
    long nextStartedAfter;
}
