/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.addon.plugin.groovy;

import java.util.function.Predicate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.addon.plugin.pkgtype.PluginApiHandler;
import org.artifactory.addon.plugin.pkgtype.PluginApiMount;
import org.artifactory.addon.plugin.pkgtype.PluginApiRequest;
import org.artifactory.addon.plugin.pkgtype.PluginApiResponse;
import org.artifactory.addon.plugin.pkgtype.PluginPackageType;
import org.artifactory.addon.plugin.pkgtype.UploadDecision;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequestBuilder;
import org.artifactory.request.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The shipped OCI registry plugin {@code plugins/oci/oci-registry.groovy}, loaded into the plugin engine with the
 * real {@code repositories} binding over an in-memory repository, driven through its {@code api} handler for every
 * registry flow (O2-O8) and for the validation and isolation rules: invalid names, digests, tags and session ids
 * are refused before any storage access, mounts never leave the request's repository, uploads are verified against
 * their digest, and manifests are size-capped.
 */
class OciRegistryPluginTest {

    /** Relative to the backend/core module directory, where Maven runs the tests. */
    private static final Path PLUGIN = Path.of("..", "..", "plugins", "oci", "oci-registry.groovy");
    private static final String REPO = "oci-local";
    private static final String BASE = "http://reg.test";
    private static final String OCI_MANIFEST = "application/vnd.oci.image.manifest.v1+json";
    private static final String OCI_INDEX = "application/vnd.oci.image.index.v1+json";
    private static final String DOCKER_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json";
    private static final String DOCKER_LIST = "application/vnd.docker.distribution.manifest.list.v2+json";

    @TempDir
    Path dir;

    private final InMemoryRepositoryService store = new InMemoryRepositoryService();
    /** The caller's per-path read permission as the binding sees it; tests narrow it to model path-scoped targets. */
    private Predicate<RepoPath> canRead = path -> true;
    private PluginTestRig rig;
    private PluginPackageType type;
    private PluginApiHandler api;
    private String user = "admin";
    private boolean admin = true;

    /** A response with its body read and its stream closed. */
    record Resp(int status, PluginApiResponse response, byte[] body) {
        String header(String name) {
            return response.header(name).orElse(null);
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }

        Object json() {
            return readJson(body);
        }

        @SuppressWarnings("unchecked")
        String errorCode() {
            Map<String, Object> doc = (Map<String, Object>) json();
            return (String) ((Map<String, Object>) ((List<Object>) doc.get("errors")).get(0)).get("code");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        rig = new PluginTestRig(dir);
        rig.extraBindings.put("repositories",
                new PluginRepositories(store.service(), mock(PropertiesService.class), path -> canRead.test(path)));
        rig.extraBindings.put("security", new PluginSecurity(mock(SecurityService.class)));
        rig.write("oci-registry.groovy", Files.readString(PLUGIN, StandardCharsets.UTF_8));
        rig.reload();
        assertTrue(rig.reports.isEmpty(), String.valueOf(rig.reports));
        type = rig.registry.find("oci-registry").orElseThrow();
        api = type.api().orElseThrow();
    }

    @AfterEach
    void nothingLeaksOutOfTheRepository() {
        assertTrue(rig.reports.isEmpty(), String.valueOf(rig.reports));
        assertTrue(Set.of(REPO).containsAll(store.touchedRepoKeys()), "touched " + store.touchedRepoKeys());
        assertEquals(0, store.openHandles(), "content handles left open");
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private Resp call(String method, String path, String rawQuery, Map<String, String> headers, byte[] body) {
        return call(method, path, rawQuery, headers, body == null ? null : new ByteArrayInputStream(body),
                body == null ? 0 : body.length);
    }

    private Resp call(String method, String path, String rawQuery, Map<String, String> headers, InputStream body,
            long length) {
        Map<String, List<String>> headerMap = new LinkedHashMap<>();
        headers.forEach((k, v) -> headerMap.put(k, List.of(v)));
        PluginApiRequest.Builder builder = PluginApiRequest.builder(method, REPO, path)
                .query(PluginApiRequest.parseQuery(rawQuery))
                .headers(headerMap)
                .caller(user, admin, "anonymous".equals(user))
                .baseUrl(BASE, PluginApiMount.V2);
        if (body != null) {
            builder.body(body, length);
        }
        PluginApiResponse response = api.handle(builder.build());
        byte[] bytes = switch (response.body()) {
            case PluginApiResponse.Body.None none -> new byte[0];
            case PluginApiResponse.Body.Bytes b -> b.content();
            case PluginApiResponse.Body.Stream s -> {
                try (InputStream in = s.input()) {
                    byte[] read = in.readAllBytes();
                    if (s.size() >= 0) {
                        assertEquals(s.size(), read.length, "declared stream size");
                    }
                    yield read;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        assertEquals("registry/2.0", response.header("Docker-Distribution-API-Version").orElse(null));
        if (bytes.length > 0) {
            assertTrue(response.header("Content-Type").isPresent(), "a body always has a Content-Type");
        }
        return new Resp(response.status(), response, bytes);
    }

    private Resp call(String method, String path) {
        return call(method, path, null, Map.of(), (byte[]) null);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private static byte[] toJson(Object document) {
        try {
            return JSON.writeValueAsBytes(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object readJson(byte[] document) {
        try {
            return JSON.readValue(document, Object.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String digest(byte[] content) {
        return "sha256:" + InMemoryRepositoryService.sha256(content);
    }

    private static String hex(byte[] content) {
        return InMemoryRepositoryService.sha256(content);
    }

    /** Pushes {@code content} as a blob of {@code name} with a monolithic POST. */
    private String pushBlob(String name, byte[] content) {
        Resp r = call("POST", "v2/" + name + "/blobs/uploads/", "digest=" + digest(content), Map.of(), content);
        assertEquals(201, r.status(), r.text());
        return digest(content);
    }

    private static Map<String, Object> descriptor(String mediaType, byte[] content) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("mediaType", mediaType);
        d.put("digest", digest(content));
        d.put("size", content.length);
        return d;
    }

    private static byte[] imageManifest(String mediaType, byte[] config, byte[]... layers) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", 2);
        if (mediaType != null) {
            m.put("mediaType", mediaType);
        }
        m.put("config", descriptor("application/vnd.oci.image.config.v1+json", config));
        m.put("layers", java.util.Arrays.stream(layers)
                .map(l -> descriptor("application/vnd.oci.image.layer.v1.tar+gzip", l)).toList());
        return toJson(m);
    }

    private static byte[] index(String mediaType, byte[]... manifests) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schemaVersion", 2);
        m.put("mediaType", mediaType);
        m.put("manifests", java.util.Arrays.stream(manifests).map(x -> descriptor(OCI_MANIFEST, x)).toList());
        return toJson(m);
    }

    private Resp putManifest(String name, String ref, String contentType, byte[] manifest) {
        return call("PUT", "v2/" + name + "/manifests/" + ref, null,
                contentType == null ? Map.of() : Map.of("Content-Type", contentType), manifest);
    }

    /** Pushes a config and one layer to {@code name} and returns an OCI manifest referencing them. */
    private byte[] pushImage(String name, String seed) {
        byte[] config = bytes("{\"seed\":\"" + seed + "\"}");
        byte[] layer = bytes("layer of " + seed);
        pushBlob(name, config);
        pushBlob(name, layer);
        return imageManifest(OCI_MANIFEST, config, layer);
    }

    private String uploadId(Resp r) {
        String id = r.header("Docker-Upload-UUID");
        assertEquals(BASE + "/v2/" + REPO + "/app/blobs/uploads/" + id, r.header("Location"));
        return id;
    }

    // --- O2: monolithic uploads -------------------------------------------------------------------------------------

    @Test
    void monolithicPostWithDigestStoresTheVerifiedBlobAndServesItWithExactHeaders() {
        byte[] content = bytes("hello blob");
        Resp r = call("POST", "v2/app/blobs/uploads/", "digest=" + digest(content), Map.of(), content);
        assertEquals(201, r.status());
        assertEquals(BASE + "/v2/" + REPO + "/app/blobs/" + digest(content), r.header("Location"));
        assertEquals(digest(content), r.header("Docker-Content-Digest"));
        assertArrayEquals(content, store.get(REPO, "app/_blobs/sha256/" + hex(content)));
        assertEquals(List.of(), store.paths(REPO, "app/_uploads"), "the upload is cleaned up");

        Resp get = call("GET", "v2/app/blobs/" + digest(content));
        assertEquals(200, get.status());
        assertArrayEquals(content, get.body());
        assertEquals("application/octet-stream", get.header("Content-Type"));
        assertEquals(digest(content), get.header("Docker-Content-Digest"));

        Resp head = call("HEAD", "v2/app/blobs/" + digest(content));
        assertEquals(200, head.status());
        assertEquals(String.valueOf(content.length), head.header("Content-Length"));
        assertEquals(0, head.body().length);

        assertEquals(404, call("GET", "v2/app/blobs/" + digest(bytes("other"))).status());
        assertEquals("BLOB_UNKNOWN", call("GET", "v2/app/blobs/" + digest(bytes("other"))).errorCode());
    }

    @Test
    void postThenSinglePutWithDigestCompletesTheUpload() {
        Resp post = call("POST", "v2/app/blobs/uploads/");
        assertEquals(202, post.status());
        assertEquals("0-0", post.header("Range"));
        assertEquals("1", post.header("OCI-Chunk-Min-Length"));
        String id = uploadId(post);

        byte[] content = bytes("put in one go");
        Resp put = call("PUT", "v2/app/blobs/uploads/" + id, "digest=" + digest(content), Map.of(), content);
        assertEquals(201, put.status(), put.text());
        assertEquals(digest(content), put.header("Docker-Content-Digest"));
        assertArrayEquals(content, call("GET", "v2/app/blobs/" + digest(content)).body());
        assertEquals(404, call("GET", "v2/app/blobs/uploads/" + id).status(), "session is gone");
    }

    @Test
    void digestMismatchDeletesTheUploadAndAnswersDigestInvalid() {
        byte[] content = bytes("real content");
        String claimed = digest(bytes("something else"));
        Resp r = call("POST", "v2/app/blobs/uploads/", "digest=" + claimed, Map.of(), content);
        assertEquals(400, r.status());
        assertEquals("DIGEST_INVALID", r.errorCode());
        assertFalse(store.has(REPO, "app/_blobs"), "nothing stored at any digest path");
        assertEquals(List.of(), store.paths(REPO, "app/_uploads"));

        String id = uploadId(call("POST", "v2/app/blobs/uploads/"));
        call("PATCH", "v2/app/blobs/uploads/" + id, null, Map.of(), bytes("part one "));
        call("PATCH", "v2/app/blobs/uploads/" + id, null, Map.of(), bytes("part two"));
        Resp put = call("PUT", "v2/app/blobs/uploads/" + id, "digest=" + claimed, Map.of(), (byte[]) null);
        assertEquals(400, put.status());
        assertEquals("DIGEST_INVALID", put.errorCode());
        assertFalse(store.has(REPO, "app/_blobs"));
        assertEquals(List.of(), store.paths(REPO, "app/_uploads"));
    }

    @Test
    void invalidAndUnsupportedDigestsAreRefusedBeforeAnyStorageAccess() {
        Map<String, String> cases = Map.of(
                "sha512:" + "a".repeat(128), "UNSUPPORTED",
                "sha256:" + "A".repeat(64), "DIGEST_INVALID",
                "sha256:abc", "DIGEST_INVALID",
                "sha256:" + "a".repeat(63) + "/", "DIGEST_INVALID",
                "nonsense", "DIGEST_INVALID");
        cases.forEach((digest, code) -> {
            Resp post = call("POST", "v2/app/blobs/uploads/", "digest=" + digest, Map.of(), bytes("x"));
            assertEquals(400, post.status(), digest);
            assertEquals(code, post.errorCode(), digest);
            if (!digest.contains("/")) {
                Resp get = call("GET", "v2/app/blobs/" + digest);
                assertEquals(400, get.status(), digest);
                assertEquals(code, get.errorCode(), digest);
            }
        });
        assertTrue(store.touchedRepoKeys().isEmpty(), "no storage access for invalid digests");
    }

    // --- O3: chunked uploads ----------------------------------------------------------------------------------------

    @Test
    void chunkedUploadChecksOrderReportsStatusAndAssemblesTheBlob() {
        byte[] chunk1 = bytes("0123456789abcdefghijk");
        byte[] chunk2 = bytes("lmnopqrstuvwxyz");
        byte[] whole = bytes("0123456789abcdefghijklmnopqrstuvwxyz");
        String id = uploadId(call("POST", "v2/app/blobs/uploads/"));
        String upload = "v2/app/blobs/uploads/" + id;
        String range1 = "0-" + (chunk1.length - 1);
        String range2 = chunk1.length + "-" + (whole.length - 1);

        Resp outOfOrder = call("PATCH", upload, null, Map.of("Content-Range", range2), chunk2);
        assertEquals(416, outOfOrder.status());
        assertEquals("0-0", outOfOrder.header("Range"));

        Resp first = call("PATCH", upload, null, Map.of("Content-Range", range1), chunk1);
        assertEquals(202, first.status());
        assertEquals(range1, first.header("Range"));
        assertEquals(id, uploadId(first));

        assertEquals(416, call("PATCH", upload, null, Map.of("Content-Range", range1), chunk1).status(),
                "a retried chunk is out of order");

        Resp status = call("GET", upload);
        assertEquals(204, status.status());
        assertEquals(range1, status.header("Range"));
        assertEquals(id, uploadId(status));

        Resp second = call("PATCH", upload, null, Map.of("Content-Range", "bytes " + range2 + "/*"), chunk2);
        assertEquals(202, second.status());
        assertEquals("0-" + (whole.length - 1), second.header("Range"));

        Resp put = call("PUT", upload, "digest=" + digest(whole), Map.of("Content-Length", "0"), (byte[]) null);
        assertEquals(201, put.status(), put.text());
        assertArrayEquals(whole, call("GET", "v2/app/blobs/" + digest(whole)).body());
        assertEquals(List.of(), store.paths(REPO, "app/_uploads"), "chunks and assembled file removed");
    }

    @Test
    void streamedPatchOfUnknownLengthAndFinalChunkInPutAreAppended() {
        String id = uploadId(call("POST", "v2/app/blobs/uploads/"));
        String upload = "v2/app/blobs/uploads/" + id;
        Resp patch = call("PATCH", upload, null, Map.of(), new ByteArrayInputStream(bytes("streamed-")), -1);
        assertEquals(202, patch.status());
        assertEquals("0-8", patch.header("Range"));
        assertEquals(202, call("PATCH", upload, null, Map.of(), new ByteArrayInputStream(new byte[0]), -1).status(),
                "an empty streamed chunk stores nothing");
        byte[] whole = bytes("streamed-and-final");
        Resp put = call("PUT", upload, "digest=" + digest(whole), Map.of("Content-Range", "9-17"), bytes("and-final"));
        assertEquals(201, put.status(), put.text());
        assertArrayEquals(whole, call("GET", "v2/app/blobs/" + digest(whole)).body());
    }

    @Test
    void chunkWhoseLengthDisagreesWithItsRangeIsRefused() {
        String id = uploadId(call("POST", "v2/app/blobs/uploads/"));
        String upload = "v2/app/blobs/uploads/" + id;
        Resp declared = call("PATCH", upload, null, Map.of("Content-Range", "0-9"), bytes("short"));
        assertEquals(400, declared.status());
        assertEquals("BLOB_UPLOAD_INVALID", declared.errorCode());
        Resp streamed = call("PATCH", upload, null, Map.of("Content-Range", "0-9"),
                new ByteArrayInputStream(bytes("short")), -1);
        assertEquals(400, streamed.status());
        assertEquals("0-0", call("GET", upload).header("Range"), "the refused chunk was not kept");
    }

    @Test
    void uploadSessionsAreStrictUuidsOwnedByTheirCreator() {
        for (String id : List.of("..", "not-a-uuid", "00000000-0000-0000-0000-000000000000",
                "6f1c3b5e-7d8a-4b2c-9e1f-0a1b2c3d4e5f")) {
            Resp r = call("GET", "v2/app/blobs/uploads/" + id);
            assertEquals(404, r.status(), id);
            assertEquals("BLOB_UPLOAD_UNKNOWN", r.errorCode(), id);
        }
        String id = uploadId(call("POST", "v2/app/blobs/uploads/"));
        user = "alice";
        admin = false;
        assertEquals(404, call("GET", "v2/app/blobs/uploads/" + id).status(), "another user's session");
        assertEquals(404, call("PATCH", "v2/app/blobs/uploads/" + id, null, Map.of(), bytes("x")).status());
        assertEquals(404, call("DELETE", "v2/app/blobs/uploads/" + id).status());
        user = "admin";
        admin = true;
        assertEquals(204, call("DELETE", "v2/app/blobs/uploads/" + id).status());
        assertEquals(404, call("GET", "v2/app/blobs/uploads/" + id).status());
        assertEquals(List.of(), store.paths(REPO, "app/_uploads"));
    }

    @Test
    void concurrentPushOfTheSameBlobResolvesToTheVerifiedBlob() {
        byte[] content = bytes("pushed twice");
        String blob = "app/_blobs/sha256/" + hex(content);
        // A caller without delete permission cannot overwrite; the other push lands first, between the check and
        // the copy. Both pushes succeed and the blob holds the verified content.
        store.denyOverwriteAndDelete = true;
        store.beforeNextCopy = () -> store.put(REPO, blob, content);
        Resp r = call("POST", "v2/app/blobs/uploads/", "digest=" + digest(content), Map.of(), content);
        assertEquals(201, r.status(), r.text());
        assertArrayEquals(content, store.get(REPO, blob));
        // The same with a copy refused: the blob is re-streamed instead.
        byte[] other = bytes("copy refused");
        store.denyOverwriteAndDelete = false;
        store.denyCopy = true;
        assertEquals(201, call("POST", "v2/app/blobs/uploads/", "digest=" + digest(other), Map.of(), other).status());
        assertArrayEquals(other, store.get(REPO, "app/_blobs/sha256/" + hex(other)));
    }

    @Test
    void contentAtADigestPathThatDoesNotMatchItsDigestIsNeitherServedNorTrusted() {
        byte[] content = bytes("genuine");
        String blob = "app/_blobs/sha256/" + hex(content);
        store.put(REPO, blob, bytes("planted"));
        assertEquals(404, call("GET", "v2/app/blobs/" + digest(content)).status());
        assertEquals(400, putManifest("app", "v1", OCI_MANIFEST, imageManifest(OCI_MANIFEST, content)).status(),
                "a manifest cannot reference the planted blob");
        pushBlob("app", content);
        assertArrayEquals(content, call("GET", "v2/app/blobs/" + digest(content)).body());
    }

    @Test
    void rangeRequestsServeExactSlices() {
        byte[] content = bytes("0123456789");
        String d = pushBlob("app", content);
        Resp slice = call("GET", "v2/app/blobs/" + d, null, Map.of("Range", "bytes=2-5"), (byte[]) null);
        assertEquals(206, slice.status());
        assertEquals("bytes 2-5/10", slice.header("Content-Range"));
        assertArrayEquals(bytes("2345"), slice.body());
        Resp suffix = call("GET", "v2/app/blobs/" + d, null, Map.of("Range", "bytes=-3"), (byte[]) null);
        assertArrayEquals(bytes("789"), suffix.body());
        Resp open = call("GET", "v2/app/blobs/" + d, null, Map.of("Range", "bytes=7-"), (byte[]) null);
        assertArrayEquals(bytes("789"), open.body());
        Resp beyond = call("GET", "v2/app/blobs/" + d, null, Map.of("Range", "bytes=10-"), (byte[]) null);
        assertEquals(416, beyond.status());
        assertEquals("bytes */10", beyond.header("Content-Range"));
    }

    // --- O4: mounts -------------------------------------------------------------------------------------------------

    @Test
    void mountFromANameInTheSameRepositoryCreatesTheBlob() {
        byte[] content = bytes("shared layer");
        String d = pushBlob("base", content);
        Resp r = call("POST", "v2/app/blobs/uploads/", "mount=" + d + "&from=" + REPO + "/base", Map.of(),
                (byte[]) null);
        assertEquals(201, r.status(), r.text());
        assertEquals(BASE + "/v2/" + REPO + "/app/blobs/" + d, r.header("Location"));
        assertArrayEquals(content, call("GET", "v2/app/blobs/" + d).body());
    }

    @Test
    void mountFromAnotherRepositoryNeverReadsItAndFallsBackToAnUploadSession() {
        byte[] content = bytes("secret layer in another repository");
        store.put("other-repo", "base/_blobs/sha256/" + hex(content), content);
        for (String from : List.of("other-repo/base", "base", REPO, "../other-repo/base",
                REPO + "/../other-repo/base")) {
            Resp r = call("POST", "v2/app/blobs/uploads/", "mount=" + digest(content) + "&from=" + from, Map.of(),
                    (byte[]) null);
            assertEquals(202, r.status(), from);
            assertTrue(r.header("Location").startsWith(BASE + "/v2/" + REPO + "/app/blobs/uploads/"), from);
        }
        Resp noFrom = call("POST", "v2/app/blobs/uploads/", "mount=" + digest(content), Map.of(), (byte[]) null);
        assertEquals(202, noFrom.status());
        assertFalse(store.has(REPO, "app/_blobs"));
        // nothingLeaksOutOfTheRepository() asserts that other-repo was never touched.
    }

    // --- path-scoped read permissions ------------------------------------------------------------------------------

    private static Predicate<RepoPath> denyUnder(String repo, String prefix) {
        return path -> !(path.getRepoKey().equals(repo)
                && (path.getPath().equals(prefix) || path.getPath().startsWith(prefix + "/")));
    }

    @Test
    void blobsAndManifestsUnderAnUnreadableNameAreUnknown() {
        byte[] content = bytes("team b layer");
        String d = pushBlob("team-b/app", content);
        pushImage("team-b/app", "v1");
        canRead = denyUnder(REPO, "team-b");
        assertEquals("BLOB_UNKNOWN", call("GET", "v2/team-b/app/blobs/" + d).errorCode());
        assertEquals(404, call("HEAD", "v2/team-b/app/blobs/" + d).status());
        assertEquals(404, call("GET", "v2/team-b/app/manifests/v1").status());
        assertEquals(404, call("GET", "v2/team-b/app/tags/list").status());
    }

    @Test
    void mountFromAnUnreadableNameInTheSameRepositoryFallsBackToAnUploadSession() {
        byte[] content = bytes("team b secret layer");
        String d = pushBlob("team-b/base", content);
        canRead = denyUnder(REPO, "team-b");
        Resp r = call("POST", "v2/team-a/app/blobs/uploads/", "mount=" + d + "&from=" + REPO + "/team-b/base",
                Map.of(), (byte[]) null);
        assertEquals(202, r.status(), r.text());
        assertFalse(store.has(REPO, "team-a/app/_blobs"));
    }

    // --- names and paths --------------------------------------------------------------------------------------------

    @Test
    void invalidNamesAndPathTraversalAreRefusedBeforeAnyStorageAccess() {
        String d = digest(bytes("x"));
        for (String name : List.of("..", "../other", "a/../../other-repo/b", "..%2f..%2fother", "a//b", "A",
                "_blobs", "a/_blobs/sha256", "a/_tags", "a/.", "a-", "-a", "a" + "/b".repeat(200))) {
            for (String path : List.of("v2/" + name + "/blobs/" + d, "v2/" + name + "/manifests/latest",
                    "v2/" + name + "/tags/list", "v2/" + name + "/blobs/uploads/")) {
                String method = path.endsWith("uploads/") ? "POST" : "GET";
                Resp r = call(method, path);
                assertEquals(400, r.status(), path);
                assertEquals("NAME_INVALID", r.errorCode(), path);
            }
        }
        assertTrue(store.touchedRepoKeys().isEmpty(), "no storage access for invalid names");
        assertEquals(404, call("GET", "v2/app/unknown/endpoint").status());
        assertEquals(404, call("GET", "not-v2/app/tags/list").status());
        assertEquals(405, call("PUT", "v2/app/blobs/" + d).status());
        assertEquals(405, call("GET", "v2/app/blobs/uploads/").status());
    }

    @Test
    void directHttpDeploysIntoTheRepositoryAreRefusedButInternalDeploysPass() {
        RepoPath path = RepoPathFactory.create(REPO, "app/_blobs/sha256/" + "a".repeat(64));
        UploadDecision.Reject reject = assertInstanceOf(UploadDecision.Reject.class,
                type.upload().orElseThrow().decide(mock(Request.class), path));
        assertEquals(403, reject.status());
        assertInstanceOf(UploadDecision.Accept.class, type.upload().orElseThrow()
                .decide(new ArtifactoryDeployRequestBuilder(path).inputStream(InputStream.nullInputStream()).build(),
                        path));
    }

    // --- O5/O6: manifests -------------------------------------------------------------------------------------------

    @Test
    void ociManifestByTagAndDigestWithItsMediaTypeAndDigestHeaders() {
        byte[] manifest = pushImage("app", "one");
        Resp put = putManifest("app", "1.0", OCI_MANIFEST, manifest);
        assertEquals(201, put.status(), put.text());
        assertEquals(BASE + "/v2/" + REPO + "/app/manifests/" + digest(manifest), put.header("Location"));
        assertEquals(digest(manifest), put.header("Docker-Content-Digest"));
        assertEquals(digest(manifest), new String(store.get(REPO, "app/_tags/1.0"), StandardCharsets.UTF_8));

        for (String ref : List.of("1.0", digest(manifest))) {
            Resp get = call("GET", "v2/app/manifests/" + ref);
            assertEquals(200, get.status(), ref);
            assertArrayEquals(manifest, get.body());
            assertEquals(OCI_MANIFEST, get.header("Content-Type"));
            assertEquals(digest(manifest), get.header("Docker-Content-Digest"));
            Resp head = call("HEAD", "v2/app/manifests/" + ref);
            assertEquals(200, head.status());
            assertEquals(String.valueOf(manifest.length), head.header("Content-Length"));
            assertEquals(OCI_MANIFEST, head.header("Content-Type"));
        }
        Resp byDigest = putManifest("app", digest(manifest), OCI_MANIFEST, manifest);
        assertEquals(201, byDigest.status(), "re-pushing by digest is idempotent");
        assertEquals(404, call("GET", "v2/app/manifests/2.0").status());
        assertEquals(404, call("HEAD", "v2/app/manifests/.INVALID_MANIFEST_NAME").status());
        assertEquals("DIGEST_INVALID", call("GET", "v2/app/manifests/sha256:totallywrong").errorCode());
    }

    @Test
    void manifestWithoutMediaTypeIsServedWithTheContentTypeItWasPushedWith() {
        byte[] config = bytes("{}");
        pushBlob("app", config);
        for (String mediaType : List.of(OCI_MANIFEST, DOCKER_MANIFEST)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("schemaVersion", 2);
            m.put("config", descriptor("application/vnd.oci.image.config.v1+json", config));
            m.put("layers", List.of());
            m.put("note", mediaType);
            byte[] manifest = toJson(m);
            assertEquals(201, putManifest("app", "t", mediaType + "; charset=utf-8", manifest).status());
            assertEquals(mediaType, call("GET", "v2/app/manifests/t").header("Content-Type"));
        }
    }

    @Test
    void dockerSchema2ManifestAndManifestListAndOciIndex() {
        byte[] amd64 = pushImage("multi", "amd64");
        byte[] arm64 = pushImage("multi", "arm64");
        byte[] docker = imageManifest(DOCKER_MANIFEST, bytes("{\"seed\":\"amd64\"}"), bytes("layer of amd64"));
        assertEquals(201, putManifest("multi", digest(amd64), OCI_MANIFEST, amd64).status());
        assertEquals(201, putManifest("multi", digest(arm64), OCI_MANIFEST, arm64).status());
        assertEquals(201, putManifest("multi", "docker", DOCKER_MANIFEST, docker).status());
        assertEquals(DOCKER_MANIFEST, call("GET", "v2/multi/manifests/docker").header("Content-Type"));

        for (String mediaType : List.of(OCI_INDEX, DOCKER_LIST)) {
            byte[] list = index(mediaType, amd64, arm64);
            Resp put = putManifest("multi", "latest", mediaType, list);
            assertEquals(201, put.status(), put.text());
            Resp get = call("GET", "v2/multi/manifests/latest");
            assertEquals(mediaType, get.header("Content-Type"));
            assertArrayEquals(list, get.body());
        }
        byte[] dangling = index(OCI_INDEX, amd64, bytes("{\"not\":\"pushed\"}"));
        Resp refused = putManifest("multi", "dangling", OCI_INDEX, dangling);
        assertEquals(400, refused.status());
        assertEquals("MANIFEST_BLOB_UNKNOWN", refused.errorCode());
        assertFalse(store.has(REPO, "multi/_tags/dangling"));
        assertFalse(store.has(REPO, "multi/_manifests/sha256/" + hex(dangling)));
    }

    @Test
    void manifestReferencingAMissingBlobOrABlobOfAnotherNameIsRefused() {
        byte[] config = bytes("{\"c\":1}");
        byte[] layer = bytes("layer");
        pushBlob("app", config);
        pushBlob("elsewhere", layer);
        byte[] manifest = imageManifest(OCI_MANIFEST, config, layer);
        Resp r = putManifest("app", "v1", OCI_MANIFEST, manifest);
        assertEquals(400, r.status());
        assertEquals("MANIFEST_BLOB_UNKNOWN", r.errorCode());
        assertEquals(List.of(), store.paths(REPO, "app/_manifests"));
        assertEquals(List.of(), store.paths(REPO, "app/_tags"));
    }

    @Test
    void invalidManifestsAreRefused() {
        byte[] manifest = pushImage("app", "x");
        assertEquals("DIGEST_INVALID",
                putManifest("app", digest(bytes("other")), OCI_MANIFEST, manifest).errorCode());
        assertEquals("MANIFEST_INVALID", putManifest("app", "v1", OCI_MANIFEST, bytes("blablabla")).errorCode());
        assertEquals("MANIFEST_INVALID", putManifest("app", "v1", OCI_INDEX, manifest).errorCode(),
                "Content-Type must match the declared mediaType");
        assertEquals("MANIFEST_INVALID", putManifest("app", "v1", "application/json",
                bytes("{\"schemaVersion\":2}")).errorCode());
        assertEquals("MANIFEST_INVALID", putManifest("app", "v1", OCI_MANIFEST,
                bytes("{\"schemaVersion\":1,\"mediaType\":\"" + OCI_MANIFEST + "\"}")).errorCode());
        assertEquals("TAG_INVALID", putManifest("app", ".hidden", OCI_MANIFEST, manifest).errorCode());
        assertEquals("TAG_INVALID", putManifest("app", "t".repeat(129), OCI_MANIFEST, manifest).errorCode());
        assertEquals(List.of(), store.paths(REPO, "app/_manifests"));
    }

    @Test
    void oversizeManifestIsRefusedWith413WithoutBufferingIt() {
        int max = 4 * 1024 * 1024;
        Resp declared = call("PUT", "v2/app/manifests/big", null, Map.of("Content-Type", OCI_MANIFEST),
                new ByteArrayInputStream(new byte[0]), max + 1L);
        assertEquals(413, declared.status());
        assertEquals("SIZE_INVALID", declared.errorCode());
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return ' ';
            }
        };
        Resp streamed = call("PUT", "v2/app/manifests/big", null, Map.of("Content-Type", OCI_MANIFEST), endless, -1);
        assertEquals(413, streamed.status());
        assertEquals(List.of(), store.paths(REPO, "app"));
    }

    @Test
    void retaggingNeedsOverwritePermissionAndReportsDenied() {
        byte[] first = pushImage("app", "first");
        byte[] second = pushImage("app", "second");
        assertEquals(201, putManifest("app", "latest", OCI_MANIFEST, first).status());
        store.denyOverwriteAndDelete = true;
        assertEquals(201, putManifest("app", "latest", OCI_MANIFEST, first).status(), "same tag, same digest");
        Resp retag = putManifest("app", "latest", OCI_MANIFEST, second);
        assertEquals(403, retag.status());
        assertEquals("DENIED", retag.errorCode());
    }

    // --- O7: tags ---------------------------------------------------------------------------------------------------

    @Test
    void tagListIsSortedAndPaginatedWithALinkHeader() {
        byte[] manifest = pushImage("app", "tags");
        for (String tag : List.of("e", "b", "a", "d", "c")) {
            assertEquals(201, putManifest("app", tag, OCI_MANIFEST, manifest).status());
        }
        Resp all = call("GET", "v2/app/tags/list");
        assertEquals(200, all.status());
        assertEquals("application/json", all.header("Content-Type"));
        assertEquals(Map.of("name", REPO + "/app", "tags", List.of("a", "b", "c", "d", "e")), all.json());
        assertNull(all.header("Link"));

        Resp page1 = call("GET", "v2/app/tags/list", "n=2", Map.of(), (byte[]) null);
        assertEquals(List.of("a", "b"), ((Map<?, ?>) page1.json()).get("tags"));
        assertEquals("<" + BASE + "/v2/" + REPO + "/app/tags/list?n=2&last=b>; rel=\"next\"", page1.header("Link"));
        Resp page2 = call("GET", "v2/app/tags/list", "n=2&last=b", Map.of(), (byte[]) null);
        assertEquals(List.of("c", "d"), ((Map<?, ?>) page2.json()).get("tags"));
        Resp page3 = call("GET", "v2/app/tags/list", "n=2&last=d", Map.of(), (byte[]) null);
        assertEquals(List.of("e"), ((Map<?, ?>) page3.json()).get("tags"));
        assertNull(page3.header("Link"));
        assertEquals(List.of(), ((Map<?, ?>) call("GET", "v2/app/tags/list", "n=0", Map.of(), (byte[]) null).json())
                .get("tags"));
        assertEquals(400, call("GET", "v2/app/tags/list", "n=-1", Map.of(), (byte[]) null).status());
        Resp unknown = call("GET", "v2/nosuch/tags/list");
        assertEquals(404, unknown.status());
        assertEquals("NAME_UNKNOWN", unknown.errorCode());
    }

    // --- O8: deletes ------------------------------------------------------------------------------------------------

    @Test
    void deleteByTagThenByDigestRemovesTagsAndAnswers404Afterwards() {
        byte[] manifest = pushImage("app", "del");
        putManifest("app", "keep", OCI_MANIFEST, manifest);
        putManifest("app", "drop", OCI_MANIFEST, manifest);
        assertEquals(202, call("DELETE", "v2/app/manifests/drop").status());
        assertEquals(404, call("GET", "v2/app/manifests/drop").status());
        assertEquals(200, call("GET", "v2/app/manifests/keep").status());
        assertEquals(404, call("DELETE", "v2/app/manifests/drop").status());

        assertEquals(202, call("DELETE", "v2/app/manifests/" + digest(manifest)).status());
        assertEquals(404, call("GET", "v2/app/manifests/" + digest(manifest)).status());
        assertEquals(404, call("GET", "v2/app/manifests/keep").status());
        assertEquals(List.of(), ((Map<?, ?>) call("GET", "v2/app/tags/list").json()).get("tags"),
                "tags of the deleted manifest are removed");
        assertEquals(List.of(), store.paths(REPO, "app/_manifests"));
        assertEquals(404, call("DELETE", "v2/app/manifests/" + digest(manifest)).status());
    }

    @Test
    void deleteBlobAnswers202ThenThe404() {
        String d = pushBlob("app", bytes("to delete"));
        assertEquals(202, call("DELETE", "v2/app/blobs/" + d).status());
        assertEquals(404, call("GET", "v2/app/blobs/" + d).status());
        Resp again = call("DELETE", "v2/app/blobs/" + d);
        assertEquals(404, again.status());
        assertEquals("BLOB_UNKNOWN", again.errorCode());
    }

    // --- referrers --------------------------------------------------------------------------------------------------

    @Test
    void referrersListManifestsWithASubjectAndFilterByArtifactType() {
        byte[] subject = pushImage("app", "subject");
        assertEquals(201, putManifest("app", "s", OCI_MANIFEST, subject).status());
        byte[] empty = bytes("{}");
        pushBlob("app", empty);
        byte[] signature = bytes("signature");
        pushBlob("app", signature);
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("schemaVersion", 2);
        artifact.put("mediaType", OCI_MANIFEST);
        artifact.put("artifactType", "application/vnd.example.sig");
        artifact.put("config", descriptor("application/vnd.oci.empty.v1+json", empty));
        artifact.put("layers", List.of(descriptor("application/octet-stream", signature)));
        artifact.put("subject", descriptor(OCI_MANIFEST, subject));
        artifact.put("annotations", Map.of("k", "v"));
        byte[] referrer = toJson(artifact);
        Resp put = putManifest("app", digest(referrer), OCI_MANIFEST, referrer);
        assertEquals(201, put.status(), put.text());
        assertEquals(digest(subject), put.header("OCI-Subject"));

        Resp list = call("GET", "v2/app/referrers/" + digest(subject));
        assertEquals(200, list.status());
        assertEquals(OCI_INDEX, list.header("Content-Type"));
        List<?> manifests = (List<?>) ((Map<?, ?>) list.json()).get("manifests");
        assertEquals(1, manifests.size());
        Map<?, ?> entry = (Map<?, ?>) manifests.get(0);
        assertEquals(digest(referrer), entry.get("digest"));
        assertEquals(referrer.length, entry.get("size"));
        assertEquals("application/vnd.example.sig", entry.get("artifactType"));
        assertEquals(Map.of("k", "v"), entry.get("annotations"));

        Resp filtered = call("GET", "v2/app/referrers/" + digest(subject), "artifactType=other/type", Map.of(),
                (byte[]) null);
        assertEquals("artifactType", filtered.header("OCI-Filters-Applied"));
        assertEquals(List.of(), ((Map<?, ?>) filtered.json()).get("manifests"));

        Resp none = call("GET", "v2/app/referrers/" + digest(bytes("nothing refers to me")));
        assertEquals(200, none.status());
        assertEquals(List.of(), ((Map<?, ?>) none.json()).get("manifests"));

        assertEquals(202, call("DELETE", "v2/app/manifests/" + digest(referrer)).status());
        assertEquals(List.of(), ((Map<?, ?>) call("GET", "v2/app/referrers/" + digest(subject)).json())
                .get("manifests"));
    }
}
