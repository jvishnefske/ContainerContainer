package org.artifactory.resource;

import lombok.Getter;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;

@Getter
public class BlockedRepoResource implements RepoResource {

    private final RepoPath repoPath;
    private final String message;
    private final BlockedType blockedType;

    public BlockedRepoResource(RepoPath repoPath, String message, BlockedType blockedType) {
        this.repoPath = repoPath;
        this.message = message;
        this.blockedType = blockedType;
    }

    @Override
    public RepoPath getResponseRepoPath() {
        return null;
    }

    @Override
    public void setResponseRepoPath(RepoPath responsePath) {
    }

    @Override
    public RepoResourceInfo getInfo() {
        return null;
    }

    @Override
    public boolean isFound() {
        return true;
    }

    @Override
    public boolean isBlocked() {
        return true;
    }

    @Override
    public boolean isExactQueryMatch() {
        return false;
    }

    @Override
    public String getRepoKey() {
        return repoPath.getRepoKey();
    }

    @Override
    public String getPath() {
        return repoPath.getPath();
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public boolean isMetadata() {
        return false;
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public long getCacheAge() {
        return 0;
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public String getEtag() {
        return null;
    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public boolean isExpirable() {
        return false;
    }

    @Override
    public void expirable() {
    }

    public enum BlockedType {
        XRAY, CURATION
    }
}
