/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2019 JFrog Ltd.
 * Modifications copyright (C) 2026 the fork authors.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.wasm;

import org.artifactory.addon.plugin.groovy.PluginSecurity;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Performs allowed effects for one plugin (W3); the imperative shell of effect handling.
 *
 * <ul>
 *     <li>{@code http_get} uses the JDK {@link HttpClient} with redirects off, no proxy and the JVM's default trust
 *         store. One deadline ({@code limits.httpTimeoutMs}) covers connecting, the response headers and the whole
 *         body; the body is read up to {@code limits.maxHttpBodyBytes} and the exchange is cancelled as soon as it
 *         exceeds that. Any HTTP status is a result ({@code ok: true}); no response in time, a connection or TLS
 *         failure or an oversized body is {@code ok: false} with a reason that contains no response data.
 *         At most {@value #MAX_RESPONSE_HEADERS} response headers are passed on, names in lower case.</li>
 *     <li>{@code mint_access_token} goes through {@link PluginSecurity#createAccessToken(long, long)}, the same
 *         path and rules as Groovy executions (P4): only in execution scope, for the current non-anonymous caller,
 *         scope {@code applied-permissions/user}, at most one hour, never past {@code not_after} or the expiry of
 *         the realm credential that authenticated the caller.</li>
 * </ul>
 */
public final class HostEffects implements StepLoop.Performer {

    /** Most response headers passed to the module. */
    public static final int MAX_RESPONSE_HEADERS = 64;

    private final HttpClient http;
    private final int timeoutMs;
    private final int maxBodyBytes;
    private final PluginSecurity security;

    /**
     * @param http         the client; build it with {@link #client(int)}
     * @param timeoutMs    the deadline of one {@code http_get}
     * @param maxBodyBytes the largest response body
     * @param security     mints tokens for the caller
     */
    public HostEffects(HttpClient http, int timeoutMs, int maxBodyBytes, PluginSecurity security) {
        this.http = Objects.requireNonNull(http, "http");
        this.timeoutMs = timeoutMs;
        this.maxBodyBytes = maxBodyBytes;
        this.security = Objects.requireNonNull(security, "security");
    }

    /** @return an HTTP client that never follows redirects and uses no proxy */
    public static HttpClient client(int connectTimeoutMs) {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public EffectResult perform(Effect.Authorized effect) {
        return switch (effect) {
            case Effect.AllowedGet get -> get(get);
            case Effect.AllowedMint mint -> mint(mint);
        };
    }

    private EffectResult mint(Effect.AllowedMint mint) {
        PluginSecurity.PluginAccessToken token = security.createAccessToken(mint.expiresIn(),
                mint.notAfter().orElse(Long.MAX_VALUE));
        return token.issued()
                ? new EffectResult.Mint(true, token.accessToken(), token.expiresIn(), "")
                : new EffectResult.Mint(false, "", 0, token.reason());
    }

    private EffectResult get(Effect.AllowedGet get) {
        HttpRequest.Builder request = HttpRequest.newBuilder(get.uri()).GET().timeout(Duration.ofMillis(timeoutMs));
        get.headers().forEach(request::header);
        CompletableFuture<HttpResponse<Capped>> future;
        try {
            future = http.sendAsync(request.build(), info -> new CappedSubscriber(maxBodyBytes));
        } catch (IllegalArgumentException e) {
            return EffectResult.HttpGet.failed("the request was refused by the HTTP client");
        }
        HttpResponse<Capped> response;
        try {
            response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return EffectResult.HttpGet.failed("no complete response within " + timeoutMs + " ms");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return EffectResult.HttpGet.failed("interrupted");
        } catch (ExecutionException e) {
            return EffectResult.HttpGet.failed("request failed: " + e.getCause().getClass().getSimpleName());
        }
        Capped body = response.body();
        if (body.overflowed()) {
            return EffectResult.HttpGet.failed("response body is larger than " + maxBodyBytes + " bytes");
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> header : response.headers().map().entrySet()) {
            if (headers.size() >= MAX_RESPONSE_HEADERS) {
                break;
            }
            headers.put(header.getKey().toLowerCase(Locale.ROOT), List.copyOf(header.getValue()));
        }
        return new EffectResult.HttpGet(true, response.statusCode(), headers, body.bytes(), "");
    }

    /** A response body read up to the cap. */
    record Capped(byte[] bytes, boolean overflowed) {
    }

    /** Collects at most {@code cap} body bytes and cancels the exchange once more arrive. */
    static final class CappedSubscriber implements HttpResponse.BodySubscriber<Capped> {
        private final int cap;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<Capped> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        CappedSubscriber(int cap) {
            this.cap = cap;
        }

        @Override
        public CompletionStage<Capped> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (bytes.size() + buffer.remaining() > cap) {
                    subscription.cancel();
                    result.complete(new Capped(new byte[0], true));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            result.complete(new Capped(bytes.toByteArray(), false));
        }
    }
}
