package org.artifactory.http;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import org.jfrog.access.client.AccessClientProvider;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Token supplier that retrieves Access tokens and caches them until close to expiry.
 * This keeps Access-specific code out of the interceptor.
 */
public class AccessTokenSupplier implements Supplier<String> {
    private static final long TOKEN_EXPIRATION_BUFFER_MILLIS = TimeUnit.HOURS.toMillis(1);

    private final AccessClientProvider accessClientProvider;
    private final TokenRequest tokenSpec;
    private final Cache<String, String> tokenCache;

    public AccessTokenSupplier(AccessClientProvider accessClientProvider,
                               long tokenTTLMillis,
                               String audience,
                               String scope) {
        Objects.requireNonNull(accessClientProvider, "accessClientProvider must not be null");
        if (tokenTTLMillis <= TOKEN_EXPIRATION_BUFFER_MILLIS) {
            tokenTTLMillis = TOKEN_EXPIRATION_BUFFER_MILLIS * 2;
        }

        long tokenExpiryMillis = tokenTTLMillis;
        this.accessClientProvider = accessClientProvider;
        this.tokenSpec = TokenRequest
                .scopes(Lists.newArrayList(scope))
                .audience(audience)
                .nonRefreshable()
                .expiresIn(tokenExpiryMillis)
                .build();

        long tokenDurationMillis = tokenExpiryMillis - TOKEN_EXPIRATION_BUFFER_MILLIS;
        this.tokenCache = CacheBuilder.newBuilder()
                .expireAfterWrite(tokenDurationMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    @Override
    public String get() {
        try {
            return tokenCache.get("token", this::generateToken);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to generate Access token", e.getCause());
        }
    }

    private String generateToken() {
        TokenResponse res = this.accessClientProvider.getAccessClient().token().create(this.tokenSpec);
        return res.getTokenValue();
    }
}


