package com.jfrog.build.acl.model;

import lombok.Builder;
import lombok.Value;

/**
 * The package metadata (BOM) resolved for a build artifact's repo path: the subset of Artifactory's metadata BOM
 * the build-content view needs to group artifacts into packages. A blank {@link #name} means the artifact has no
 * package identity (it is a standalone artifact).
 */
@Value
@Builder
public class BuildArtifactMetadata {

    String packageType;
    String name;
    String version;
    String packageId;
}
