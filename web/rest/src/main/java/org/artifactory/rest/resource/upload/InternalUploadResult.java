package org.artifactory.rest.resource.upload;

/**
 * Response body returned by {@link InternalUploadResource} on a successful upload. Mirrors the
 * essentials of the standard deploy response so the internal caller can confirm what was stored
 * (resolved path — including the {@code -cache} half for remote repos — size, and checksums).
 *
 * <p>Checksums and size are read back from the stored item after the write completes; when that
 * read is unavailable they are left {@code null}/{@code -1} rather than failing the request,
 * since the upload itself already succeeded.
 *
 * @author Fawaz Atwa
 */
public record InternalUploadResult(
        String repoPath,
        long size,
        String sha256,
        String sha1,
        String md5) {
}
