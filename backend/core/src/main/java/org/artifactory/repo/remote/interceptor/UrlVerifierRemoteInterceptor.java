package org.artifactory.repo.remote.interceptor;

import jakarta.validation.constraints.NotNull;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.UrlVerifier;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies if download is allowed for remote repo.
 * Will forbid downloads for remote repos that reference local resources.
 *
 * @author Volodymyr Shulga
 * @see org.artifactory.request.UrlVerifier for details.
 */
public class UrlVerifierRemoteInterceptor implements RemoteRepoInterceptor {
    private UrlVerifier urlVerifier;

    @SuppressWarnings("SpringJavaAutowiredMembersInspection") //interceptor beans defined in xml
    @Autowired
    public UrlVerifierRemoteInterceptor(UrlVerifier urlVerifier) {
        this.urlVerifier = urlVerifier;
    }

    @Override
    public boolean isRemoteDownloadAllowed(@NotNull RemoteRepoConfig repoConfig, @NotNull RepoPath remoteRepoPath) {
        return !urlVerifier
                .isRemoteRepoBlocked(repoConfig.getRepoTypeConfig().getUrl(), repoConfig.getKey());
    }
}
