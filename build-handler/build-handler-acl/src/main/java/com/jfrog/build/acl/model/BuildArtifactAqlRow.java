package com.jfrog.build.acl.model;

import lombok.Value;

import java.util.Date;

@Value
public class BuildArtifactAqlRow {

    Long nodeId;

    String repoKey;
    String directoryPath;
    String name;

    String createdBy;
    String modifiedBy;
    String originalMd5;
    String actualMd5;
    String originalSha1;
    String actualSha1;
    String sha2;

    long size;

    Date created;
    Date modified;
    Date updated;
}
