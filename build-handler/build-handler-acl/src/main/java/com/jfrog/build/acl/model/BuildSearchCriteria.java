package com.jfrog.build.acl.model;

public enum BuildSearchCriteria {

    IN_ARTIFACTS,
    IN_DEPENDENCIES,
    IN_BOTH;

    public final boolean searchInArtifacts() {
        return this == IN_ARTIFACTS || this == IN_BOTH;
    }

    public final boolean searchInDependencies() {
        return this == IN_DEPENDENCIES || this == IN_BOTH;
    }
}
