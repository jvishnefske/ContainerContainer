/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One request to a plugin package type's HTTP API, as handed to a {@link PluginApiHandler}.
 *
 * <p>Immutable except for {@link #body()}, the live request stream, which the handler may read once. The maps are
 * unmodifiable deep copies; header names are lower case. Construct it with {@link #builder(String, String, String)}.
 *
 * @param method        HTTP method, upper case ({@code GET}, {@code HEAD}, {@code PUT}, {@code POST},
 *                      {@code PATCH}, {@code DELETE})
 * @param repoKey       key of the repository the request addresses
 * @param path          the request path after the repository key, without a leading slash, for example
 *                      {@code v2/library/alpine/manifests/3.20}; empty for the repository root
 * @param query         decoded query parameters in request order, for example {@code n -> [10]}; parameters
 *                      without a value map to an empty string
 * @param headers       request headers by lower-case name, values in request order
 * @param body          the request body as sent, streamed from the client and never buffered by the shell; an empty
 *                      stream when the request has none. The shell closes it after the handler returns.
 * @param contentLength the declared body length, {@code -1} when unknown (for example a chunked body)
 * @param username      the authenticated caller, {@code anonymous} for an anonymous caller
 * @param admin         whether the caller is an administrator
 * @param anonymous     whether the caller is the anonymous user
 * @param baseUrl       the client-visible base URL of the request's {@link #mount()}, without a trailing slash; see
 *                      {@link #urlFor(String)}
 * @param mount         how the client addressed the API
 */
public record PluginApiRequest(
        String method,
        String repoKey,
        String path,
        Map<String, List<String>> query,
        Map<String, List<String>> headers,
        InputStream body,
        long contentLength,
        String username,
        boolean admin,
        boolean anonymous,
        String baseUrl,
        PluginApiMount mount) {

    public PluginApiRequest {
        method = requireNonBlank(method, "method").toUpperCase(Locale.ROOT);
        repoKey = requireNonBlank(repoKey, "repoKey");
        path = PluginApiMount.trimLeadingSlashes(Objects.requireNonNull(path, "path"));
        query = copy(Objects.requireNonNull(query, "query"), false);
        headers = copy(Objects.requireNonNull(headers, "headers"), true);
        Objects.requireNonNull(body, "body");
        if (contentLength < -1) {
            throw new IllegalArgumentException("contentLength must be -1 (unknown) or more, got " + contentLength);
        }
        username = requireNonBlank(username, "username");
        baseUrl = PluginApiMount.trimTrailingSlashes(Objects.requireNonNull(baseUrl, "baseUrl"));
        Objects.requireNonNull(mount, "mount");
    }

    /** @return the first value of header {@code name} (any case) */
    public Optional<String> header(String name) {
        return first(headers.get(name.toLowerCase(Locale.ROOT)));
    }

    /** @return every value of header {@code name} (any case), empty when absent */
    public List<String> headerValues(String name) {
        return headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
    }

    /** @return the first value of query parameter {@code name} */
    public Optional<String> queryParam(String name) {
        return first(query.get(name));
    }

    /** @return every value of query parameter {@code name}, empty when absent */
    public List<String> queryParams(String name) {
        return query.getOrDefault(name, List.of());
    }

    /**
     * The absolute URL a client uses for handler path {@code path} of this repository, for example to build a
     * {@code Location} header: {@code urlFor("v2/library/alpine/blobs/uploads/42")} is
     * {@code https://reg.example.com/v2/myrepo/library/alpine/blobs/uploads/42} for a request that came through the
     * registry root, and {@code https://host/artifactory/api/docker/myrepo/v2/library/alpine/blobs/uploads/42} for
     * one that called {@code /api/docker/} directly. See {@link PluginApiMount#clientPath}.
     */
    public String urlFor(String path) {
        return baseUrl + mount.clientPath(repoKey, path);
    }

    /**
     * Reads the whole body if it is at most {@code maxBytes} long, for small documents such as a manifest.
     * Reads at most {@code maxBytes + 1} bytes, so memory stays bounded whatever the client sends.
     *
     * @return the body, or empty when it is longer than {@code maxBytes} (the stream is then partly consumed)
     */
    public Optional<byte[]> readBody(int maxBytes) throws IOException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative: " + maxBytes);
        }
        if (contentLength > maxBytes) {
            return Optional.empty();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 8192) + 1);
        byte[] buffer = new byte[8192];
        long remaining = (long) maxBytes + 1;
        while (remaining > 0) {
            int read = body.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                break;
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
        return out.size() > maxBytes ? Optional.empty() : Optional.of(out.toByteArray());
    }

    /**
     * Parses a raw query string ({@code a=1&b=&c&a=2}) into decoded parameters, in order. Pure; never touches a
     * request body, unlike servlet parameter parsing of form posts.
     *
     * @param rawQuery the query string without {@code ?}, {@code null} or empty for none
     */
    public static Map<String, List<String>> parseQuery(String rawQuery) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Collections.unmodifiableMap(result);
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            result.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return copy(result, false);
    }

    /** Starts a request; every other field has a neutral default (no query, headers or body, anonymous caller). */
    public static Builder builder(String method, String repoKey, String path) {
        return new Builder(method, repoKey, path);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static Optional<String> first(List<String> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
    }

    private static String requireNonBlank(String value, String name) {
        if (Objects.requireNonNull(value, name).isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> source, boolean lowerCaseKeys) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        source.forEach((key, values) -> {
            String name = lowerCaseKeys ? Objects.requireNonNull(key, "name").toLowerCase(Locale.ROOT)
                    : Objects.requireNonNull(key, "name");
            List<String> merged = result.computeIfAbsent(name, k -> new ArrayList<>());
            for (String value : Objects.requireNonNull(values, "values of " + key)) {
                merged.add(Objects.requireNonNull(value, "value of " + key));
            }
        });
        result.replaceAll((key, values) -> List.copyOf(values));
        return Collections.unmodifiableMap(result);
    }

    /** Builder for {@link PluginApiRequest}. */
    public static final class Builder {
        private final String method;
        private final String repoKey;
        private final String path;
        private Map<String, List<String>> query = Map.of();
        private Map<String, List<String>> headers = Map.of();
        private InputStream body = InputStream.nullInputStream();
        private long contentLength = -1;
        private String username = "anonymous";
        private boolean admin;
        private boolean anonymous = true;
        private String baseUrl = "";
        private PluginApiMount mount = PluginApiMount.PKG;

        private Builder(String method, String repoKey, String path) {
            this.method = method;
            this.repoKey = repoKey;
            this.path = path;
        }

        public Builder query(Map<String, List<String>> value) {
            this.query = value;
            return this;
        }

        public Builder headers(Map<String, List<String>> value) {
            this.headers = value;
            return this;
        }

        public Builder body(InputStream value, long length) {
            this.body = value;
            this.contentLength = length;
            return this;
        }

        public Builder caller(String name, boolean isAdmin, boolean isAnonymous) {
            this.username = name;
            this.admin = isAdmin;
            this.anonymous = isAnonymous;
            return this;
        }

        public Builder baseUrl(String value, PluginApiMount how) {
            this.baseUrl = value;
            this.mount = how;
            return this;
        }

        public PluginApiRequest build() {
            return new PluginApiRequest(method, repoKey, path, query, headers, body, contentLength, username, admin,
                    anonymous, baseUrl, mount);
        }
    }
}
