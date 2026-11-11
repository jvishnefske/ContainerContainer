package org.artifactory.engine;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.UriBuilder;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.request.ControlledCacheRedirectService;
import org.artifactory.fs.RepoResource;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.request.Request;
import org.artifactory.resource.RepoResourceInfo;
import org.artifactory.util.RepoPathUtils;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.springframework.stereotype.Service;

import java.net.URI;

import static org.artifactory.api.request.ArtifactoryHeaders.PARAM_FORCE_GET_STREAM;
import static org.artifactory.common.ConstantValues.artifactControlledCacheEnabled;
import static org.artifactory.engine.ControlledCacheConstants.SHA1_QUERY_PARAM;
import static org.artifactory.engine.ControlledCacheRedirectUtil.isCacheControlValidateRequest;

@AllArgsConstructor
@Service
@Slf4j
public class ControlledCacheRedirectServiceImpl implements ControlledCacheRedirectService {

    private static final String NO_REDIRECT_LOG_PREFIX = "Not redirecting to controlled cache. Reason:";

    private final RepositoryService repositoryService;

    @Override
    public boolean shouldRedirect(@NonNull Request request,
            @NonNull RepoResource resource, @Nullable RedirectType redirectType) {
        if (!artifactControlledCacheEnabled.getBoolean()) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Feature flag is disabled");
            return false;
        }
        //internal requests from package managers might need the original stream
        // (e.g: docker manifest, gems ...)
        if (Boolean.parseBoolean(request.getParameter(PARAM_FORCE_GET_STREAM))) {
            return false;
        }

        if (redirectType == null) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Redirect type is null");
            return false;
        }
        if (shouldPreventRedirectBasedOnRequest(request)) {
            return false;
        }
        if (shouldPreventRedirectBasedOnResource(resource)) {
            return false;
        }
        return true;
    }

    private static boolean shouldPreventRedirectBasedOnRequest(@NonNull Request request) {
        // No need to redirect HEAD requests, we already have all the information to respond
        if (request.isHeadOnly()) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Request is HEAD only");
            return true;
        }
        //for now range requests are not supported
        if (request.isRange()) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Request is range request");
            return true;
        }
        //check header X-JFrog-Cache-Request=validate
        if (!isCacheControlValidateRequest(request)) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Request is not a cache validation request");
            return true;
        }
        return false;
    }

    private boolean shouldPreventRedirectBasedOnResource(@NonNull RepoResource resource) {
        if (!resource.isFound()) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Resource not found");
            return true;
        }
        if (isRemoteRepoAndDoesNotStoreLocally(resource)) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Remote repo does not store artifacts locally");
            return true;
        }
        if (resource.isExpirable()) {
            log.debug(NO_REDIRECT_LOG_PREFIX + " Resource is expirable");
            return true;
        }
        return false;
    }

    @Override
    public URI createRedirectUri(@NonNull RepoResourceInfo repoResourceInfo) {
        String path = RepoPathUtils.removeDashCacheFromPath(repoResourceInfo.getRepoPath());
        return UriBuilder.fromUri("/artifactory").path(path)
                .queryParam(SHA1_QUERY_PARAM, repoResourceInfo.getSha1())
                .build();
    }

    /**
     * Checks if given repo is remote and does not support storing artifacts locally
     *
     * @param resource repo to check
     * @return true if given repo is remote and does *not* support storing artifacts locally, false otherwise
     */
    private boolean isRemoteRepoAndDoesNotStoreLocally(RepoResource resource) {
        RemoteRepoConfig remoteRepoConfig = repositoryService
                .getRemoteRepoConfigByKey(resource.getRepoPath().getRepoKey());
        if (remoteRepoConfig == null || remoteRepoConfig.getRepoTypeConfig() == null) {
            // Note a remote repo
            return false;
        }
        return !remoteRepoConfig.getRepoTypeConfig().isStoreArtifactsLocally();
    }
}