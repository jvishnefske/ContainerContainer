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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides whether the host performs an effect a module asked for (W3). Pure.
 *
 * <ul>
 *     <li>{@code http_get}: the URL must parse as an absolute {@code https} URI with a host, no user info and no
 *         fragment; its host (compared in lower case) and port (443 when absent) must equal an entry of the
 *         manifest's {@code capabilities.httpGet.hosts}. At most {@value #MAX_REQUEST_HEADERS} request headers;
 *         names are HTTP tokens and not ones the HTTP client owns ({@link #RESTRICTED_REQUEST_HEADERS}), values
 *         carry no control characters. The URL is at most {@value #MAX_URL_LENGTH} characters.</li>
 *     <li>{@code mint_access_token}: only in {@link Hook#EXECUTION} calls, with a positive {@code expires_in}; the
 *         lifetime rules of {@code PluginSecurity.createAccessToken} (P4) still apply when it is performed.</li>
 * </ul>
 * Anything else is {@link Denied}, which fails the whole call (W4): a module cannot probe the policy.
 */
public final class EffectPolicy {

    /** Most request headers one {@code http_get} may set. */
    public static final int MAX_REQUEST_HEADERS = 32;
    /** Longest URL accepted. */
    public static final int MAX_URL_LENGTH = 4096;
    /** Request headers the module may not set: the HTTP client manages them. */
    public static final Set<String> RESTRICTED_REQUEST_HEADERS = Set.of("connection", "content-length", "expect",
            "host", "upgrade", "transfer-encoding", "te", "trailer", "keep-alive", "proxy-authorization",
            "proxy-connection", "http2-settings");

    private static final Pattern TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]{1,64}");
    private static final int MAX_HEADER_VALUE = 8192;

    private EffectPolicy() {
    }

    /** The decision on one effect. */
    public sealed interface Decision {
    }

    /** The effect may be performed as {@code effect}. */
    public record Allowed(Effect.Authorized effect) implements Decision {
        public Allowed {
            Objects.requireNonNull(effect, "effect");
        }
    }

    /** The effect is refused; the call fails closed. */
    public record Denied(String reason) implements Decision {
        public Denied {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * @param hook  the hook of the call that asked for the effect
     * @param hosts the manifest's allowed hosts
     */
    public static Decision authorize(Effect effect, Hook hook, Set<WasmManifest.AllowedHost> hosts) {
        return switch (effect) {
            case Effect.HttpGet get -> httpGet(get, hosts);
            case Effect.MintAccessToken mint -> mint(mint, hook);
        };
    }

    private static Decision mint(Effect.MintAccessToken mint, Hook hook) {
        if (hook != Hook.EXECUTION) {
            return new Denied("mint_access_token is available in execution calls only, not in " + hook.wireName());
        }
        if (mint.expiresIn() < 1) {
            return new Denied("mint_access_token expires_in must be at least 1");
        }
        return new Allowed(new Effect.AllowedMint(mint.expiresIn(), mint.notAfter()));
    }

    private static Decision httpGet(Effect.HttpGet get, Set<WasmManifest.AllowedHost> hosts) {
        if (get.url().length() > MAX_URL_LENGTH) {
            return new Denied("http_get url is longer than " + MAX_URL_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(get.url());
        } catch (URISyntaxException e) {
            return new Denied("http_get url is not a valid URI");
        }
        if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme())) {
            return new Denied("http_get url must be https");
        }
        if (uri.getRawUserInfo() != null || (uri.getRawAuthority() != null && uri.getRawAuthority().contains("@"))) {
            return new Denied("http_get url must not carry user info");
        }
        if (uri.getRawFragment() != null) {
            return new Denied("http_get url must not have a fragment");
        }
        if (uri.getHost() == null) {
            return new Denied("http_get url has no host");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        if (!hosts.contains(new WasmManifest.AllowedHost(host, port))) {
            return new Denied("http_get to " + host + ":" + port + " is not allowed by the manifest");
        }
        if (get.headers().size() > MAX_REQUEST_HEADERS) {
            return new Denied("http_get sets more than " + MAX_REQUEST_HEADERS + " headers");
        }
        for (Map.Entry<String, String> header : get.headers().entrySet()) {
            String name = header.getKey();
            if (!TOKEN.matcher(name).matches()) {
                return new Denied("http_get header name '" + name + "' is not valid");
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (RESTRICTED_REQUEST_HEADERS.contains(lower) || lower.startsWith("proxy-")) {
                return new Denied("http_get may not set header '" + name + "'");
            }
            if (!isFieldValue(header.getValue())) {
                return new Denied("http_get header '" + name + "' has an invalid value");
            }
        }
        return new Allowed(new Effect.AllowedGet(uri, get.headers()));
    }

    /** @return whether {@code value} is a header value without control characters, at most 8 KiB. Pure. */
    static boolean isFieldValue(String value) {
        if (value == null || value.length() > MAX_HEADER_VALUE) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7f) {
                return false;
            }
        }
        return true;
    }

    /** @return whether {@code name} is a valid HTTP header name of at most 64 characters. Pure. */
    static boolean isToken(String name) {
        return name != null && TOKEN.matcher(name).matches();
    }
}
