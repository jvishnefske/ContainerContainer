package com.jfrog.build.model.api;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ModuleArtifactModel {

    private String repoKey;
    private String path;
    private String name;
    private String type;
    private String repoPath;

    @Builder.Default
    private List<BaseArtifact> actions = new ArrayList<>();
    private String status;
    private String moduleName;
    private String prevName;
}
