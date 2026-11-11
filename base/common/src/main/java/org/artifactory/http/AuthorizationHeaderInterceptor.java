package org.artifactory.http;

import org.jfrog.access.http.auth.client5.PreemptiveAccessAuthInterceptor5;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Generic Authorization header interceptor that adds a Bearer token
 * using a provided token supplier.
 */
public class AuthorizationHeaderInterceptor extends PreemptiveAccessAuthInterceptor5 {
    private final Supplier<String> tokenSupplier;

    public AuthorizationHeaderInterceptor(Supplier<String> tokenSupplier) {
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier must not be null");
    }

    @Override
    protected String getOrCreateToken() {
        return tokenSupplier.get();
    }
}


