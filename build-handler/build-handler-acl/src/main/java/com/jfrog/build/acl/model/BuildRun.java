package com.jfrog.build.acl.model;

public interface BuildRun extends BuildId {

    String getCiUrl();

    String getReleaseStatus();

    String getReleaseRepoKey();

    @Override
    String toString();
}