/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.pkgtype;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A1, A2, A4: the request and response values of the plugin HTTP API contract, and base URL derivation. */
class PluginApiContractTest {

    @Test
    void requestNormalisesMethodPathAndHeaderNamesAndCopiesMaps() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", new ArrayList<>(List.of("application/octet-stream")));
        headers.put("OCI-Chunk-Min-Length", List.of("1024"));
        PluginApiRequest request = PluginApiRequest.builder("patch", "images", "/v2/a/blobs/uploads/1")
                .headers(headers).build();
        headers.get("Content-Type").add("mutated");
        assertEquals("PATCH", request.method());
        assertEquals("v2/a/blobs/uploads/1", request.path());
        assertEquals(List.of("application/octet-stream"), request.headers().get("content-type"));
        assertEquals(Optional.of("1024"), request.header("oci-chunk-min-length"));
        assertEquals(Optional.of("1024"), request.header("OCI-Chunk-Min-Length"));
        assertThrows(UnsupportedOperationException.class, () -> request.headers().put("x", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> request.headerValues("content-type").add("x"));
    }

    @Test
    void requestRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> PluginApiRequest.builder(" ", "r", "").build());
        assertThrows(IllegalArgumentException.class, () -> PluginApiRequest.builder("GET", "", "").build());
        assertThrows(IllegalArgumentException.class,
                () -> PluginApiRequest.builder("GET", "r", "").body(InputStream.nullInputStream(), -2).build());
        assertThrows(NullPointerException.class, () -> PluginApiRequest.builder("GET", "r", "").body(null, 0).build());
    }

    @Test
    void queryStringIsParsedInOrderAndDecoded() {
        Map<String, List<String>> query = PluginApiRequest.parseQuery("n=10&last=library%2Falpine&flag&n=20&&e=");
        assertEquals(List.of("n", "last", "flag", "e"), List.copyOf(query.keySet()));
        assertEquals(List.of("10", "20"), query.get("n"));
        assertEquals(List.of("library/alpine"), query.get("last"));
        assertEquals(List.of(""), query.get("flag"));
        assertTrue(PluginApiRequest.parseQuery(null).isEmpty());
        PluginApiRequest request = PluginApiRequest.builder("POST", "images", "v2/a/blobs/uploads/")
                .query(PluginApiRequest.parseQuery("mount=sha256%3Aabc&from=other")).build();
        assertEquals(Optional.of("sha256:abc"), request.queryParam("mount"));
        assertEquals(List.of("other"), request.queryParams("from"));
    }

    @Test
    void readBodyIsBounded() throws IOException {
        PluginApiRequest small = PluginApiRequest.builder("PUT", "r", "m")
                .body(new ByteArrayInputStream("{}".getBytes()), -1).build();
        assertArrayEquals("{}".getBytes(), small.readBody(2).orElseThrow());
        PluginApiRequest large = PluginApiRequest.builder("PUT", "r", "m")
                .body(new ByteArrayInputStream(new byte[100]), -1).build();
        assertTrue(large.readBody(10).isEmpty());
        PluginApiRequest declared = PluginApiRequest.builder("PUT", "r", "m")
                .body(InputStream.nullInputStream(), 1_000_000).build();
        assertTrue(declared.readBody(10).isEmpty());
    }

    @Test
    void mountIsDetectedFromTheForwardedUri() {
        assertEquals(PluginApiMount.V2,
                PluginApiMount.detect(false, "/v2/images/library/alpine/manifests/1", "images"));
        assertEquals(PluginApiMount.DOCKER, PluginApiMount.detect(false, null, "images"));
        assertEquals(PluginApiMount.DOCKER, PluginApiMount.detect(false, "/v2/library/alpine/manifests/1", "reg"),
                "a subdomain rewrite does not put the key in the path, so the docker API URL is used");
        assertEquals(PluginApiMount.PKG, PluginApiMount.detect(true, "/v2/images/x", "images"));
    }

    @Test
    void baseUrlAndUrlForGiveTheUrlTheClientUses() {
        String platform = "https://reg.example.com/";
        String context = "https://reg.example.com/artifactory";
        PluginApiRequest viaV2 = PluginApiRequest.builder("POST", "images", "v2/library/alpine/blobs/uploads/")
                .baseUrl(PluginApiMount.V2.baseUrl(platform, context), PluginApiMount.V2).build();
        assertEquals("https://reg.example.com", viaV2.baseUrl());
        assertEquals("https://reg.example.com/v2/images/library/alpine/blobs/uploads/42",
                viaV2.urlFor("v2/library/alpine/blobs/uploads/42"));
        assertEquals("https://reg.example.com/v2/images/", viaV2.urlFor("v2"));

        PluginApiRequest direct = PluginApiRequest.builder("POST", "images", "v2/x")
                .baseUrl(PluginApiMount.DOCKER.baseUrl(platform, context), PluginApiMount.DOCKER).build();
        assertEquals("https://reg.example.com/artifactory/api/docker/images/v2/library/alpine/blobs/uploads/42",
                direct.urlFor("/v2/library/alpine/blobs/uploads/42"));

        PluginApiRequest pkg = PluginApiRequest.builder("GET", "files", "index.json")
                .baseUrl(PluginApiMount.PKG.baseUrl(platform, context + "/"), PluginApiMount.PKG).build();
        assertEquals("https://reg.example.com/artifactory/api/pkg/files/a/b", pkg.urlFor("a/b"));
    }

    @Test
    void responseEnforcesStatusRangeAndImmutableHeaders() {
        assertThrows(IllegalArgumentException.class, () -> PluginApiResponse.status(99));
        assertThrows(IllegalArgumentException.class, () -> PluginApiResponse.status(600));
        assertThrows(NullPointerException.class,
                () -> new PluginApiResponse(200, null, new PluginApiResponse.Body.None()));
        assertThrows(NullPointerException.class, () -> new PluginApiResponse(200, Map.of(), null));
        PluginApiResponse response = PluginApiResponse.status(202)
                .withHeader("Location", "/v2/a/blobs/uploads/1")
                .withHeader("Docker-Upload-UUID", "1")
                .withHeader("location", "ignored-second-value");
        assertEquals(List.of("/v2/a/blobs/uploads/1", "ignored-second-value"), response.headers().get("Location"));
        assertEquals(Optional.of("1"), response.header("docker-upload-uuid"));
        assertThrows(UnsupportedOperationException.class, () -> response.headers().put("x", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> response.headers().get("Location").add("x"));
        assertThrows(IllegalArgumentException.class, () -> response.withHeader("X-Bad", "a\r\nInjected: yes"));
        assertThrows(IllegalArgumentException.class, () -> response.withHeader("X\nBad", "a"));
        assertEquals(204, response.withStatus(204).status());
        assertEquals(202, response.status());
    }

    @Test
    void responseBodies() {
        byte[] content = {1, 2, 3};
        PluginApiResponse bytes = PluginApiResponse.bytes(200, "application/octet-stream", content);
        content[0] = 9;
        PluginApiResponse.Body.Bytes body = (PluginApiResponse.Body.Bytes) bytes.body();
        assertArrayEquals(new byte[]{1, 2, 3}, body.content());
        body.content()[1] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, body.content());
        assertEquals(3, body.size());
        assertEquals(Optional.of("application/json"), PluginApiResponse.json(200, "{}").header("content-type"));
        assertEquals(-1, PluginApiResponse.stream(200, InputStream.nullInputStream(), -1).body().size());
        assertThrows(IllegalArgumentException.class,
                () -> PluginApiResponse.stream(200, InputStream.nullInputStream(), -5));
        assertEquals(0, PluginApiResponse.status(201).body().size());
    }

    @Test
    void packageTypeApiIsOptionalAndTheOldConstructorStillWorks() {
        PluginApiHandler handler = request -> PluginApiResponse.status(200);
        assertEquals(Optional.of(handler), PluginPackageType.builder("t", "p").api(handler).build().api());
        PluginPackageType legacy = new PluginPackageType("t", "T", "p", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
        assertTrue(legacy.api().isEmpty());
    }
}
