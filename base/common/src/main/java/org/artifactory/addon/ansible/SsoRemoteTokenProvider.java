package org.artifactory.addon.ansible;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.crypto.CryptoHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.security.props.auth.model.OAuth2TokenModel;
import org.jfrog.client.http.auth.TokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.artifactory.util.PackageTypeConfigFields.AUTH_URL;
import static org.jfrog.common.ExceptionLogUtils.debugOrWarn;

/**
 * Implementation of a {@link TokenProvider} for SSO authentication using OAuth2 refresh token flow.
 * Handles configuration extraction and token caching; delegates the actual token fetch to
 * {@link AnsibleAddon#fetchSsoToken}.
 */
@Slf4j
@Service
public class SsoRemoteTokenProvider implements TokenProvider {

    private static final int CACHE_INITIAL_CAPACITY = 100;
    private static final int CACHE_MAX_SIZE = 1000;
    private static final int MAX_CACHE_DURATION_HOURS = 24;

    private final Cache<SsoTokenCacheKey, CachedOAuth2Token> tokenCache = CacheBuilder.newBuilder()
            .initialCapacity(CACHE_INITIAL_CAPACITY)
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(MAX_CACHE_DURATION_HOURS, TimeUnit.HOURS)
            .build();

    private final RepositoryService repositoryService;

    @Autowired
    public SsoRemoteTokenProvider(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    @Nullable
    public String getToken(Map<String, String> challengeParams, String method, String uri, String repoKey) {
        if (repoKey == null) {
            log.debug("Repo key is null, cannot fetch SSO token");
            return null;
        }

        RemoteRepoConfig repoConfig = repositoryService.getRemoteRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            log.warn("No remote repo config found for key: {}", repoKey);
            return null;
        }

        String authUrl = getAuthUrl(repoConfig);
        String refreshToken = getRefreshToken(repoConfig);
        if (StringUtils.isBlank(authUrl) || StringUtils.isBlank(refreshToken)) {
            log.warn("Auth URL or refresh token not configured for repo: {}", repoKey);
            return null;
        }

        CachedOAuth2Token cached = getOrFetchToken(repoKey, authUrl, refreshToken);
        return cached != null ? cached.getAccessToken() : null;
    }

    @Nullable
    private CachedOAuth2Token getOrFetchToken(String repoKey, String authUrl, String refreshToken) {
        SsoTokenCacheKey cacheKey = SsoTokenCacheKey.of(repoKey, authUrl, refreshToken);

        CachedOAuth2Token cached = tokenCache.getIfPresent(cacheKey);
        if (cached != null && !cached.isExpiredOrExpiringSoon()) {
            log.debug("Using cached token for repo: {}", repoKey);
            return cached;
        }

        try {
            if (cached != null) {
                log.debug("Cached token for repo: {} is expired or expiring soon, fetching new token", repoKey);
                tokenCache.invalidate(cacheKey);
            }
            return tokenCache.get(cacheKey, () -> loadToken(authUrl, refreshToken, repoKey, cached));
        } catch (Exception e) {
            String errorMsg = "Failed to get or fetch token from '" + authUrl + "' for repo: " + repoKey;
            debugOrWarn(log, errorMsg, e);
            return null;
        }
    }

    /**
     * Callable for {@link Cache#get}. Guava guarantees only one thread executes this per cache key; others block and
     * reuse the result. If the fetch fails and the previous token has not definitively expired, it is returned as a
     * fallback so that all waiting threads receive a usable token.
     */
    private CachedOAuth2Token loadToken(String authUrl, String refreshToken, String repoKey,
            @Nullable CachedOAuth2Token previousToken) {
        try {
            return fetchNewToken(authUrl, refreshToken, repoKey);
        } catch (Exception e) {
            if (previousToken != null && !previousToken.isDefinitivelyExpired()) {
                log.debug("Failed to refresh token for repo: {}, using existing token", repoKey);
                return previousToken;
            }
            throw e;
        }
    }

    private CachedOAuth2Token fetchNewToken(String authUrl, String refreshToken, String repoKey) {
        OAuth2TokenModel tokenModel = ContextHelper.get()
                .beanForType(AddonsManager.class)
                .addonByType(AnsibleAddon.class)
                .fetchSsoToken(authUrl, refreshToken, repoKey);
        if (tokenModel == null || StringUtils.isBlank(tokenModel.getAccessToken())) {
            throw new TokenFetchException("Failed to fetch token for repo: " + repoKey);
        }
        log.debug("Fetched new token for repo: {}, expires in: {}s", repoKey, tokenModel.getExpiresIn());
        return new CachedOAuth2Token(tokenModel);
    }

    @Nullable
    private String getAuthUrl(RemoteRepoConfig repoConfig) {
        Map<String, String> packageTypeConfig = repoConfig.getPackageTypeConfig();
        return packageTypeConfig != null ? packageTypeConfig.get(AUTH_URL.getKey()) : null;
    }

    @Nullable
    private String getRefreshToken(RemoteRepoConfig repoConfig) {
        String password = repoConfig.getRepoTypeConfig().getPassword();
        return StringUtils.isNotBlank(password)
                ? CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(), password)
                : null;
    }

    private static class TokenFetchException extends RuntimeException {
        TokenFetchException(String message) {
            super(message);
        }
    }
}
