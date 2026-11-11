package com.jfrog.build.model.api;

import com.jfrog.build.model.api.PublishedModule;
import lombok.Data;

@Data
public class BuildModule {

    private final String moduleId;
    private final String numOfArtifacts;
    private final String numOfDependencies;
    private final String type;
    private final String buildId;
    private final String buildNumber;

    public BuildModule(PublishedModule module) {
        this.moduleId = module.getId();
        this.numOfArtifacts = module.getNumOfArtifact();
        this.numOfDependencies = module.getNumOfDependencies();
        this.type = module.getType();
        this.buildId = module.getBuildId();
        this.buildNumber = module.getBuildNumber();
    }
}
