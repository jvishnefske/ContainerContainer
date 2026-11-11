package com.jfrog.build.acl.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.artifactory.threadlocal.cache.RepositoryNameProvider;

@Value
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BuildNode implements RepositoryNameProvider {

    private static final int MAX_ALLOWED_USERNAME_LENGTH = 64;

    @EqualsAndHashCode.Include
    String repo;

    @EqualsAndHashCode.Include
    String path;

    @EqualsAndHashCode.Include
    String name;

    long nodeId;
    short depth;
    long created;
    long modified;
    long updated;
    boolean file;
    String createdBy;
    String modifiedBy;

    // File-only attributes
    long length;
    String sha1Actual;
    String sha1Original;
    String sha2;
    String md5Actual;
    String md5Original;

    @Override
    public String toString() {
        return "Node" + "{" + asString() + '}';
    }

    private String asString() {
        return "nodeId=" + nodeId +
                ", file=" + file +
                ", repo='" + repo + '\'' +
                ", path='" + path + '\'' +
                ", name='" + name + '\'';
    }
}
