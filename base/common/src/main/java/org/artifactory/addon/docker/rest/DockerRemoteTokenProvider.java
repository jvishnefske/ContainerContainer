/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.addon.docker.rest;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.docker.DockerAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.download.RateLimiterException;
import org.jfrog.client.http.auth.TokenProvider;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Docker specific implementation of a {@link TokenProvider}.
 * The tokens are cached in-memory and auto expire according to the
 * {@code artifactory.docker.tokens.cache.idleTimeSecs} system property
 *
 * @author Shay Yaakov
 */
@Service
public class DockerRemoteTokenProvider implements TokenProvider {
    public static final String TOKEN_ERROR = "Can't fetch token for repo: ";
    private static final Logger log = LoggerFactory.getLogger(DockerRemoteTokenProvider.class);
    /**
     * Matches /v2/&lt;repo-name&gt;/manifests|blobs|tags (Docker Distribution Spec). Group 1 is the repository name.
     */
    private static final Pattern V2_REPO_NAME_PATTERN =
            Pattern.compile("^/v2/(.+)/(?:manifests|blobs|tags|referrers)(?:/|$)");
    private static final String SCOPE = "scope";
    private static final String REPOSITORY_SCOPE_PREFIX = "repository:";
    private static final String CATALOG_PATH = "/v2/_catalog";
    private LoadingCache<DockerTokenCacheKey, String> tokens;

    private @NonNull String getPath(String uri) {
        int q = uri.indexOf('?');
        return q >= 0 ? uri.substring(0, q) : uri;
    }

    /**
     * Extracts the Docker repository name from a pre-computed, query-string-free v2 API path.
     * Stops at "manifests", "blobs", "tags", or "referrers" (Docker spec).
     * E.g. /v2/nats/manifests/2025.2.5 -> "nats", /v2/myimage/tags/list -> "myimage".
     */
    private String repositoryNameFromPath(String path) {
        Matcher matcher = V2_REPO_NAME_PATTERN.matcher(path);
        if (!matcher.find()) {
            return StringUtils.EMPTY;
        }
        return StringUtils.stripEnd(matcher.group(1).trim(), "/");
    }

    @PostConstruct
    public void initTokensCache() {
        tokens = CacheBuilder.newBuilder()
                .initialCapacity(1000)
                .expireAfterWrite(ConstantValues.dockerTokensCacheIdleTimeSecs.getLong(), TimeUnit.SECONDS)
                .build(new CacheLoader<DockerTokenCacheKey, String>() {
                    @Override
                    public String load(@Nonnull DockerTokenCacheKey key) {
                        String token = fetchNewToken(key);
                        if (StringUtils.isBlank(token)) {
                            throw new IllegalStateException(TOKEN_ERROR + key.getRepoKey()
                                    + " realm: " + key.getRealm() + " scope:" + key.getScope());
                        }
                        return token;
                    }
                });
    }

    @Override
    public String getToken(Map<String, String> challengeParams, String method, String uri, String repoKey) {
        try {
            populateParamsIfNeeded(challengeParams, method, uri);
            if (log.isDebugEnabled()) {
                log.trace("Getting token for {}", challengeParams);
            }
            return tokens.get(new DockerTokenCacheKey(challengeParams, repoKey));
        } catch (RateLimiterException e) {
            throw e;
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not get token from cache for " + challengeParams, e);
        }
    }

    private void populateParamsIfNeeded(Map<String, String> challengeParams, String method, String uri) {
        handleNoServiceParamInChallenge(challengeParams);
        handleNoScopeParamInChallenge(challengeParams, method, uri);
    }

    private void handleNoServiceParamInChallenge(Map<String, String> challengeParams) {
        if (!challengeParams.containsKey("service")) {
            String realm = challengeParams.get("realm");
            if (StringUtils.isNotBlank(realm) && realm.startsWith("http")) {
                try {
                    challengeParams.put("service", new URL(realm).getHost());
                } catch (MalformedURLException e) {
                    log.error("Realm of token cannot be parsed due to: {}", e.getMessage(), e);
                }
            }
        }
    }

    private void handleNoScopeParamInChallenge(Map<String, String> challengeParams, String method, String uri) {
        if (!challengeParams.containsKey(SCOPE)) {
            if (uri.startsWith("/v2/")) {
                handleV2(challengeParams, method, uri);
            } else {
                if (log.isWarnEnabled()) {
                    log.warn("Bearer challenge has no scope and URI does not starts with /v2 but '{}'", uri);
                }
            }
        }
    }

    private void handleV2(Map<String, String> challengeParams, String method, String uri) {
        String path = StringUtils.stripEnd(getPath(uri), "/");
        if (CATALOG_PATH.equals(path)) {
            handleCatalog(challengeParams, method);
            return;
        }

        String repoName = repositoryNameFromPath(path);
        StringBuilder scopeBuilder = new StringBuilder(REPOSITORY_SCOPE_PREFIX).append(repoName).append(":");
        String newScope = appendActionToScope(method, scopeBuilder);
        if (newScope != null) {
            log.debug("Bearer challenge new scope set to '{}'", newScope);
            challengeParams.put(SCOPE, newScope);
        }
    }

    private void handleCatalog(Map<String, String> challengeParams, String method) {
        switch (StringUtils.upperCase(method)) {
            case HttpMethod.GET, HttpMethod.HEAD -> {
                String catalogScope = "registry:catalog:*";
                log.debug("Catalog Bearer challenge new scope set to '{}'", catalogScope);
                challengeParams.put(SCOPE, catalogScope);
            }
            case null, default -> {
                if (log.isWarnEnabled()) {
                    log.warn("Bearer challenge has no scope and method is not GET or HEAD for _catalog '{}'", method);
                }
            }
        }
    }

    private String appendActionToScope(String method, StringBuilder scopeBuilder) {
        return switch (StringUtils.upperCase(method)) {
            case HttpMethod.GET, HttpMethod.HEAD -> {
                scopeBuilder.append("pull");
                yield scopeBuilder.toString();
            }
            case HttpMethod.PUT -> {
                scopeBuilder.append("push");
                yield scopeBuilder.toString();
            }
            case null, default -> {
                if (log.isWarnEnabled()) {
                    log.warn("Bearer challenge has no scope and method is not GET, HEAD or PUT '{}'", method);
                }
                yield null;
            }
        };
    }

    private String fetchNewToken(DockerTokenCacheKey tokenCacheKey) {
        log.trace("Fetching new token for '{}'", tokenCacheKey);
        return ContextHelper.get().beanForType(AddonsManager.class).addonByType(DockerAddon.class)
                .fetchDockerAuthToken(tokenCacheKey);
    }
}
