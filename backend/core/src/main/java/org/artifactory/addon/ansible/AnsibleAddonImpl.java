package org.artifactory.addon.ansible;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpPost;
import org.artifactory.common.addon.Addon;
import org.artifactory.repo.AnsibleHttpRepo;
import org.artifactory.repo.RemoteRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Ansible addon implementation. Resolves the AnsibleHttpRepo and delegates
 * OAuth2 request construction to {@link OAuth2TokenRequestUtils} and
 * HTTP execution to AnsibleHttpRepo's dedicated token client.
 */
@Slf4j
@Component
public class AnsibleAddonImpl implements AnsibleAddon, Addon {

    private final InternalRepositoryService repositoryService;

    @Autowired
    public AnsibleAddonImpl(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    @Nullable
    public OAuth2TokenModel fetchSsoToken(String authUrl, String refreshToken, String repoKey) {
        RemoteRepo remoteRepo = repositoryService.remoteRepositoryByKey(repoKey);
        if (!(remoteRepo instanceof AnsibleHttpRepo ansibleHttpRepo)) {
            log.debug("Repository '{}' is not an AnsibleHttpRepo", repoKey);
            return null;
        }
        HttpPost request = OAuth2TokenRequestUtils.createRefreshTokenRequest(authUrl, refreshToken);
        if (request == null) {
            log.debug("Failed to create token request for repo '{}'", repoKey);
            return null;
        }
        return ansibleHttpRepo.fetchAnsibleAuthToken(request);
    }

    @Override
    public boolean isDefault() {
        return false;
    }
}
