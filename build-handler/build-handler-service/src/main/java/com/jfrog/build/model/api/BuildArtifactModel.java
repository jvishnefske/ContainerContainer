package com.jfrog.build.model.api;

import com.jfrog.build.model.BuildNodeRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildArtifactModel {

    private String repo;
    private String path;
    private String name;
    private String sha1;
    private String md5;
    private long size;

    public BuildArtifactModel(BuildNodeRecord artifact) {
        this.repo = artifact.repoKey();
        this.path = artifact.path();
        this.name = artifact.name();
        this.sha1 = artifact.sha1();
        this.md5 = artifact.md5();
        this.size = artifact.size();
    }
}
