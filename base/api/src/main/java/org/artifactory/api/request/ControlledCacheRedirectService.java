package org.artifactory.api.request;

import org.artifactory.fs.RepoResource;
import org.artifactory.request.Request;
import org.artifactory.resource.RepoResourceInfo;
import org.jfrog.storage.binstore.ifc.RedirectType;

import java.net.URI;

public interface ControlledCacheRedirectService {

    /**
     * Controlled cache redirect is enabled if
     *    -- Feature flag is on
     *    -- Force-get-stream query parameter is not set (used by internal package manager requests)
     *    -- Other redirect type is not configured
     *    -- Request contains 'X-JFrog-Cache-Request: validate' header
     *    -- Request is not HEAD
     *    -- Request is not a range request
     *    -- Resource is found
     *    -- If remote, store artifacts locally is enabled for repository
     *    -- Artifact is not expirable (See: {@link org.artifactory.fs.RepoResource#isExpirable()})
     * @return true if enabled, false otherwise
     */
    boolean shouldRedirect(Request request, RepoResource resource, RedirectType redirectType);

    /**
     * Create redirect uri for given resource.
     * Redirect url format: base_path/<local_repo_path>?key=<sha1>
     * Removes "-cache" from the repo key if present for two reasons:
     * 1. On the one hand in case the requested artifact was not in the remote repository cache, we would redirect to
     * the remote repository URL, and on the other hand if it was in the remote repository cache, we would redirect to
     * the remote repository cache URL. For consistency, we decided to always redirect to the remote repository URL.
     * regardless of the cache state.
     * 2. In the edge case the artifact is purged from the cache immediately after the redirect to the caching proxy, if
     * the URL is set to the remote repository cache, the user would get 404. To prevent this, it is better to always
     * redirect to the remote repository URL, thus in this edge case the artifact would simply be downloaded to the
     * cache again and served.
     * @param repoResourceInfo resource to create redirect url.
     * @return Created redirect uri
     */
    URI createRedirectUri(RepoResourceInfo repoResourceInfo);
}