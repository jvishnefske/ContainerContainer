/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype;

import java.util.Objects;

/**
 * How a client addressed a plugin package type's HTTP API, which decides the URL the client uses for a handler path.
 *
 * <p>A {@link PluginApiRequest#path()} is always relative to the repository ({@code v2/library/alpine/manifests/3.20}
 * for {@code /api/docker/myrepo/v2/library/alpine/manifests/3.20}). The URL a client sees for that path depends on
 * the entry point:
 * <ul>
 *     <li>{@link #V2}: the client called the registry root, {@code <platform>/v2/<repoKey>/<rest>}, which the
 *         router and {@code DockerInternalRewrite} (repository-path-prefix method) forward to
 *         {@code /api/docker/<repoKey>/v2/<rest>}. The base URL is the platform URL, without the Artifactory
 *         context path.</li>
 *     <li>{@link #DOCKER}: the client called {@code <artifactory>/api/docker/<repoKey>/<path>} directly, or came
 *         through a {@code /v2/} rewrite that is not repository-path-prefix (a subdomain rewrite); the docker API
 *         URL under the Artifactory context URL is always valid.</li>
 *     <li>{@link #PKG}: the client called {@code <artifactory>/api/pkg/<repoKey>/<path>}.</li>
 * </ul>
 */
public enum PluginApiMount {
    V2,
    DOCKER,
    PKG;

    private static final String V2_SEGMENT = "v2";

    /**
     * Decides the mount of a request. Pure.
     *
     * @param pkgEndpoint          {@code true} for {@code /api/pkg/...}, {@code false} for {@code /api/docker/...}
     * @param originalRelativeUri  the URI the client sent, relative to the Artifactory context path (for a request
     *                             forwarded by the {@code /v2/} rewrite, the URI before forwarding, such as
     *                             {@code /v2/myrepo/library/alpine/manifests/3.20}); {@code null} when the request
     *                             was not forwarded
     * @param repoKey              repository key of the request
     */
    public static PluginApiMount detect(boolean pkgEndpoint, String originalRelativeUri, String repoKey) {
        Objects.requireNonNull(repoKey, "repoKey");
        if (pkgEndpoint) {
            return PKG;
        }
        if (originalRelativeUri != null && originalRelativeUri.startsWith("/v2/" + repoKey + "/")) {
            return V2;
        }
        return DOCKER;
    }

    /**
     * The base URL for this mount: {@code platformUrl} for {@link #V2}, {@code contextUrl} otherwise, without a
     * trailing slash. Pure.
     *
     * @param platformUrl the client-visible platform URL, for example {@code https://reg.example.com}
     * @param contextUrl  the client-visible Artifactory URL, for example {@code https://reg.example.com/artifactory}
     */
    public String baseUrl(String platformUrl, String contextUrl) {
        return trimTrailingSlashes(this == V2 ? platformUrl : contextUrl);
    }

    /**
     * The path, relative to {@link #baseUrl}, a client uses for handler path {@code path} of repository
     * {@code repoKey}; always starts with {@code /}. Pure.
     *
     * <p>For {@link #V2}, {@code v2/<rest>} maps to {@code /v2/<repoKey>/<rest>}. A path outside {@code v2/} cannot
     * be reached through the registry root, so it maps to the same prefix ({@code /v2/<repoKey>/<path>}); handlers
     * served through {@code /v2/} only see {@code v2/...} paths.
     */
    public String clientPath(String repoKey, String path) {
        String relative = trimLeadingSlashes(Objects.requireNonNull(path, "path"));
        return switch (this) {
            case V2 -> "/v2/" + repoKey + "/" + stripV2(relative);
            case DOCKER -> "/api/docker/" + repoKey + "/" + relative;
            case PKG -> "/api/pkg/" + repoKey + "/" + relative;
        };
    }

    private static String stripV2(String path) {
        if (path.equals(V2_SEGMENT)) {
            return "";
        }
        if (path.startsWith(V2_SEGMENT + "/")) {
            return path.substring(V2_SEGMENT.length() + 1);
        }
        return path;
    }

    static String trimLeadingSlashes(String value) {
        int start = 0;
        while (start < value.length() && value.charAt(start) == '/') {
            start++;
        }
        return value.substring(start);
    }

    static String trimTrailingSlashes(String value) {
        int end = Objects.requireNonNull(value, "url").length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
