package com.jfrog.build.acl.model;

import jakarta.annotation.Nullable;
import lombok.Value;
import org.jfrog.build.api.Artifact;

@Value
public class ArtifactoryBuildArtifact {

    @Nullable
    Artifact artifact;

    @Nullable
    BuildFileInfo fileInfo;
}
