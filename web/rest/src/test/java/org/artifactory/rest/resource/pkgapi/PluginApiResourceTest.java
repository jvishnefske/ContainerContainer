/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.rest.resource.pkgapi;

import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.artifactory.addon.plugin.pkgtype.PluginApiMount;
import org.artifactory.addon.plugin.pkgtype.PluginApiRequest;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.PluginPackageTypeRegistry;
import org.artifactory.addon.plugin.pkgtype.impl.PluginApiService;
import org.artifactory.addon.plugin.pkgtype.impl.PluginPackageTypeResolver;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.glassfish.jersey.server.CloseableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A1 to A5 at the REST layer: {@link DockerPluginApiResource} and {@link PkgPluginApiResource} through
 * {@link PluginApiEndpoint} and a real {@link PluginApiService} over mocked repository and security services.
 */
class PluginApiResourceTest {

    private static final long LARGE = 64L * 1024 * 1024;

    private final InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
    private final PluginPackageTypeResolver resolver = mock(PluginPackageTypeResolver.class);
    private final PluginPackageTypeRegistry registry = mock(PluginPackageTypeRegistry.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final SecurityService securityService = mock(SecurityService.class);
    private final CloseableService closeableService = mock(CloseableService.class);
    private final List<Closeable> registeredForClose = new ArrayList<>();
    private final List<PluginApiRequest> seen = new ArrayList<>();
    private final AtomicReference<Function<PluginApiRequest, PluginApiResponse>> behaviour = new AtomicReference<>();
    private final PluginPackageType type = PluginPackageType.builder("oci", "registry").api(request -> {
        seen.add(request);
        return behaviour.get().apply(request);
    }).build();
    @SuppressWarnings("rawtypes")
    private final LocalRepo repo = mock(LocalRepo.class);
    private DockerPluginApiResource docker;
    private PkgPluginApiResource pkg;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PluginApiService service =
                new PluginApiService(repositoryService, resolver, registry, authorizationService, securityService);
        PluginApiEndpoint endpoint = new PluginApiEndpoint(service, new PluginApiEndpoint.UrlResolver() {
            @Override
            public String platformUrl(HttpServletRequest request) {
                return "https://reg.example.com";
            }

            @Override
            public String contextUrl(HttpServletRequest request) {
                return "https://reg.example.com/artifactory";
            }
        });
        docker = new DockerPluginApiResource(endpoint, closeableService);
        pkg = new PkgPluginApiResource(endpoint, closeableService);
        when(closeableService.add(any())).thenAnswer(invocation -> registeredForClose.add(invocation.getArgument(0)));
        when(repositoryService.repositoryByKey("images")).thenReturn(repo);
        when(repo.isLocal()).thenReturn(true);
        when(resolver.routableTypeOf(repo)).thenReturn(Optional.of(type));
        when(registry.all()).thenReturn(List.of(type));
        when(securityService.currentUsername()).thenReturn("alice");
        when(authorizationService.canRead(any())).thenReturn(true);
        when(authorizationService.canDeploy(any())).thenReturn(true);
        when(authorizationService.canDelete(any())).thenReturn(true);
        behaviour.set(request -> PluginApiResponse.status(202).withHeader("Docker-Upload-UUID", "u1"));
    }

    @Test
    void everyMethodReachesTheHandlerWithItsRequest() throws Exception {
        for (String method : List.of("GET", "HEAD", "PUT", "POST", "PATCH", "DELETE")) {
            seen.clear();
            HttpServletRequest request = request(method, "/artifactory/api/docker/images/v2/a/blobs/uploads/",
                    "digest=sha256%3Aabc&n=1", new ByteArrayInputStream(new byte[0]), 0);
            Response response = callDocker(method, "images", "v2/a/blobs/uploads/", request);
            assertEquals(202, response.getStatus(), method);
            assertEquals("u1", response.getHeaderString("Docker-Upload-UUID"), method);
            PluginApiRequest received = seen.get(0);
            assertEquals(method, received.method());
            assertEquals("images", received.repoKey());
            assertEquals("v2/a/blobs/uploads/", received.path());
            assertEquals(Optional.of("sha256:abc"), received.queryParam("digest"));
            assertEquals(Optional.of("application/octet-stream"), received.header("Content-Type"));
            assertEquals("alice", received.username());
            assertFalse(received.anonymous());
        }
    }

    @Test
    void pkgPrefixServesTheSameApi() throws Exception {
        HttpServletRequest request = request("PATCH", "/artifactory/api/pkg/images/x", null, emptyBody(), -1);
        Response response = pkg.patch("images", "x", request);
        assertEquals(202, response.getStatus());
        assertEquals(PluginApiMount.PKG, seen.get(0).mount());
        assertEquals("https://reg.example.com/artifactory/api/pkg/images/y", seen.get(0).urlFor("y"));
    }

    @Test
    void repositoryWithoutApiTypeIs404AndRunsNoPluginCode() throws Exception {
        when(resolver.routableTypeOf(repo)).thenReturn(Optional.of(PluginPackageType.builder("plain", "p").build()));
        Response response = docker.get("images", "v2/", request("GET", "/x", null, emptyBody(), 0));
        assertEquals(404, response.getStatus());
        assertTrue(body(response).contains("no plugin package type with an HTTP API"));
        assertEquals(404, docker.get("missing", "v2/", request("GET", "/x", null, emptyBody(), 0)).getStatus());
        assertTrue(seen.isEmpty());
    }

    @Test
    void anonymousWithoutPermissionGets401WithTheBasicChallengeAndAuthenticatedGets403() throws Exception {
        when(authorizationService.canDeploy(any())).thenReturn(false);
        when(securityService.isAnonymous()).thenReturn(true);
        Response anonymous = docker.put("images", "v2/a/manifests/1", request("PUT", "/x", null, emptyBody(), 0));
        assertEquals(401, anonymous.getStatus());
        assertEquals("Basic realm=\"Artifactory Realm\"", anonymous.getHeaderString("WWW-Authenticate"));
        when(securityService.isAnonymous()).thenReturn(false);
        Response forbidden = docker.post("images", "v2/a/blobs/uploads/", request("POST", "/x", null, emptyBody(), 0));
        assertEquals(403, forbidden.getStatus());
        assertNull(forbidden.getHeaderString("WWW-Authenticate"));
        assertEquals(202, docker.get("images", "v2/", request("GET", "/x", null, emptyBody(), 0)).getStatus());
        when(authorizationService.canDelete(any())).thenReturn(false);
        assertEquals(403, docker.delete("images", "v2/a/manifests/1",
                request("DELETE", "/x", null, emptyBody(), 0)).getStatus());
        assertEquals(1, seen.size(), "only the permitted GET reached the handler");
    }

    @Test
    void blackedOutRepositoryIsRefused() throws Exception {
        when(repo.isBlackedOut()).thenReturn(true);
        Response response = docker.get("images", "v2/", request("GET", "/x", null, emptyBody(), 0));
        assertEquals(404, response.getStatus());
        assertTrue(body(response).contains("blacked out"));
        assertTrue(seen.isEmpty());
    }

    @Test
    void largeBodiesAreStreamedInBothDirections() throws Exception {
        ServletInputStream upload = servletStream(new ByteArrayInputStream(new byte[]{42}));
        AtomicBoolean closed = new AtomicBoolean();
        InputStream download = new GeneratedStream(LARGE, closed);
        behaviour.set(request -> {
            assertSame(upload, request.body(), "the handler gets the servlet stream itself, unread");
            assertEquals(LARGE, request.contentLength());
            return PluginApiResponse.stream(200, download, LARGE).withHeader("Content-Length", "1");
        });
        Response response = docker.patch("images", "v2/a/blobs/uploads/u1",
                request("PATCH", "/x", null, upload, LARGE));
        assertEquals(String.valueOf(LARGE), response.getHeaderString("Content-Length"));
        assertEquals("application/octet-stream", response.getHeaderString("Content-Type"));
        assertTrue(registeredForClose.contains(download), "registered for closing at request end");
        CountingOutputStream out = new CountingOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        assertEquals(LARGE, out.count);
        assertTrue(out.largestWrite <= 64 * 1024, "copied in pieces, not buffered whole");
        assertTrue(closed.get());
    }

    @Test
    void headSendsHeadersOnlyAndClosesTheStream() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        behaviour.set(request -> PluginApiResponse.stream(200, new GeneratedStream(10, closed), 10)
                .withHeader("Docker-Content-Digest", "sha256:abc"));
        Response response = docker.head("images", "v2/a/blobs/sha256:abc", request("HEAD", "/x", null, emptyBody(), 0));
        assertEquals("10", response.getHeaderString("Content-Length"));
        assertEquals("sha256:abc", response.getHeaderString("Docker-Content-Digest"));
        assertTrue(closed.get());
        assertEquals("", body(response));

        behaviour.set(request -> PluginApiResponse.status(200).withHeader("Content-Length", "1234"));
        Response sizedHead = docker.head("images", "v2/a/blobs/sha256:abc",
                request("HEAD", "/x", null, emptyBody(), 0));
        assertEquals("1234", sizedHead.getHeaderString("Content-Length"), "a HEAD may advertise unsent content");
    }

    @Test
    void emptyBodyGetsZeroLengthAndBytesGetTheirLength() throws Exception {
        Response accepted = docker.post("images", "v2/a/blobs/uploads/", request("POST", "/x", null, emptyBody(), 0));
        assertEquals("0", accepted.getHeaderString("Content-Length"));
        behaviour.set(request -> PluginApiResponse.json(200, "{\"tags\":[]}"));
        Response json = docker.get("images", "v2/a/tags/list", request("GET", "/x", "n=10", emptyBody(), 0));
        assertEquals("11", json.getHeaderString("Content-Length"));
        assertEquals("application/json", json.getHeaderString("Content-Type"));
        assertEquals("{\"tags\":[]}", body(json));
    }

    @Test
    void handlerFailureIsAGeneric500() throws Exception {
        behaviour.set(request -> {
            throw new IllegalStateException("database password is hunter2");
        });
        Response response = docker.get("images", "v2/", request("GET", "/x", null, emptyBody(), 0));
        assertEquals(500, response.getStatus());
        assertFalse(body(response).contains("hunter2"));
    }

    @Test
    void forwardedV2RequestGetsThePlatformBaseUrl() throws Exception {
        HttpServletRequest request = request("POST", "/artifactory/api/docker/images/v2/library/a/blobs/uploads/",
                null, emptyBody(), 0);
        when(request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI))
                .thenReturn("/artifactory/v2/images/library/a/blobs/uploads/");
        when(request.getAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH)).thenReturn("/artifactory");
        docker.post("images", "v2/library/a/blobs/uploads/", request);
        PluginApiRequest received = seen.get(0);
        assertEquals(PluginApiMount.V2, received.mount());
        assertEquals("https://reg.example.com", received.baseUrl());
        assertEquals("https://reg.example.com/v2/images/library/a/blobs/uploads/u1",
                received.urlFor("v2/library/a/blobs/uploads/u1"));

        seen.clear();
        docker.post("images", "v2/library/a/blobs/uploads/", request("POST", "/x", null, emptyBody(), 0));
        assertEquals(PluginApiMount.DOCKER, seen.get(0).mount());
        assertEquals("https://reg.example.com/artifactory/api/docker/images/v2/x", seen.get(0).urlFor("v2/x"));
    }

    @Test
    void pingAnswersRegistryClients() throws Exception {
        Response ok = docker.ping(pingRequest("/artifactory/v2/"));
        assertEquals(200, ok.getStatus());
        assertEquals("registry/2.0", ok.getHeaderString("Docker-Distribution-API-Version"));
        assertEquals("{}", body(ok));
        assertEquals(200, docker.ping(pingRequest("/artifactory/v2")).getStatus());
        assertEquals(200, docker.pingHead(pingRequest(null)).getStatus(), "direct /api/docker/v2/");

        when(securityService.isAnonymous()).thenReturn(true);
        Response challenge = docker.ping(pingRequest("/artifactory/v2/"));
        assertEquals(401, challenge.getStatus());
        assertEquals("Basic realm=\"Artifactory Realm\"", challenge.getHeaderString("WWW-Authenticate"));
        assertEquals("registry/2.0", challenge.getHeaderString("Docker-Distribution-API-Version"));
        when(securityService.isAnonAccessEnabled()).thenReturn(true);
        assertEquals(200, docker.ping(pingRequest("/artifactory/v2/")).getStatus());

        assertEquals(404, docker.ping(pingRequest("/artifactory/v2/_catalog")).getStatus(),
                "other single-segment paths the rewrite sends to the ping are not the ping");
        when(registry.all()).thenReturn(List.of(PluginPackageType.builder("plain", "p").build()));
        assertEquals(404, docker.ping(pingRequest("/artifactory/v2/")).getStatus());
        assertTrue(seen.isEmpty());
    }

    @Test
    void resourcesDeclareEveryMethodOnTheirPaths() {
        assertEquals("docker", DockerPluginApiResource.class.getAnnotation(jakarta.ws.rs.Path.class).value());
        assertEquals("pkg", PkgPluginApiResource.class.getAnnotation(jakarta.ws.rs.Path.class).value());
        for (Class<?> resource : List.of(DockerPluginApiResource.class, PkgPluginApiResource.class)) {
            List<String> methods = new ArrayList<>();
            for (Method method : resource.getDeclaredMethods()) {
                jakarta.ws.rs.Path path = method.getAnnotation(jakarta.ws.rs.Path.class);
                if (path != null && path.value().equals("{repoKey}/{path: .*}")) {
                    for (Class<? extends java.lang.annotation.Annotation> verb : List.of(jakarta.ws.rs.GET.class,
                            jakarta.ws.rs.HEAD.class, jakarta.ws.rs.PUT.class, jakarta.ws.rs.POST.class,
                            jakarta.ws.rs.PATCH.class, jakarta.ws.rs.DELETE.class)) {
                        if (method.isAnnotationPresent(verb)) {
                            methods.add(verb.getSimpleName());
                        }
                    }
                }
            }
            Collections.sort(methods);
            assertEquals(List.of("DELETE", "GET", "HEAD", "PATCH", "POST", "PUT"), methods, resource.getName());
        }
    }

    private Response callDocker(String method, String repoKey, String path, HttpServletRequest request)
            throws IOException {
        return switch (method) {
            case "GET" -> docker.get(repoKey, path, request);
            case "HEAD" -> docker.head(repoKey, path, request);
            case "PUT" -> docker.put(repoKey, path, request);
            case "POST" -> docker.post(repoKey, path, request);
            case "PATCH" -> docker.patch(repoKey, path, request);
            case "DELETE" -> docker.delete(repoKey, path, request);
            default -> throw new IllegalArgumentException(method);
        };
    }

    private HttpServletRequest pingRequest(String forwardedFrom) {
        HttpServletRequest request = request("GET", "/artifactory/api/docker/v2/", null, emptyBody(), 0);
        when(request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI)).thenReturn(forwardedFrom);
        when(request.getAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH)).thenReturn("/artifactory");
        return request;
    }

    private static HttpServletRequest request(String method, String uri, String query, InputStream body,
            long length) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getQueryString()).thenReturn(query);
        when(request.getContentLengthLong()).thenReturn(length);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of("Content-Type")));
        when(request.getHeaders("Content-Type"))
                .thenAnswer(invocation -> Collections.enumeration(List.of("application/octet-stream")));
        try {
            when(request.getInputStream()).thenReturn(body instanceof ServletInputStream servlet ? servlet
                    : servletStream(body));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return request;
    }

    private static InputStream emptyBody() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static String body(Response response) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ((StreamingOutput) response.getEntity()).write(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static ServletInputStream servletStream(InputStream in) {
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return in.read();
            }
        };
    }

    /** Produces {@code size} bytes without holding them, and records being closed. */
    private static final class GeneratedStream extends InputStream {
        private long remaining;
        private final AtomicBoolean closed;

        GeneratedStream(long size, AtomicBoolean closed) {
            this.remaining = size;
            this.closed = closed;
        }

        @Override
        public int read() {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return 'x';
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (remaining <= 0) {
                return -1;
            }
            int n = (int) Math.min(length, remaining);
            java.util.Arrays.fill(buffer, offset, offset + n, (byte) 'x');
            remaining -= n;
            return n;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    /** Counts bytes and remembers the largest single write. */
    private static final class CountingOutputStream extends OutputStream {
        long count;
        int largestWrite;

        @Override
        public void write(int b) {
            count++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            count += length;
            largestWrite = Math.max(largestWrite, length);
        }
    }
}
