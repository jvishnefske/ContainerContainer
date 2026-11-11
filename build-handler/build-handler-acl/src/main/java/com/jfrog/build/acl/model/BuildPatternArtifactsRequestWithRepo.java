package com.jfrog.build.acl.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jfrog.build.api.dependency.BuildPatternArtifactsRequest;

@NoArgsConstructor
public class BuildPatternArtifactsRequestWithRepo extends BuildPatternArtifactsRequest {

    @Getter
    @Setter
    private String buildRepo;

    public BuildPatternArtifactsRequestWithRepo(
            String buildName,
            String buildNumber,
            String buildRepo,
            String project) {
        super(buildName, buildNumber,project);
        this.buildRepo = buildRepo;
    }
}
