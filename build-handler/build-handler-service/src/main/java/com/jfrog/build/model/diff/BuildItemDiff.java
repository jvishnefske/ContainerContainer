package com.jfrog.build.model.diff;

import com.jfrog.build.acl.model.BuildRepoPath;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a build item which is used for builds diff comparison
 * <p>Build item diff can be either:<br/>
 * An artifact - The id is the artifact name and the baseId is the module baseId
 * (module id without a version, e.g: for module 'a:a:1.0' the baseId is 'a:a').<br/>
 * A dependency - The id is the dependency id and the baseId is the dependency base id
 * (dependency id without a version, e.g: for dependency 'ant:ant:4.1' the baseId is 'ant:ant').
 *
 * @author Shay Yaakov
 */
public class BuildItemDiff {

    private String id; // multi1-2.15-SNAPSHOT-sources.jar
    private String baseId; // org.jfrog.test:multi1
    private String moduleId; // org.jfrog.test:multi1:1.0-SNAPSHOT
    private String type; // java-source-jar
    private String sha1;
    private String md5;
    private BuildRepoPath repoPath;
    private boolean internalDependency;
    private Set<String> scopes;

    private BuildItemDiff(Builder builder) {
        this.id = builder.id;
        this.baseId = builder.baseId;
        this.type = builder.type;
        this.sha1 = builder.sha1;
        this.md5 = builder.md5;
        this.moduleId = builder.moduleId;
        this.repoPath = builder.repoPath;
        this.internalDependency = builder.internalDependency;
        this.scopes = builder.scopes;
    }

    public String getId() {
        return id;
    }

    public String getBaseId() {
        return baseId;
    }

    public String getModuleId() {
        return moduleId;
    }

    public String getType() {
        return type;
    }

    public String getSha1() {
        return sha1;
    }

    public String getMd5() {
        return md5;
    }

    public BuildRepoPath getRepoPath() {
        return repoPath;
    }

    public boolean isInternalDependency() {
        return internalDependency;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuildItemDiff that = (BuildItemDiff) o;
        return internalDependency == that.internalDependency && Objects.equals(id, that.id) &&
                Objects.equals(baseId, that.baseId) && Objects.equals(moduleId, that.moduleId) &&
                Objects.equals(type, that.type) && Objects.equals(sha1, that.sha1) && Objects.equals(md5, that.md5) &&
                Objects.equals(repoPath, that.repoPath) && Objects.equals(scopes, that.scopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, baseId, moduleId, type, sha1, md5, repoPath, internalDependency, scopes);
    }

    public static class Builder {
        private String id; // multi1-2.15-SNAPSHOT-sources.jar
        private String baseId; // org.jfrog.test:multi1
        private String moduleId; // org.jfrog.test:multi1:1.0-SNAPSHOT
        private String type; // java-source-jar
        private String sha1;
        private String md5;
        private BuildRepoPath repoPath;
        private boolean internalDependency;
        private Set<String> scopes;

        public Builder(String id, String fullId, String moduleId) {
            this.id = id;
            this.baseId = StringUtils.substringBeforeLast(fullId, ":");
            this.moduleId = moduleId;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder sha1(String sha1) {
            this.sha1 = sha1;
            return this;
        }

        public Builder md5(String md5) {
            this.md5 = md5;
            return this;
        }

        public Builder repoPath(BuildRepoPath repoPath) {
            this.repoPath = repoPath;
            return this;
        }

        public Builder internalDependency(boolean internalDependency) {
            this.internalDependency = internalDependency;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public BuildItemDiff build() {
            if (StringUtils.isBlank(id)) {
                throw new IllegalArgumentException("Please provide an id.");
            }

            if (StringUtils.isBlank(baseId)) {
                throw new IllegalArgumentException("Please provide full id.");
            }

            if (StringUtils.isBlank(moduleId)) {
                throw new IllegalArgumentException("Please provide module id.");
            }

            return new BuildItemDiff(this);
        }
    }
}
