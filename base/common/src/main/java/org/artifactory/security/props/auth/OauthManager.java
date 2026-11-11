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

package org.artifactory.security.props.auth;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.ConstantValues;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.model.TokenKeyValue;
import org.artifactory.security.props.auth.model.TokenManagerKey;
import org.artifactory.security.props.auth.model.TokenType;
import org.artifactory.util.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Chen Keinan
 */
@Component
@Slf4j
public class OauthManager implements TokenManager, EncryptedTokenManager {

    public static final String OAUTH_KEY = "basictoken";

    public static final String OAUTH_TOKEN_PREFIX = "Bearer ";

    public static final String OAUTH_INFO_PROPERTY = "authinfo.";

    public static final String OAUTH_IDN_TOKEN_NAME = "RT_IDN_TOKEN";

    public static final List<String> TOKEN_SCOPES = List.of("applied-permissions/user");
    private static final Set<String> PROP_KEYS = Set.of(OAUTH_KEY);

    private AccessService accessService;
    private UserGroupService userGroupService;

    private LoadingCache<TokenManagerKey, TokenKeyValue> tokensCache;

    @Autowired
    public void setUserGroupService(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @Autowired
    public void setAccessService(AccessService accessService) {
        this.accessService = accessService;
    }

    @PostConstruct
    @VisibleForTesting
    void init() {
        tokensCache = CacheBuilder.newBuilder()
                .expireAfterWrite(ConstantValues.propsTokenCacheExpirySeconds.getInt(), TimeUnit.SECONDS)
                .maximumSize(ConstantValues.propsTokenCacheSize.getInt())
                .build(new CacheLoader<>() {
                    @Override
                    public TokenKeyValue load(@Nonnull TokenManagerKey tokenManagerKey) {
                        return createToken(tokenManagerKey);
                    }
                });
    }

    @Override
    public TokenKeyValue createToken(TokenManagerKey tokenManagerKey) {
        String userName = tokenManagerKey.username();
        TokenKeyValue token = null;
        if (!userGroupService.isUserExists(userName)) {
            token = generateTokenForExternalUser(tokenManagerKey);
        } else {
            try {
                UserTokenSpec tokenSpec = UserTokenSpec.create(userName)
                        .expiresIn(getExpirationTimeForNewToken(tokenManagerKey.tokenType()))
                        .refreshable(false)
                        .scope(TOKEN_SCOPES)
                        .description(tokenManagerKey.tokenType().toString());
                CreatedTokenInfo createdTokenInfo = accessService.createToken(tokenSpec, true, false);
                token = new TokenKeyValue(OAUTH_KEY, createdTokenInfo.getTokenValue(),
                        Objects.requireNonNull(createdTokenInfo.getExpiresIn()).intValue());
            } catch (Exception e) {
                log.debug("Failed generating identity token for user: '{}'. {}", userName, e.getMessage());
                log.trace("Failed generating token.", e);
            }
        }
        return token;
    }

    @Override
    public TokenKeyValue getToken(TokenManagerKey tokenManagerKey) {
        TokenKeyValue token = null;
        try {
            token = tokensCache.get(tokenManagerKey);
            if (System.currentTimeMillis() >= token.getExpiresTimeStamp()) {
                // we got an expired token from the cache.
                token = createToken(tokenManagerKey);
                tokensCache.put(tokenManagerKey, token);
            }
        } catch (CacheLoader.InvalidCacheLoadException | ExecutionException e) {
            // Can happen if using cache and value == null or if an error has occurred
            log.debug("Could not get identity token for user '{}', key {}", tokenManagerKey.username(), OAUTH_KEY,
                    e);
        }
        return token;
    }

    @Override
    public Set<String> getPropKeys() {
        return PROP_KEYS;
    }

    @VisibleForTesting
    String getScope(UserInfo userInfo) {
        StringBuilder builder = new StringBuilder("applied-permissions/groups:");
        Set<UserGroupInfo> groups = userInfo.getGroups();
        if (CollectionUtils.isNullOrEmpty(groups)) {
            builder.append("*");
        } else {
            Iterator<UserGroupInfo> it = groups.iterator();
            boolean hasNext = it.hasNext();
            while (hasNext) {
                builder.append(it.next());
                if (hasNext = it.hasNext()) {
                    builder.append(",");
                }
            }
        }
        return builder.toString();
    }

    private TokenKeyValue generateTokenForExternalUser(TokenManagerKey tokenManagerKey) {
        String userName = tokenManagerKey.username();
        TokenKeyValue token = null;
        CreatedTokenInfo createdTokenInfo;
        String key = "accesstoken";
        try {
            UserInfo userInfo = userGroupService.currentUser();
            String scope = getScope(userInfo);
            UserTokenSpec tokenSpec = UserTokenSpec.create(userName)
                    .expiresIn(ConstantValues.genericTokensCacheIdleTimeSecs.getLong())
                    .refreshable(false)
                    .description(tokenManagerKey.tokenType().toString())
                    .scope(Lists.newArrayList(scope));
            createdTokenInfo = accessService.createToken(tokenSpec, true, false);
            token = new TokenKeyValue(key, createdTokenInfo.getTokenValue(),
                    Objects.requireNonNull(createdTokenInfo.getExpiresIn()).intValue());
        } catch (Exception e) {
            log.debug("Failed generating token for external user: '{}' with key '{}'. {}", userName, key,
                    e.getMessage());
            log.trace("Failed generating token for external user: '{}'.", userName, e);
        }
        return token;
    }

    /**
     * Get the expiration time before creating a new token, according to the given type
     */
    private Long getExpirationTimeForNewToken(TokenType tokenType) {
        if (TokenType.DOCKER.equals(tokenType)) {
            return ConstantValues.dockerTokensExpirationTimeSecs.getLong();
        }
        return ConstantValues.genericTokensExpirationTimeSecs.getLong();
    }
}