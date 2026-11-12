/*
 * OCI Distribution Spec v1.1 registry for the Artifactory OSS fork, written purely as a plugin package type.
 *
 * Package type 'oci-registry': a LOCAL Generic repository whose packageTypeConfig carries
 * pluginPackageType=oci-registry. Clients address it through the registry root with the repository key as the
 * first path segment (Artifactory's repository-path-prefix method):
 *     <host>/v2/<repoKey>/<name>/...        image references look like <host>/<repoKey>/<name>:<tag>
 * or directly as <artifactory>/api/docker/<repoKey>/v2/<name>/... . The Java shell has already authenticated the
 * caller (HTTP Basic) and checked the method's permission on the repository root before this code runs: GET/HEAD
 * read, PUT/POST/PATCH deploy, DELETE delete. Writes below additionally get Artifactory's per-path checks
 * (overwriting a file needs delete permission). GET /v2/ (the ping) is answered by the Java shell.
 *
 * Endpoints (paths relative to /v2/<repoKey>/):
 *   GET|HEAD   <name>/blobs/<digest>                   blob, with single-range Range support
 *   DELETE     <name>/blobs/<digest>                   202
 *   POST       <name>/blobs/uploads/                   new session (202), ?digest= monolithic (201),
 *                                                      ?mount=<digest>&from=<name> mount within this repository
 *   GET        <name>/blobs/uploads/<uuid>             upload status (204)
 *   PATCH      <name>/blobs/uploads/<uuid>             append a chunk (Content-Range checked, 416 out of order)
 *   PUT        <name>/blobs/uploads/<uuid>?digest=     finish, with an optional final chunk (201)
 *   DELETE     <name>/blobs/uploads/<uuid>             cancel (204)
 *   GET|HEAD|PUT|DELETE <name>/manifests/<tag|digest> OCI image manifest and index, Docker schema 2 manifest and
 *                                                      manifest list
 *   GET        <name>/tags/list?n=&last=               lexically sorted, Link header for the next page
 *   GET        <name>/referrers/<digest>?artifactType= referrers index (OCI 1.1)
 *
 * Storage layout inside the repository (every path is under the request's repository and is built only from
 * validated components; reserved folders start with '_', which no name component can):
 *   <name>/_blobs/sha256/<hex>                        blob content
 *   <name>/_manifests/sha256/<hex>                    manifest bytes exactly as pushed
 *   <name>/_manifests/sha256/<hex>.mediatype          the manifest's media type (text)
 *   <name>/_tags/<tag>                                text "sha256:<hex>" naming the tagged manifest
 *   <name>/_referrers/sha256/<subject hex>/<hex>      JSON descriptor of a manifest whose subject is <subject hex>
 *   <name>/_uploads/<uuid>/owner                      the user that opened the upload session
 *   <name>/_uploads/<uuid>/p-<19-digit offset>        one stored chunk, named by its start offset
 *   <name>/_uploads/<uuid>/assembled                  the concatenated chunks while a multi-chunk upload finishes
 *
 * Integrity: Artifactory computes the SHA-256 of every stored file. A blob or manifest counts as present only when
 * the stored SHA-256 equals the hex in its path, so content stored at a digest path by other means (the UI
 * deployer) is never served or trusted. Uploaded content is first stored under _uploads/, verified against the
 * client's digest there, and only then copied (or, when a copy is refused, re-streamed) to its digest path; a
 * mismatch deletes the upload and answers 400 DIGEST_INVALID. Concurrent pushes of the same blob each verify their
 * own upload and then promote it; whichever copy lands, the path ends up holding the verified content, and both
 * requests re-check the path before answering. Direct HTTP deploys into the repository are refused by the upload
 * handler; only the plugin's own internal deploys (and Artifactory UI deploys, covered by the check above) store.
 *
 * Memory: blobs are streamed in both directions and never buffered; manifests are read with a 4 MiB cap (413
 * above it). Listings are bounded (tags, referrers, upload chunks).
 *
 * Not implemented (see plugins/oci/README.md): garbage collection of unreferenced blobs and of abandoned upload
 * sessions, remote and virtual repositories, token authentication (Basic only), the catalog endpoint, automatic
 * mounting without 'from', and mounting from another Artifactory repository (answered with a normal upload
 * session, as the spec allows, because it would bypass that repository's read permission).
 *
 * Copyright (C) 2026 the fork authors. AGPL-3.0-or-later.
 */

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.transform.Field
import org.apache.commons.io.input.BoundedInputStream
import org.artifactory.addon.plugin.pkgtype.PluginApiMount
import org.artifactory.repo.service.deploy.ArtifactoryDeployRequest

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.regex.Matcher
import java.util.regex.Pattern

@Field final String TYPE_NAME = 'oci-registry'

/** Repository name, from the distribution spec. */
@Field final Pattern NAME = Pattern.compile(
        '[a-z0-9]+((\\.|_|__|-+)[a-z0-9]+)*(\\/[a-z0-9]+((\\.|_|__|-+)[a-z0-9]+)*)*')
@Field final int MAX_NAME_LENGTH = 255
@Field final Pattern TAG = Pattern.compile('[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}')
@Field final Pattern SHA256_DIGEST = Pattern.compile('sha256:([a-f0-9]{64})')
/** Any well-formed digest (algorithm:encoded), used to tell an unsupported algorithm from garbage. */
@Field final Pattern ANY_DIGEST = Pattern.compile('[a-z0-9]+([+._-][a-z0-9]+)*:[a-zA-Z0-9=_-]{1,256}')
@Field final Pattern SESSION_ID = Pattern.compile(
        '[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}')
@Field final Pattern PART = Pattern.compile('p-([0-9]{19})')
@Field final Pattern CONTENT_RANGE = Pattern.compile('(?:bytes[ =])?([0-9]{1,18})-([0-9]{1,18})(?:/(?:[0-9]{1,18}|\\*))?')
@Field final Pattern RANGE = Pattern.compile('bytes=([0-9]{0,18})-([0-9]{0,18})')

@Field final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024
@Field final int MAX_DESCRIPTORS = 10000
@Field final int MAX_PARTS = 10000
@Field final int MAX_TAGS = 10000
@Field final int MAX_REFERRERS = 1000
@Field final int MAX_POINTER_BYTES = 4096

@Field final String OCI_MANIFEST = 'application/vnd.oci.image.manifest.v1+json'
@Field final String OCI_INDEX = 'application/vnd.oci.image.index.v1+json'
@Field final String DOCKER_MANIFEST = 'application/vnd.docker.distribution.manifest.v2+json'
@Field final String DOCKER_LIST = 'application/vnd.docker.distribution.manifest.list.v2+json'
@Field final Set<String> IMAGE_TYPES = [OCI_MANIFEST, DOCKER_MANIFEST] as Set
@Field final Set<String> INDEX_TYPES = [OCI_INDEX, DOCKER_LIST] as Set
@Field final String FOREIGN_LAYER = 'application/vnd.docker.image.rootfs.foreign.diff.tar.gzip'

/**
 * JSON via Jackson, which Artifactory ships (groovy-json is not on its classpath). Manifests are untrusted input:
 * trailing content after the document is refused, and Jackson's default read constraints bound nesting and sizes.
 */
@Field final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

String toJson(Object document) {
    JSON.writeValueAsString(document)
}

// ---------------------------------------------------------------------------------------------------------------
// Responses
// ---------------------------------------------------------------------------------------------------------------

/** @return an error response in the spec's JSON format */
Map error(int status, String code, String message, Map headers = [:]) {
    String json = toJson([errors: [[code: code, message: message, detail: [:]]]])
    [status: status, headers: ['Content-Type': 'application/json'] + headers, body: json]
}

Map notAllowed(String allow) {
    error(405, 'UNSUPPORTED', 'method not allowed here', [Allow: allow])
}

Map json(int status, String contentType, Object document, Map headers = [:]) {
    [status: status, headers: ['Content-Type': contentType] + headers, body: toJson(document)]
}

/** @return the response for a failed write as Artifactory reported it */
Map writeFailed(def status, String what) {
    int code = status?.statusCode ?: 500
    String message = "${what}: ${status?.statusMsg ?: 'failed'}".toString()
    if (code == 401 || code == 403) {
        return error(403, 'DENIED', message)
    }
    log.warn('oci-registry: {} ({})', message, code)
    error(500, 'UNKNOWN', "${what} failed".toString())
}

// ---------------------------------------------------------------------------------------------------------------
// Validation and paths
// ---------------------------------------------------------------------------------------------------------------

boolean validName(String name) {
    name != null && name.length() <= MAX_NAME_LENGTH && NAME.matcher(name).matches()
}

boolean validTag(String tag) {
    tag != null && TAG.matcher(tag).matches()
}

/** @return the hex of a sha256 digest, or the error response for an invalid or unsupported one */
Object parseDigest(String digest) {
    Matcher m = SHA256_DIGEST.matcher(digest ?: '')
    if (m.matches()) {
        return m.group(1)
    }
    if (otherAlgorithm(digest)) {
        return error(400, 'UNSUPPORTED', 'only sha256 digests are supported')
    }
    error(400, 'DIGEST_INVALID', 'invalid digest')
}

/** @return whether {@code digest} is well formed but uses an algorithm other than sha256 */
boolean otherAlgorithm(String digest) {
    digest != null && digest.length() <= 300 && !digest.startsWith('sha256:') && ANY_DIGEST.matcher(digest).matches()
}

RepoPath rp(PluginApiRequest req, String path) {
    RepoPathFactory.create(req.repoKey(), path)
}

String blobPath(String name, String hex) { "${name}/_blobs/sha256/${hex}".toString() }

String manifestPath(String name, String hex) { "${name}/_manifests/sha256/${hex}".toString() }

String mediaTypePath(String name, String hex) { "${manifestPath(name, hex)}.mediatype".toString() }

String tagsDir(String name) { "${name}/_tags".toString() }

String tagPath(String name, String tag) { "${tagsDir(name)}/${tag}".toString() }

String referrersDir(String name, String subjectHex) { "${name}/_referrers/sha256/${subjectHex}".toString() }

String uploadDir(String name, String id) { "${name}/_uploads/${id}".toString() }

/** The name as the client sees it: through the registry root the repository key is its first segment. */
String clientName(PluginApiRequest req, String name) {
    req.mount() == PluginApiMount.V2 ? "${req.repoKey()}/${name}".toString() : name
}

/**
 * Resolves a mount's {@code from} to a name in the request's own repository, or null. Through the registry root the
 * client's names carry the repository key as their first segment, so {@code from} must start with it; anything
 * else names another Artifactory repository (or nothing) and is never followed.
 */
String sameRepoName(PluginApiRequest req, String from) {
    if (from == null) {
        return null
    }
    String name = from
    if (req.mount() == PluginApiMount.V2) {
        String prefix = req.repoKey() + '/'
        if (!from.startsWith(prefix)) {
            return null
        }
        name = from.substring(prefix.length())
    }
    validName(name) ? name : null
}

/** @return the stored file at {@code path} when its stored SHA-256 is {@code hex}, otherwise null */
def verified(PluginApiRequest req, String path, String hex) {
    def file = repositories.findFile(rp(req, path)).orElse(null)
    file != null && file.sha256() == hex ? file : null
}

/** @return the small text file at {@code path}, trimmed, or null when missing or too large */
String readPointer(PluginApiRequest req, String path) {
    def file = repositories.findFile(rp(req, path)).orElse(null)
    if (file == null || file.size() > MAX_POINTER_BYTES) {
        return null
    }
    repositories.getStringContent(rp(req, path))?.trim()
}

String sha256Hex(byte[] bytes) {
    MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
}

String mediaTypeOf(String contentType) {
    contentType == null ? null : contentType.split(';', 2)[0].trim().toLowerCase(Locale.ROOT)
}

// ---------------------------------------------------------------------------------------------------------------
// Routing
// ---------------------------------------------------------------------------------------------------------------

@Field final List<List> ROUTES = [
        [Pattern.compile('v2/(.+)/blobs/uploads/?'), 'uploads'],
        [Pattern.compile('v2/(.+)/blobs/uploads/([^/]+)'), 'upload'],
        [Pattern.compile('v2/(.+)/blobs/([^/]+)'), 'blob'],
        [Pattern.compile('v2/(.+)/manifests/([^/]+)'), 'manifest'],
        [Pattern.compile('v2/(.+)/tags/list'), 'tags'],
        [Pattern.compile('v2/(.+)/referrers/([^/]+)'), 'referrers'],
]

Map route(String path) {
    for (List entry : ROUTES) {
        Matcher m = ((Pattern) entry[0]).matcher(path)
        if (m.matches()) {
            return [op: entry[1], name: m.group(1), ref: m.groupCount() > 1 ? m.group(2) : null]
        }
    }
    null
}

Map handle(PluginApiRequest req) {
    String path = req.path()
    if (path == 'v2' || path == 'v2/') {
        return [status: 200, headers: ['Content-Type': 'application/json'], body: '{}']
    }
    Map r = route(path)
    if (r == null) {
        return error(404, 'NAME_UNKNOWN', 'not a registry API path')
    }
    String name = r.name
    if (!validName(name)) {
        return error(400, 'NAME_INVALID', 'invalid repository name')
    }
    String method = req.method()
    switch (r.op) {
        case 'uploads':
            return method == 'POST' ? startUpload(req, name) : notAllowed('POST')
        case 'upload':
            switch (method) {
                case 'GET': case 'HEAD': return uploadStatus(req, name, r.ref)
                case 'PATCH': return patchUpload(req, name, r.ref)
                case 'PUT': return finishUpload(req, name, r.ref)
                case 'DELETE': return cancelUpload(req, name, r.ref)
                default: return notAllowed('GET, HEAD, PATCH, PUT, DELETE')
            }
        case 'blob':
            switch (method) {
                case 'GET': case 'HEAD': return getBlob(req, name, r.ref)
                case 'DELETE': return deleteBlob(req, name, r.ref)
                default: return notAllowed('GET, HEAD, DELETE')
            }
        case 'manifest':
            switch (method) {
                case 'GET': case 'HEAD': return getManifest(req, name, r.ref)
                case 'PUT': return putManifest(req, name, r.ref)
                case 'DELETE': return deleteManifest(req, name, r.ref)
                default: return notAllowed('GET, HEAD, PUT, DELETE')
            }
        case 'tags':
            return method in ['GET', 'HEAD'] ? listTags(req, name) : notAllowed('GET, HEAD')
        case 'referrers':
            return method in ['GET', 'HEAD'] ? referrers(req, name, r.ref) : notAllowed('GET, HEAD')
    }
    error(404, 'NAME_UNKNOWN', 'not a registry API path')
}

// ---------------------------------------------------------------------------------------------------------------
// Blobs
// ---------------------------------------------------------------------------------------------------------------

Map getBlob(PluginApiRequest req, String name, String digest) {
    Object hex = parseDigest(digest)
    if (hex instanceof Map) {
        return hex
    }
    String path = blobPath(name, hex)
    def file = verified(req, path, hex)
    if (file == null) {
        return error(404, 'BLOB_UNKNOWN', 'blob unknown to registry')
    }
    long size = file.size()
    Map headers = ['Content-Type'         : 'application/octet-stream',
                   'Docker-Content-Digest': "sha256:${hex}".toString(),
                   'ETag'                 : "\"sha256:${hex}\"".toString(),
                   'Accept-Ranges'        : 'bytes']
    long start = 0
    long end = size - 1
    int status = 200
    String range = req.header('range').orElse(null)
    if (range != null) {
        Matcher m = RANGE.matcher(range.trim())
        if (!m.matches() || (m.group(1).isEmpty() && m.group(2).isEmpty())) {
            return error(416, 'BLOB_UNKNOWN', 'unsupported range', ['Content-Range': "bytes */${size}".toString()])
        }
        try {
            if (m.group(1).isEmpty()) {
                long suffix = Long.parseLong(m.group(2))
                start = Math.max(0L, size - suffix)
            } else {
                start = Long.parseLong(m.group(1))
                if (!m.group(2).isEmpty()) {
                    end = Math.min(end, Long.parseLong(m.group(2)))
                }
            }
        } catch (NumberFormatException ignored) {
            start = size
        }
        if (start >= size || end < start) {
            return error(416, 'BLOB_UNKNOWN', 'range not satisfiable', ['Content-Range': "bytes */${size}".toString()])
        }
        status = 206
        headers['Content-Range'] = "bytes ${start}-${end}/${size}".toString()
    }
    long length = end - start + 1
    if (req.method() == 'HEAD') {
        headers['Content-Length'] = String.valueOf(length)
        return [status: status, headers: headers]
    }
    def content = repositories.openContent(rp(req, path)).orElse(null)
    if (content == null) {
        return error(404, 'BLOB_UNKNOWN', 'blob unknown to registry')
    }
    if (status == 200) {
        return [status: 200, headers: headers, body: content]
    }
    InputStream input = content.input()
    try {
        input.skipNBytes(start)
    } catch (IOException | RuntimeException e) {
        input.close()
        throw e
    }
    BoundedInputStream bounded = new BoundedInputStream(input, length)
    [status: 206, headers: headers, body: [stream: bounded, size: length]]
}

Map deleteBlob(PluginApiRequest req, String name, String digest) {
    Object hex = parseDigest(digest)
    if (hex instanceof Map) {
        return hex
    }
    RepoPath path = rp(req, blobPath(name, hex))
    if (!repositories.exists(path)) {
        return error(404, 'BLOB_UNKNOWN', 'blob unknown to registry')
    }
    def status = repositories.delete(path)
    status.error ? writeFailed(status, 'delete blob') : [status: 202]
}

/**
 * Makes {@code <name>/_blobs/sha256/<hex>} hold the content of {@code source}, whose SHA-256 the caller has verified
 * to be {@code hex}. Copies (cheap: Artifactory shares the binary), falls back to re-streaming when the copy is
 * refused, and re-checks the target afterwards so a concurrent push of the same blob cannot corrupt it.
 *
 * @return true when the target holds the verified content
 */
boolean promote(PluginApiRequest req, RepoPath source, String name, String hex) {
    String target = blobPath(name, hex)
    if (verified(req, target, hex) != null) {
        return true
    }
    repositories.copy(source, rp(req, target))
    if (verified(req, target, hex) != null) {
        return true
    }
    def content = repositories.openContent(source).orElse(null)
    if (content != null) {
        try {
            repositories.deploy(rp(req, target), content.input())
        } finally {
            content.input().close()
        }
    }
    verified(req, target, hex) != null
}

Map blobCreated(PluginApiRequest req, String name, String hex) {
    [status : 201,
     headers: [Location               : req.urlFor("v2/${name}/blobs/sha256:${hex}".toString()),
               'Docker-Content-Digest': "sha256:${hex}".toString()]]
}

// ---------------------------------------------------------------------------------------------------------------
// Uploads
// ---------------------------------------------------------------------------------------------------------------

Map uploadHeaders(PluginApiRequest req, String name, String id, long offset) {
    [Location            : req.urlFor("v2/${name}/blobs/uploads/${id}".toString()),
     'Docker-Upload-UUID': id,
     Range               : "0-${offset > 0 ? offset - 1 : 0}".toString()]
}

Map startUpload(PluginApiRequest req, String name) {
    String mount = req.queryParam('mount').orElse(null)
    if (mount != null) {
        Object hex = parseDigest(mount)
        String fromName = sameRepoName(req, req.queryParam('from').orElse(null))
        if (!(hex instanceof Map) && fromName != null) {
            def source = verified(req, blobPath(fromName, hex), hex)
            if (source != null && promote(req, source.repoPath(), name, hex)) {
                return blobCreated(req, name, hex)
            }
        }
        // Not mountable from this repository: a normal upload session, as the spec allows.
    }
    String digest = req.queryParam('digest').orElse(null)
    if (digest != null) {
        Object hex = parseDigest(digest)
        if (hex instanceof Map) {
            return hex
        }
        String id = UUID.randomUUID().toString()
        Map stored = appendChunk(req, name, id, 0L, null)
        if (stored.error != null) {
            cleanup(req, name, id)
            return stored.error
        }
        return complete(req, name, id, hex)
    }
    String id = UUID.randomUUID().toString()
    def status = repositories.deploy(rp(req, "${uploadDir(name, id)}/owner"),
            new ByteArrayInputStream(req.username().getBytes(StandardCharsets.UTF_8)))
    if (status.error) {
        return writeFailed(status, 'open upload session')
    }
    [status : 202,
     headers: uploadHeaders(req, name, id, 0L) + ['OCI-Chunk-Min-Length': '1']]
}

/** @return the start offset of the chunk file {@code child}, or null when it is not one */
Long partStart(def child) {
    Matcher m = PART.matcher(child.repoPath.name)
    if (child.isFolder() || !m.matches()) {
        return null
    }
    try {
        return Long.parseLong(m.group(1))
    } catch (NumberFormatException ignored) {
        return null
    }
}

/**
 * Loads an upload session of {@code name}: its id must be a UUID, its folder must exist and belong to the caller.
 *
 * @return [parts: [[start:, path:]...] sorted, offset: next offset] or [error: response]
 */
Map session(PluginApiRequest req, String name, String id) {
    Map unknown = [error: error(404, 'BLOB_UPLOAD_UNKNOWN', 'blob upload unknown to registry')]
    if (id == null || !SESSION_ID.matcher(id).matches()) {
        return unknown
    }
    String owner = readPointer(req, "${uploadDir(name, id)}/owner")
    if (owner == null || (owner != req.username() && !req.admin())) {
        return unknown
    }
    List<Map> parts = []
    for (def child : repositories.getChildren(rp(req, uploadDir(name, id)))) {
        Long start = partStart(child)
        if (start != null) {
            parts << [start: start, path: child.repoPath]
            if (parts.size() > MAX_PARTS) {
                return [error: error(400, 'BLOB_UPLOAD_INVALID', 'too many chunks')]
            }
        }
    }
    parts.sort { it.start }
    long offset = 0
    if (!parts.isEmpty()) {
        Map last = parts[-1]
        def file = repositories.findFile(last.path).orElse(null)
        offset = last.start + (file == null ? 0 : file.size())
    }
    [parts: parts, offset: offset]
}

/**
 * Stores the request body as the chunk starting at {@code offset}. A Content-Range must start at {@code offset}
 * (416 otherwise) and match the stored length. A request without a body stores nothing.
 *
 * @return [offset: next offset] or [error: response]
 */
Map appendChunk(PluginApiRequest req, String name, String id, long offset, Map headersOnError) {
    Map onError = headersOnError ?: [:]
    String contentRange = req.header('content-range').orElse(null)
    long expected = -1
    if (contentRange != null) {
        Matcher m = CONTENT_RANGE.matcher(contentRange.trim())
        if (!m.matches()) {
            return [error: error(416, 'BLOB_UPLOAD_INVALID', 'invalid Content-Range', onError)]
        }
        long start = Long.parseLong(m.group(1))
        long end = Long.parseLong(m.group(2))
        if (start != offset || end < start) {
            return [error: error(416, 'BLOB_UPLOAD_INVALID', 'chunk out of order', onError)]
        }
        expected = end - start + 1
        if (req.contentLength() >= 0 && req.contentLength() != expected) {
            return [error: error(400, 'BLOB_UPLOAD_INVALID', 'Content-Range does not match Content-Length', onError)]
        }
    }
    if (req.contentLength() == 0) {
        return [offset: offset]
    }
    InputStream body = req.body()
    if (req.contentLength() < 0) {
        // A body of unknown length (chunked) may be empty; look before storing an empty chunk.
        PushbackInputStream peek = new PushbackInputStream(body, 1)
        int first = peek.read()
        if (first < 0) {
            return [offset: offset]
        }
        peek.unread(first)
        body = peek
    }
    RepoPath part = rp(req, "${uploadDir(name, id)}/${String.format('p-%019d', offset)}".toString())
    def status = repositories.deploy(part, body)
    if (status.error) {
        return [error: writeFailed(status, 'store chunk')]
    }
    def stored = repositories.findFile(part).orElse(null)
    long size = stored == null ? 0 : stored.size()
    if (expected >= 0 && size != expected) {
        repositories.delete(part)
        return [error: error(400, 'BLOB_UPLOAD_INVALID', 'chunk length does not match Content-Range', onError)]
    }
    if (size == 0) {
        repositories.delete(part)
    }
    [offset: offset + size]
}

Map uploadStatus(PluginApiRequest req, String name, String id) {
    Map s = session(req, name, id)
    if (s.error != null) {
        return s.error
    }
    [status: 204, headers: uploadHeaders(req, name, id, s.offset)]
}

Map patchUpload(PluginApiRequest req, String name, String id) {
    Map s = session(req, name, id)
    if (s.error != null) {
        return s.error
    }
    if (s.parts.size() >= MAX_PARTS) {
        return error(400, 'BLOB_UPLOAD_INVALID', 'too many chunks')
    }
    Map stored = appendChunk(req, name, id, s.offset, uploadHeaders(req, name, id, s.offset))
    if (stored.error != null) {
        return stored.error
    }
    [status: 202, headers: uploadHeaders(req, name, id, stored.offset)]
}

Map finishUpload(PluginApiRequest req, String name, String id) {
    Map s = session(req, name, id)
    if (s.error != null) {
        return s.error
    }
    Object hex = parseDigest(req.queryParam('digest').orElse(null))
    if (hex instanceof Map) {
        return hex
    }
    if (s.parts.size() >= MAX_PARTS) {
        return error(400, 'BLOB_UPLOAD_INVALID', 'too many chunks')
    }
    Map stored = appendChunk(req, name, id, s.offset, uploadHeaders(req, name, id, s.offset))
    if (stored.error != null) {
        return stored.error
    }
    complete(req, name, id, hex)
}

/** Assembles the chunks of upload {@code id}, verifies them against {@code hex} and stores the blob. */
Map complete(PluginApiRequest req, String name, String id, String hex) {
    String dir = uploadDir(name, id)
    List<Map> parts = []
    long offset = 0
    for (def child : repositories.getChildren(rp(req, dir))) {
        Long start = partStart(child)
        if (start != null) {
            parts << [start: start, path: child.repoPath]
        }
    }
    parts.sort { it.start }
    for (Map part : parts) {
        def file = repositories.findFile(part.path).orElse(null)
        if (file == null || part.start != offset) {
            cleanup(req, name, id)
            return error(400, 'BLOB_UPLOAD_INVALID', 'upload chunks are not contiguous')
        }
        part.sha256 = file.sha256()
        offset += file.size()
    }
    RepoPath candidate
    String candidateSha
    if (parts.size() == 1) {
        candidate = parts[0].path
        candidateSha = parts[0].sha256
    } else {
        candidate = rp(req, "${dir}/assembled".toString())
        def repos = repositories
        // Opens each chunk only when the previous one is exhausted, so one content handle is open at a time.
        Iterator<Map> pending = parts.iterator()
        Enumeration<InputStream> streams = [
                hasMoreElements: { -> pending.hasNext() },
                nextElement    : { -> repos.openContent(pending.next().path).orElseThrow().input() }] as Enumeration
        InputStream joined = new SequenceInputStream(streams)
        def status
        try {
            status = repositories.deploy(candidate, joined)
        } finally {
            joined.close()
        }
        if (status.error) {
            cleanup(req, name, id)
            return writeFailed(status, 'assemble upload')
        }
        candidateSha = repositories.findFile(candidate).orElse(null)?.sha256()
    }
    if (candidateSha != hex) {
        cleanup(req, name, id)
        return error(400, 'DIGEST_INVALID', 'provided digest did not match uploaded content')
    }
    if (!promote(req, candidate, name, hex)) {
        cleanup(req, name, id)
        return error(500, 'UNKNOWN', 'could not store blob')
    }
    cleanup(req, name, id)
    blobCreated(req, name, hex)
}

/** Removes an upload session; best effort (a caller without delete permission leaves it behind). */
void cleanup(PluginApiRequest req, String name, String id) {
    RepoPath dir = rp(req, uploadDir(name, id))
    if (repositories.exists(dir)) {
        def status = repositories.delete(dir)
        if (status.error) {
            log.debug('oci-registry: could not remove upload {}: {}', dir, status.statusMsg)
        }
    }
}

Map cancelUpload(PluginApiRequest req, String name, String id) {
    Map s = session(req, name, id)
    if (s.error != null) {
        return s.error
    }
    def status = repositories.delete(rp(req, uploadDir(name, id)))
    status.error ? writeFailed(status, 'cancel upload') : [status: 204]
}

// ---------------------------------------------------------------------------------------------------------------
// Manifests
// ---------------------------------------------------------------------------------------------------------------

/** @return the hex of the manifest {@code ref} names, or null when it names none */
String resolveReference(PluginApiRequest req, String name, String ref) {
    if (ref.contains(':')) {
        Matcher m = SHA256_DIGEST.matcher(ref)
        return m.matches() ? m.group(1) : null
    }
    if (!validTag(ref)) {
        return null
    }
    String pointer = readPointer(req, tagPath(name, ref))
    Matcher m = SHA256_DIGEST.matcher(pointer ?: '')
    m.matches() ? m.group(1) : null
}

/** @return the stored media type of manifest {@code hex}, falling back to what its content declares */
String storedMediaType(PluginApiRequest req, String name, String hex, Object parsed) {
    String stored = mediaTypeOf(readPointer(req, mediaTypePath(name, hex)))
    if (stored in IMAGE_TYPES || stored in INDEX_TYPES) {
        return stored
    }
    if (parsed instanceof Map && (parsed.mediaType in IMAGE_TYPES || parsed.mediaType in INDEX_TYPES)) {
        return parsed.mediaType
    }
    parsed instanceof Map && parsed.manifests instanceof List ? OCI_INDEX : OCI_MANIFEST
}

Map getManifest(PluginApiRequest req, String name, String ref) {
    if (ref.contains(':')) {
        Object hex = parseDigest(ref)
        if (hex instanceof Map) {
            return hex
        }
    }
    String hex = resolveReference(req, name, ref)
    def file = hex == null ? null : verified(req, manifestPath(name, hex), hex)
    if (file == null) {
        return error(404, 'MANIFEST_UNKNOWN', 'manifest unknown to registry')
    }
    String mediaType = mediaTypeOf(readPointer(req, mediaTypePath(name, hex)))
    if (!(mediaType in IMAGE_TYPES || mediaType in INDEX_TYPES)) {
        mediaType = storedMediaType(req, name, hex, parseManifest(readManifest(req, name, hex)))
    }
    Map headers = ['Content-Type'         : mediaType,
                   'Docker-Content-Digest': "sha256:${hex}".toString(),
                   'ETag'                 : "\"sha256:${hex}\"".toString()]
    if (req.method() == 'HEAD') {
        return [status: 200, headers: headers + ['Content-Length': String.valueOf(file.size())]]
    }
    def content = repositories.openContent(rp(req, manifestPath(name, hex))).orElse(null)
    content == null ? error(404, 'MANIFEST_UNKNOWN', 'manifest unknown to registry') :
            [status: 200, headers: headers, body: content]
}

/** @return the bytes of stored manifest {@code hex}, or null when missing or over the size cap */
byte[] readManifest(PluginApiRequest req, String name, String hex) {
    def content = repositories.openContent(rp(req, manifestPath(name, hex))).orElse(null)
    if (content == null) {
        return null
    }
    InputStream input = content.input()
    try {
        if (content.size() > MAX_MANIFEST_BYTES) {
            return null
        }
        return input.readNBytes(MAX_MANIFEST_BYTES)
    } finally {
        input.close()
    }
}

Object parseManifest(byte[] bytes) {
    if (bytes == null) {
        return null
    }
    try {
        return JSON.readValue(bytes, Object)
    } catch (IOException | RuntimeException ignored) {
        return null
    }
}

/** @return the hex of descriptor {@code d}'s digest, null when it is not a sha256 digest, or 'invalid' */
Object descriptorHex(Object d) {
    if (!(d instanceof Map) || !(d.digest instanceof String)) {
        return 'invalid'
    }
    Matcher m = SHA256_DIGEST.matcher(d.digest)
    if (m.matches()) {
        return m.group(1)
    }
    otherAlgorithm(d.digest) ? null : 'invalid'
}

Map putManifest(PluginApiRequest req, String name, String ref) {
    String refHex = null
    if (ref.contains(':')) {
        Object hex = parseDigest(ref)
        if (hex instanceof Map) {
            return hex
        }
        refHex = hex
    } else if (!validTag(ref)) {
        return error(400, 'TAG_INVALID', 'invalid tag')
    }
    byte[] bytes = req.readBody(MAX_MANIFEST_BYTES).orElse(null)
    if (bytes == null) {
        return error(413, 'SIZE_INVALID', "manifest larger than ${MAX_MANIFEST_BYTES} bytes".toString())
    }
    String hex = sha256Hex(bytes)
    if (refHex != null && refHex != hex) {
        return error(400, 'DIGEST_INVALID', 'manifest digest does not match the reference')
    }
    Object doc = parseManifest(bytes)
    if (!(doc instanceof Map) || doc.schemaVersion != 2) {
        return error(400, 'MANIFEST_INVALID', 'manifest is not a schema version 2 JSON document')
    }
    String contentType = mediaTypeOf(req.header('content-type').orElse(null))
    String declared = doc.mediaType instanceof String ? doc.mediaType : null
    if (declared != null && contentType != null && declared != contentType) {
        return error(400, 'MANIFEST_INVALID', 'Content-Type does not match the manifest mediaType')
    }
    String mediaType = declared ?: contentType ?: (doc.manifests instanceof List ? OCI_INDEX : OCI_MANIFEST)
    List<Object> refs
    String referenceRoot
    if (mediaType in IMAGE_TYPES) {
        if (!(doc.config instanceof Map) || !(doc.layers == null || doc.layers instanceof List)) {
            return error(400, 'MANIFEST_INVALID', 'image manifest needs a config and a layers list')
        }
        refs = [doc.config] + ((doc.layers ?: []).findAll { !(it instanceof Map && it.mediaType == FOREIGN_LAYER) })
        referenceRoot = 'blob'
    } else if (mediaType in INDEX_TYPES) {
        if (!(doc.manifests instanceof List)) {
            return error(400, 'MANIFEST_INVALID', 'index needs a manifests list')
        }
        refs = doc.manifests
        referenceRoot = 'manifest'
    } else {
        return error(400, 'MANIFEST_INVALID', "unsupported manifest media type ${mediaType}".toString())
    }
    if (refs.size() > MAX_DESCRIPTORS) {
        return error(400, 'MANIFEST_INVALID', 'too many descriptors')
    }
    for (Object d : refs) {
        Object dHex = descriptorHex(d)
        if (dHex == 'invalid') {
            return error(400, 'MANIFEST_INVALID', 'invalid descriptor')
        }
        String path = dHex == null ? null :
                (referenceRoot == 'blob' ? blobPath(name, dHex) : manifestPath(name, dHex))
        if (path == null || verified(req, path, dHex) == null) {
            return error(400, 'MANIFEST_BLOB_UNKNOWN', "${referenceRoot} unknown to registry: ${d.digest}".toString())
        }
    }
    String subjectHex = null
    if (doc.subject != null) {
        Object sHex = descriptorHex(doc.subject)
        if (sHex == 'invalid') {
            return error(400, 'MANIFEST_INVALID', 'invalid subject descriptor')
        }
        subjectHex = sHex
    }

    if (verified(req, manifestPath(name, hex), hex) == null) {
        if (!repositories.exists(rp(req, mediaTypePath(name, hex)))) {
            def status = repositories.deploy(rp(req, mediaTypePath(name, hex)),
                    new ByteArrayInputStream(mediaType.getBytes(StandardCharsets.UTF_8)))
            if (status.error) {
                return writeFailed(status, 'store manifest media type')
            }
        }
        def status = repositories.deploy(rp(req, manifestPath(name, hex)), new ByteArrayInputStream(bytes))
        if (status.error) {
            return writeFailed(status, 'store manifest')
        }
        if (verified(req, manifestPath(name, hex), hex) == null) {
            return error(500, 'UNKNOWN', 'stored manifest does not match its digest')
        }
    }
    if (subjectHex != null) {
        Map descriptor = [mediaType: mediaType, digest: "sha256:${hex}".toString(), size: bytes.length]
        String artifactType = doc.artifactType instanceof String ? doc.artifactType :
                (mediaType in IMAGE_TYPES && doc.config instanceof Map && doc.config.mediaType instanceof String ?
                        doc.config.mediaType : null)
        if (artifactType != null) {
            descriptor.artifactType = artifactType
        }
        if (doc.annotations instanceof Map && !doc.annotations.isEmpty()) {
            descriptor.annotations = doc.annotations
        }
        RepoPath entry = rp(req, "${referrersDir(name, subjectHex)}/${hex}".toString())
        if (!repositories.exists(entry)) {
            def status = repositories.deploy(entry,
                    new ByteArrayInputStream(toJson(descriptor).getBytes(StandardCharsets.UTF_8)))
            if (status.error) {
                return writeFailed(status, 'store referrer')
            }
        }
    }
    if (refHex == null) {
        String digest = "sha256:${hex}".toString()
        if (readPointer(req, tagPath(name, ref)) != digest) {
            def status = repositories.deploy(rp(req, tagPath(name, ref)),
                    new ByteArrayInputStream(digest.getBytes(StandardCharsets.UTF_8)))
            if (status.error) {
                return writeFailed(status, 'tag manifest')
            }
        }
    }
    Map headers = [Location               : req.urlFor("v2/${name}/manifests/sha256:${hex}".toString()),
                   'Docker-Content-Digest': "sha256:${hex}".toString()]
    if (doc.subject instanceof Map && doc.subject.digest instanceof String) {
        headers['OCI-Subject'] = doc.subject.digest
    }
    [status: 201, headers: headers]
}

Map deleteManifest(PluginApiRequest req, String name, String ref) {
    if (!ref.contains(':')) {
        if (!validTag(ref)) {
            return error(404, 'MANIFEST_UNKNOWN', 'manifest unknown to registry')
        }
        RepoPath tag = rp(req, tagPath(name, ref))
        if (!repositories.exists(tag)) {
            return error(404, 'MANIFEST_UNKNOWN', 'manifest unknown to registry')
        }
        def status = repositories.delete(tag)
        return status.error ? writeFailed(status, 'delete tag') : [status: 202]
    }
    Object parsed = parseDigest(ref)
    if (parsed instanceof Map) {
        return parsed
    }
    String hex = parsed
    RepoPath manifest = rp(req, manifestPath(name, hex))
    if (!repositories.exists(manifest)) {
        return error(404, 'MANIFEST_UNKNOWN', 'manifest unknown to registry')
    }
    Object doc = verified(req, manifestPath(name, hex), hex) == null ? null :
            parseManifest(readManifest(req, name, hex))
    def status = repositories.delete(manifest)
    if (status.error) {
        return writeFailed(status, 'delete manifest')
    }
    RepoPath mediaType = rp(req, mediaTypePath(name, hex))
    if (repositories.exists(mediaType)) {
        repositories.delete(mediaType)
    }
    if (doc instanceof Map && doc.subject != null) {
        Object sHex = descriptorHex(doc.subject)
        if (sHex instanceof String && sHex != 'invalid') {
            RepoPath entry = rp(req, "${referrersDir(name, sHex)}/${hex}".toString())
            if (repositories.exists(entry)) {
                repositories.delete(entry)
            }
        }
    }
    String digest = "sha256:${hex}".toString()
    int seen = 0
    for (def child : repositories.getChildren(rp(req, tagsDir(name)))) {
        if (++seen > MAX_TAGS) {
            break
        }
        if (!child.isFolder() && validTag(child.repoPath.name) &&
                readPointer(req, tagPath(name, child.repoPath.name)) == digest) {
            repositories.delete(child.repoPath)
        }
    }
    [status: 202]
}

// ---------------------------------------------------------------------------------------------------------------
// Discovery
// ---------------------------------------------------------------------------------------------------------------

Map listTags(PluginApiRequest req, String name) {
    if (!repositories.exists(rp(req, name))) {
        return error(404, 'NAME_UNKNOWN', 'repository name not known to registry')
    }
    Integer n = null
    String nParam = req.queryParam('n').orElse(null)
    if (nParam != null && !nParam.isEmpty()) {
        if (!(nParam ==~ /[0-9]{1,9}/)) {
            return error(400, 'UNSUPPORTED', 'invalid n')
        }
        n = Integer.parseInt(nParam)
    }
    String last = req.queryParam('last').orElse(null)
    List<String> tags = []
    for (def child : repositories.getChildren(rp(req, tagsDir(name)))) {
        String tag = child.repoPath.name
        if (!child.isFolder() && validTag(tag) && (last == null || tag > last)) {
            tags << tag
        }
    }
    tags.sort()
    int limit = Math.min(n == null ? MAX_TAGS : n, MAX_TAGS)
    Map headers = [:]
    if (tags.size() > limit) {
        tags = tags.subList(0, limit)
        if (limit > 0) {
            String next = req.urlFor("v2/${name}/tags/list".toString()) + '?n=' + limit + '&last=' +
                    URLEncoder.encode(tags[-1], StandardCharsets.UTF_8)
            headers.Link = "<${next}>; rel=\"next\"".toString()
        }
    }
    json(200, 'application/json', [name: clientName(req, name), tags: tags], headers)
}

Map referrers(PluginApiRequest req, String name, String digest) {
    Object hex = parseDigest(digest)
    if (hex instanceof Map) {
        return hex
    }
    List<String> filters = req.queryParams('artifactType').findAll { !it.isEmpty() }
    List<Map> manifests = []
    int seen = 0
    for (def child : repositories.getChildren(rp(req, referrersDir(name, hex)))) {
        if (++seen > MAX_REFERRERS) {
            break
        }
        String refHex = child.repoPath.name
        if (child.isFolder() || !(refHex ==~ /[a-f0-9]{64}/) || verified(req, manifestPath(name, refHex), refHex) == null) {
            continue
        }
        Object descriptor = parseManifest(readPointerBytes(req, child.repoPath))
        if (!(descriptor instanceof Map) || descriptor.digest != "sha256:${refHex}".toString()) {
            continue
        }
        if (filters.isEmpty() || descriptor.artifactType in filters) {
            manifests << descriptor
        }
    }
    Map headers = filters.isEmpty() ? [:] : ['OCI-Filters-Applied': 'artifactType']
    json(200, OCI_INDEX, [schemaVersion: 2, mediaType: OCI_INDEX, manifests: manifests], headers)
}

byte[] readPointerBytes(PluginApiRequest req, RepoPath path) {
    def file = repositories.findFile(path).orElse(null)
    if (file == null || file.size() > MAX_POINTER_BYTES * 16) {
        return null
    }
    repositories.getStringContent(path)?.getBytes(StandardCharsets.UTF_8)
}

// ---------------------------------------------------------------------------------------------------------------
// The package type
// ---------------------------------------------------------------------------------------------------------------

packageType(TYPE_NAME) {
    displayName 'OCI registry'

    // Content enters only through the registry API, whose internal deploys arrive as ArtifactoryDeployRequest;
    // a direct HTTP PUT into the repository would let a client store unverified content at a digest path.
    upload { request, repoPath ->
        request instanceof ArtifactoryDeployRequest ? null :
                UploadDecision.reject(403, 'oci-registry repositories accept content only through the /v2/ registry API')
    }

    api { PluginApiRequest request ->
        Map response = handle(request)
        Map headers = (response.headers ?: [:]) + ['Docker-Distribution-API-Version': 'registry/2.0']
        response + [headers: headers]
    }
}
