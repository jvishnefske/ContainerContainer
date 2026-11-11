package com.jfrog.build.acl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class BuildFileInfo {

    Long nodeId;
    BuildRepoPath repoPath;

    String createdBy;
    String modifiedBy;
    String originalMd5;
    String actualMd5;
    String originalSha1;
    String actualSha1;
    String sha2;

    long size;
    long created;
    long modified;
    long updated;

    String buildDependencyName;

    @NonNull
    public String getRepoKey() {
        return repoPath.getRepoKey();
    }

    @NonNull
    public String getRelativePath() {
        return repoPath.getRelativePath();
    }

    @NonNull
    public String getName() {
        return repoPath.getName();
    }
}
