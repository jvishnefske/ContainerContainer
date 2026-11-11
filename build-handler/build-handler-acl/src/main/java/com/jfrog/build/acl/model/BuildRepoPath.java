package com.jfrog.build.acl.model;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.Value;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.Strings.CS;
import static org.jfrog.client.util.PathUtils.getFileName;

@Value
public class BuildRepoPath {

    String repoKey;
    String relativePath;
    String name;

    /**
     * Want to use this constructor?
     * <p>
     * Disclaimer: READ THE FOLLOWING CAUTION:
     * <p>
     * There is a lot of confusion in terms of what the "path" means.
     * Some Artifactory classes mean a "directory path" (e.g. Node entity).
     * (a "repo path" without a repository key and filename),
     * <p>
     * Other Artifactory classes mean a "relative path" (e.g. RepoPath DTO).
     * (a "repo path" without a repository key).
     * <p>
     * This is the 1st case.
     */
    public BuildRepoPath(
            @NonNull String repoKey,
            @Nullable String directoryPath,
            @Nullable String name) {
        validateRepoKey(repoKey);
        this.repoKey = repoKey.trim();
        this.relativePath = mergePathAndName(directoryPath, name);
        this.name = trimSlashes(name);
    }

    /**
     * Want to use this constructor?
     * <p>
     * Disclaimer: READ THE FOLLOWING CAUTION:
     * <p>
     * There is a lot of confusion in terms of what the "path" means.
     * Some Artifactory classes mean a "directory path" (e.g. Node entity).
     * (a "repo path" without a repository key and filename),
     * <p>
     * Other Artifactory classes mean a "relative path" (e.g. RepoPath DTO).
     * (a "repo path" without a repository key).
     * <p>
     * This is the 2nd case.
     */
    public BuildRepoPath(
            @NonNull String repoKey,
            @Nullable String relativePath) {
        validateRepoKey(repoKey);
        this.repoKey = repoKey.trim();
        this.relativePath = trimSlashes(relativePath);
        this.name = getFileName(this.relativePath);
    }

    @NonNull
    @Override
    public String toString() {
        return repoKey + "/" + relativePath;
    }

    @NonNull
    private static String mergePathAndName(
            @Nullable String path,
            @Nullable String name) {
        path = trimSlashes(path);
        name = trimSlashes(name);
        if (isBlank(path) || CS.equals(path, ".")) {
            return name;
        }
        return isNotBlank(name) ? path + "/" + name : path;
    }

    @NonNull
    private static String trimSlashes(@Nullable String path) {
        return trimToEmpty(path).replaceAll("^/+", "").replaceAll("/+$", ""); // NOSONAR
    }

    private static void validateRepoKey(@NonNull String repoKey) {
        if (isBlank(repoKey)) {
            throw new IllegalArgumentException("Repository key cannot be empty");
        }
    }
}