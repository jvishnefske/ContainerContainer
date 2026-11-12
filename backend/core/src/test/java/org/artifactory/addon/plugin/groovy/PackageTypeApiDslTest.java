/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import org.artifactory.addon.plugin.pkgtype.PluginApiHandler;
import org.artifactory.addon.plugin.pkgtype.PluginApiMount;
import org.artifactory.addon.plugin.pkgtype.PluginApiRequest;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.resource.ResourceStreamHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A6: the {@code api} block of the {@code packageType} DSL, map and record returns, and fail-closed adaptation. */
class PackageTypeApiDslTest {

    private static final String SCRIPT = """
            packageType('oci-test') {
                api { request ->
                    switch (request.path) {
                        case 'v2/record':
                            return PluginApiResponse.json(200, '{"ok":true}')
                                    .withHeader('Docker-Content-Digest', 'sha256:abc')
                        case 'v2/map':
                            return [status: 202,
                                    headers: ['Location': request.urlFor('v2/x/blobs/uploads/1'),
                                              'Range': ['0-9'], 'OCI-Chunk-Min-Length': 1024],
                                    body: "hi ${request.username}"]
                        case 'v2/bytes':
                            return [body: 'raw'.bytes]
                        case 'v2/stream':
                            return [body: [stream: new ByteArrayInputStream('abc'.bytes), size: 3]]
                        case 'v2/echo':
                            return [status: 201, body: [stream: request.body]]
                        case 'v2/none':
                            return [status: 404]
                        case 'v2/throw':
                            throw new IllegalStateException('handler bug with secret')
                        case 'v2/cancel':
                            throw new CancelException('already exists', 409)
                        case 'v2/cancel-200':
                            throw new CancelException('odd', 200)
                        case 'v2/badstatus':
                            return [status: 700]
                        case 'v2/badkey':
                            return [status: 200, bogus: 1, body: [stream: calls[0]]]
                        case 'v2/handle':
                            return [body: calls[0]]
                        case 'v2/string':
                            return 'just a string'
                        default:
                            return null
                    }
                }
            }
            packageType('no-api') { }
            """;

    @TempDir
    Path dir;

    private PluginTestRig rig;
    private PluginApiHandler api;

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        rig.write("oci.groovy", SCRIPT);
        rig.reload();
        assertTrue(rig.reports.isEmpty(), () -> "load reports: " + rig.reports);
        api = rig.registry.find("oci-test").orElseThrow().api().orElseThrow();
    }

    private PluginApiResponse call(String path) {
        return api.handle(PluginApiRequest.builder("GET", "images", path)
                .caller("alice", false, false)
                .baseUrl("https://reg.example.com", PluginApiMount.V2).build());
    }

    private static String text(PluginApiResponse response) {
        return new String(((PluginApiResponse.Body.Bytes) response.body()).content(), StandardCharsets.UTF_8);
    }

    @Test
    void typeWithoutApiBlockHasNoApi() {
        assertTrue(rig.registry.find("no-api").orElseThrow().api().isEmpty());
    }

    @Test
    void recordReturnIsUsedAsIs() {
        PluginApiResponse response = call("v2/record");
        assertEquals(200, response.status());
        assertEquals(Optional.of("sha256:abc"), response.header("Docker-Content-Digest"));
        assertEquals("{\"ok\":true}", text(response));
    }

    @Test
    void mapReturnIsConverted() {
        PluginApiResponse response = call("v2/map");
        assertEquals(202, response.status());
        assertEquals(List.of("https://reg.example.com/v2/images/x/blobs/uploads/1"),
                response.headers().get("Location"));
        assertEquals(List.of("0-9"), response.headers().get("Range"));
        assertEquals(List.of("1024"), response.headers().get("OCI-Chunk-Min-Length"));
        assertEquals("hi alice", text(response));
        assertEquals("raw", text(call("v2/bytes")));
        assertInstanceOf(PluginApiResponse.Body.None.class, call("v2/none").body());
        assertEquals(404, call("v2/none").status());
    }

    @Test
    void streamBodiesAreHandedThroughUnread() throws IOException {
        PluginApiResponse.Body.Stream stream =
                assertInstanceOf(PluginApiResponse.Body.Stream.class, call("v2/stream").body());
        assertEquals(3, stream.size());
        assertEquals("abc", new String(stream.input().readAllBytes(), StandardCharsets.UTF_8));

        InputStream requestBody = new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8));
        PluginApiResponse echo = api.handle(PluginApiRequest.builder("PUT", "images", "v2/echo")
                .body(requestBody, 7).build());
        PluginApiResponse.Body.Stream echoed = assertInstanceOf(PluginApiResponse.Body.Stream.class, echo.body());
        assertSame(requestBody, echoed.input(), "the handler sees the original request stream");
        assertEquals(-1, echoed.size());
    }

    @Test
    void resourceStreamHandleBodyIsStreamedWithItsSizeAndClosedWithTheStream() throws IOException {
        AtomicBoolean closed = new AtomicBoolean();
        rig.calls.add(new ResourceStreamHandle() {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream("blob".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public long getSize() {
                return 4;
            }

            @Override
            public void close() {
                closed.set(true);
            }
        });
        PluginApiResponse.Body.Stream body =
                assertInstanceOf(PluginApiResponse.Body.Stream.class, call("v2/handle").body());
        assertEquals(4, body.size());
        body.input().close();
        assertTrue(closed.get());
    }

    @Test
    void failuresAndInvalidReturnsFailClosedWith500AndAreReported() {
        for (String path : List.of("v2/throw", "v2/badstatus", "v2/string", "v2/unknown")) {
            PluginApiResponse response = call(path);
            assertEquals(500, response.status(), path);
            assertTrue(!text(response).contains("secret"), path);
        }
        assertEquals(4, rig.reports.size());
        assertTrue(rig.reports.stream().allMatch(report -> report.pluginName().equals("oci")
                && report.what().equals("packageType(oci-test).api")));
    }

    @Test
    void invalidReturnClosesTheStreamItCarried() {
        AtomicBoolean closed = new AtomicBoolean();
        rig.calls.add(new ByteArrayInputStream(new byte[1]) {
            @Override
            public void close() {
                closed.set(true);
            }
        });
        assertEquals(500, call("v2/badkey").status());
        assertTrue(closed.get());
    }

    @Test
    void cancelExceptionAnswersWithItsStatus() {
        PluginApiResponse cancelled = call("v2/cancel");
        assertEquals(409, cancelled.status());
        assertEquals("already exists", text(cancelled));
        assertEquals(500, call("v2/cancel-200").status());
        assertTrue(rig.reports.isEmpty());
    }

    @Test
    void conversionRejectsWhatIsNotAResponse() {
        assertInstanceOf(ApiResponseConversion.Result.Invalid.class, ApiResponseConversion.convert(42));
        assertInstanceOf(ApiResponseConversion.Result.Invalid.class,
                ApiResponseConversion.convert(Map.of("status", 200.5)));
        assertInstanceOf(ApiResponseConversion.Result.Invalid.class,
                ApiResponseConversion.convert(Map.of("headers", "x")));
        assertInstanceOf(ApiResponseConversion.Result.Invalid.class,
                ApiResponseConversion.convert(Map.of("body", Map.of("stream", "not a stream"))));
        assertInstanceOf(ApiResponseConversion.Result.Invalid.class,
                ApiResponseConversion.convert(Map.of("headers", Map.of("X-Bad", "a\nb"))));
        ApiResponseConversion.Result.Valid valid = assertInstanceOf(ApiResponseConversion.Result.Valid.class,
                ApiResponseConversion.convert(Map.of()));
        assertEquals(200, valid.response().status());
    }
}
