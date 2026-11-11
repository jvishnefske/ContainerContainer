package org.artifactory.repo.remote.interceptor;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.methods.HttpRequestBase;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.HeadersMultiMap;
import org.artifactory.request.Request;
import org.springframework.stereotype.Component;

import static org.artifactory.api.request.ArtifactoryHeaders.EMAIL;
import static org.artifactory.api.request.ArtifactoryHeaders.USER;
import static org.artifactory.util.HttpUtils.addHeader;

/**
 * @author Andrey Tashlitsky
 */
@Component
@RequiredArgsConstructor
public class RemoteSendContextInterceptor implements RemoteRepoInterceptor {

    private final RepositoryService repositoryService;
    private final UserGroupService userGroupService;

    @Override
    public void beforeRemoteHttpMethodExecution(HttpRequestBase outgoingRequest, @Nullable Request incomingRequest,
            HeadersMultiMap headers, RepoPath repoPath) {
        if (shouldSendContext(incomingRequest, repoPath)) {
            addHeader(incomingRequest, headers, USER, userGroupService.currentUser().getUsername());
            addHeader(incomingRequest, headers, EMAIL, userGroupService.currentUser().getEmail());
        }
    }

    private boolean shouldSendContext(Request incomingRequest, RepoPath repoPath) {
        if (incomingRequest == null) {
            return false;
        }
        RemoteRepoConfig repoConfig = repositoryService.remoteRepoConfigByKey(repoPath.getRepoKey());
        return repoConfig != null && repoConfig.getRepoTypeConfig().isSendContext();
    }
}
