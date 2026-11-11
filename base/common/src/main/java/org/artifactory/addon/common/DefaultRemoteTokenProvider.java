package org.artifactory.addon.common;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.RemoteRepoConfig;
import org.jfrog.client.http.auth.TokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import java.util.Map;

/**
 * @author Arkady Mishiev
 * This class provides a basic way to support a Bearer token as an authorization header,
 * when its value is based on existing repo config field - 'password/token'.
 * It will be called in case of meeting following 2 conditions:
 * 1. enableTokenAuthentication flag is turned on for the remote repo in package
 * 2. the remote repository responds with relevant Realm challenge in the header
 */
@Service
public class DefaultRemoteTokenProvider implements TokenProvider {

    private final RepositoryService repositoryService;

    @Autowired
    public DefaultRemoteTokenProvider(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Nullable
    @Override
    public String getToken(Map<String, String> challengeParams, String method, String uri, @Nullable String repoKey) {
        if (repoKey != null) {
            RemoteRepoConfig repoConfig = repositoryService.getRemoteRepoConfigByKey(repoKey);
            if (repoConfig != null && StringUtils.isNotEmpty(repoConfig.getRepoTypeConfig().getPassword())) {
                return CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(),
                        repoConfig.getRepoTypeConfig().getPassword());
            }
        }
        return null;
    }
}