/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.rest.resource.pkgapi;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.artifactory.addon.plugin.pkgtype.PluginApiMount;
import org.artifactory.addon.plugin.pkgtype.PluginApiRequest;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiGate;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiService;
import org.artifactory.util.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The servlet side of the plugin HTTP API: turns a servlet request into a {@link PluginApiRequest}, asks
 * {@link PluginApiService} whether it may reach the handler, and turns the handler's {@link PluginApiResponse} into a
 * JAX-RS response. Shared by {@link DockerPluginApiResource} ({@code /api/docker/}) and
 * {@link PkgPluginApiResource} ({@code /api/pkg/}).
 *
 * <p>Order of work for a request: the gate decides from the repository and the caller's permission (no plugin code
 * runs, and the request body is not touched, before it allows the request); then the handler gets the live request
 * stream; then the response is written. Bodies are streamed in both directions: the request body is the servlet
 * input stream, never read here, and a stream response body is copied to the client in 64 KiB pieces. Every stream
 * response body is closed: after copying, or unread for HEAD and refused requests, and in any case at the end of the
 * request through the {@code closer} the resource passes in (Jersey's {@code CloseableService}).
 *
 * <p>The query string is parsed from {@link HttpServletRequest#getQueryString()} rather than through servlet
 * parameters, which would consume a form-encoded POST body.
 */
@Component
public class PluginApiEndpoint {

    static final String API_VERSION_HEADER = "Docker-Distribution-API-Version";
    static final String API_VERSION = "registry/2.0";
    static final String CONTENT_LENGTH = "Content-Length";
    static final String CONTENT_TYPE = "Content-Type";
    private static final int COPY_BUFFER = 64 * 1024;

    /** Resolves the client-visible URLs a {@link PluginApiRequest#baseUrl()} is chosen from. */
    interface UrlResolver {
        /** The platform URL, such as {@code https://reg.example.com}. */
        String platformUrl(HttpServletRequest request);

        /** The Artifactory URL, such as {@code https://reg.example.com/artifactory}. */
        String contextUrl(HttpServletRequest request);

        /**
         * Artifactory's own resolution: the {@code X-JFrog-Override-Base-Url} header, then the configured base URL,
         * then trusted {@code X-Forwarded-Host} and {@code X-Forwarded-Proto}, then the request's scheme, host and
         * port (the {@code Host} header, which the router passes on).
         */
        static UrlResolver artifactory() {
            return new UrlResolver() {
                @Override
                public String platformUrl(HttpServletRequest request) {
                    return HttpUtils.getPlatformServletContextUrl(request);
                }

                @Override
                public String contextUrl(HttpServletRequest request) {
                    return HttpUtils.getServletContextUrl(request);
                }
            };
        }
    }

    private final PluginApiService service;
    private final UrlResolver urls;

    @Autowired
    public PluginApiEndpoint(PluginApiService service) {
        this(service, UrlResolver.artifactory());
    }

    PluginApiEndpoint(PluginApiService service, UrlResolver urls) {
        this.service = service;
        this.urls = urls;
    }

    /**
     * Serves one API request.
     *
     * @param pkgEndpoint {@code true} for {@code /api/pkg/}, {@code false} for {@code /api/docker/}
     * @param closer      registers a stream to be closed when the request ends, whatever happens to the response
     */
    public Response serve(boolean pkgEndpoint, String repoKey, String path, HttpServletRequest request,
            Consumer<Closeable> closer) throws IOException {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        PluginApiGate.Decision decision = service.authorize(method, repoKey);
        if (decision instanceof PluginApiGate.Decision.Refuse refuse) {
            return refusal(refuse.status(), refuse.message(), refuse.challenge());
        }
        PluginApiGate.Decision.Dispatch dispatch = (PluginApiGate.Decision.Dispatch) decision;
        PluginApiResponse response = service.invoke(dispatch, toApiRequest(pkgEndpoint, method, repoKey, path,
                request));
        return toJaxRs(response, "HEAD".equals(method), closer);
    }

    /**
     * Answers the registry ping, {@code GET /v2/}, which {@code DockerInternalRewrite} forwards to
     * {@code /api/docker/v2/}. The rewrite forwards every {@code /v2/<one-segment>} there too ({@code /v2/_catalog},
     * {@code /v2/token}); only {@code /v2} and {@code /v2/} are the ping, the rest answer 404.
     */
    public Response ping(HttpServletRequest request) {
        Optional<String> original = originalRelativeUri(request);
        if (original.isPresent() && !original.get().equals("/v2") && !original.get().equals("/v2/")) {
            return refusal(404, "Not found", false);
        }
        return switch (service.ping()) {
            case OK -> Response.ok(bytesOut("{}".getBytes(StandardCharsets.UTF_8)))
                    .header(CONTENT_TYPE, "application/json")
                    .header(CONTENT_LENGTH, 2)
                    .header(API_VERSION_HEADER, API_VERSION)
                    .build();
            case CHALLENGE -> Response.fromResponse(refusal(401, "Authentication is required", true))
                    .header(API_VERSION_HEADER, API_VERSION)
                    .build();
            case NOT_FOUND -> refusal(404, "No plugin package type serves an HTTP API", false);
        };
    }

    PluginApiRequest toApiRequest(boolean pkgEndpoint, String method, String repoKey, String path,
            HttpServletRequest request) throws IOException {
        PluginApiService.CallerFacts caller = service.caller();
        PluginApiMount mount = PluginApiMount.detect(pkgEndpoint, originalRelativeUri(request).orElse(null), repoKey);
        InputStream body = request.getInputStream();
        return PluginApiRequest.builder(method, repoKey, path == null ? "" : path)
                .query(PluginApiRequest.parseQuery(request.getQueryString()))
                .headers(headers(request))
                .body(body == null ? InputStream.nullInputStream() : body, request.getContentLengthLong())
                .caller(caller.username(), caller.admin(), caller.anonymous())
                .baseUrl(mount.baseUrl(urls.platformUrl(request), urls.contextUrl(request)), mount)
                .build();
    }

    /**
     * Builds the JAX-RS response for a handler response, following the rules documented on
     * {@link PluginApiResponse}. Every entity is a {@link StreamingOutput}, also for an empty body, so Jersey and
     * Artifactory's response filters pass it through untouched (they replace a missing error entity).
     */
    static Response toJaxRs(PluginApiResponse response, boolean head, Consumer<Closeable> closer) {
        PluginApiResponse.Body body = response.body();
        if (body instanceof PluginApiResponse.Body.Stream stream) {
            closer.accept(stream.input());
        }
        Response.ResponseBuilder builder = Response.status(response.status());
        long knownSize = body instanceof PluginApiResponse.Body.None ? -1 : body.size();
        boolean hasContentType = false;
        boolean hasContentLength = false;
        for (Map.Entry<String, List<String>> header : response.headers().entrySet()) {
            String name = header.getKey().toLowerCase(Locale.ROOT);
            if (name.equals("transfer-encoding") || (name.equals("content-length") && knownSize >= 0)) {
                continue;
            }
            hasContentType |= name.equals("content-type");
            hasContentLength |= name.equals("content-length");
            for (String value : header.getValue()) {
                builder.header(header.getKey(), value);
            }
        }
        if (knownSize >= 0) {
            builder.header(CONTENT_LENGTH, knownSize);
        } else if (!head && !hasContentLength && body instanceof PluginApiResponse.Body.None) {
            builder.header(CONTENT_LENGTH, 0);
        }
        if (!hasContentType && !(body instanceof PluginApiResponse.Body.None)) {
            builder.header(CONTENT_TYPE, "application/octet-stream");
        }
        if (head) {
            closeQuietly(body);
            return builder.entity(bytesOut(new byte[0])).build();
        }
        StreamingOutput entity = switch (body) {
            case PluginApiResponse.Body.None none -> bytesOut(new byte[0]);
            case PluginApiResponse.Body.Bytes bytes -> bytesOut(bytes.content());
            case PluginApiResponse.Body.Stream stream -> out -> copyAndClose(stream.input(), out);
        };
        return builder.entity(entity).build();
    }

    static Response refusal(int status, String message, boolean challenge) {
        byte[] bytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
        Response.ResponseBuilder builder = Response.status(status)
                .header(CONTENT_TYPE, "text/plain; charset=utf-8")
                .header(CONTENT_LENGTH, bytes.length)
                .entity(bytesOut(bytes));
        if (challenge) {
            builder.header("WWW-Authenticate", PluginApiGate.BASIC_CHALLENGE);
        }
        return builder.build();
    }

    /**
     * The URI the client sent, relative to the context path, when this request was forwarded (by the {@code /v2/}
     * rewrite); empty for a request that was not forwarded.
     */
    static Optional<String> originalRelativeUri(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (!(uri instanceof String original)) {
            return Optional.empty();
        }
        Object context = request.getAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH);
        String contextPath = context instanceof String c ? c : "";
        return Optional.of(original.startsWith(contextPath) ? original.substring(contextPath.length()) : original);
    }

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (request.getHeaderNames() == null) {
            return headers;
        }
        for (String name : Collections.list(request.getHeaderNames())) {
            List<String> values = headers.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>());
            if (request.getHeaders(name) != null) {
                values.addAll(Collections.list(request.getHeaders(name)));
            }
        }
        return headers;
    }

    private static StreamingOutput bytesOut(byte[] bytes) {
        return out -> out.write(bytes);
    }

    private static void copyAndClose(InputStream input, OutputStream out) throws IOException {
        try (InputStream in = input) {
            byte[] buffer = new byte[COPY_BUFFER];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private static void closeQuietly(PluginApiResponse.Body body) {
        if (body instanceof PluginApiResponse.Body.Stream stream) {
            try {
                stream.input().close();
            } catch (IOException | RuntimeException ignored) {
                // HEAD sends no body; a stream that fails to close has nothing left to send
            }
        }
    }
}
