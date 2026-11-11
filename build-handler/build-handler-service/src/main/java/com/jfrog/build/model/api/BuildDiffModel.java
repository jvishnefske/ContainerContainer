package com.jfrog.build.model.api;

import lombok.Data;

import java.util.List;

@Data
public class BuildDiffModel {

    private List<ModuleArtifactModel> artifacts;
    private List<ModuleDependencyModel> dependencies;
    private List<BuildPropsModel> props;
}
