package com.jfrog.build.model.api;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BaseArtifact {

    private String name;
    private String repoKey;
    private String path;
    private String param;

    public BaseArtifact(String name) {
        this.name = name;
    }
}
