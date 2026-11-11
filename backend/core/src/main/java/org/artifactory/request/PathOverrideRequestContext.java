package org.artifactory.request;

import jakarta.annotation.Nonnull;
import lombok.NonNull;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.fs.RepoResource;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;

import java.util.Map;

/**
 * Delegating wrapper that overrides only the resource path
 * while preserving all other state from the original context.
 *
 * @author Shahaf Golan
 */
public class PathOverrideRequestContext implements InternalRequestContext {

    private final InternalRequestContext delegate;
    private final RepoPath overriddenRepoPath;

    public PathOverrideRequestContext(InternalRequestContext delegate, RepoPath overriddenRepoPath) {
        this.delegate = delegate;
        this.overriddenRepoPath = overriddenRepoPath;
    }

    @Override
    public String getResourcePath() {
        return overriddenRepoPath.getPath();
    }

    @Override
    public String getPath() {
        return overriddenRepoPath.getPath();
    }

    @Override
    public String getRepoKey() {
        return overriddenRepoPath.getRepoKey();
    }

    @Override
    public boolean isFromAnotherArtifactory() {
        return delegate.isFromAnotherArtifactory();
    }

    @Override
    public Properties getProperties() {
        return delegate.getProperties();
    }

    @Override
    public Request getRequest() {
        return delegate.getRequest();
    }

    @Override
    public void setAttribute(String name, String value) {
        delegate.setAttribute(name, value);
    }

    @Override
    public String getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Nonnull
    @Override
    public RepoPath alterPath(
            CloseableHttpResponse response, RepoPath repoPath) {
        return delegate.alterPath(response, repoPath);
    }

    @Override
    public boolean isSkipCurationOnResource() {
        return delegate.isSkipCurationOnResource();
    }

    @Override
    public void setSkipCurationOnResource(boolean skip) {
        delegate.setSkipCurationOnResource(skip);
    }

    @Override
    public boolean isForceExpiryCheck() {
        return delegate.isForceExpiryCheck();
    }

    @Override
    public @NonNull Map<String, String> getRequestHeaders() {
        return delegate.getRequestHeaders();
    }

    @Override
    public boolean shouldUnExpireCachedResource(RepoResource resource) {
        return delegate.shouldUnExpireCachedResource(resource);
    }

    @Override
    public String getServletContextUrl() {
        return delegate.getServletContextUrl();
    }

    @Override
    public String getZipResourcePath() {
        return delegate.getZipResourcePath();
    }
}
