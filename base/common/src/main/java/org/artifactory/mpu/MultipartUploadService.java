package org.artifactory.mpu;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Andrey Tashlitsky
 */
public interface MultipartUploadService {

    String MPU_SCOPE = "internal:mpu:x";

    /**
     * Generates a token for a single multipart upload session.
     *
     * @param repoKey    Target repository key to deploy.
     * @param path       Target path to deploy.
     * @param partSizeMB Size of each part upload.
     * @return Token for a multipart upload session.
     */
    @Nonnull
    String generateToken(@Nonnull String repoKey, @Nonnull String path, int partSizeMB);

    /**
     * Gets a URL to upload a single part.
     *
     * @param partNumber Index of the part.
     * @return Signed URL to the storage bucket.
     */
    @Nonnull
    String urlPart(int partNumber);

    /**
     * Complete the multipart upload session.
     *
     * @param response The response object to write all the upload info to.
     * @param sha1     The checksum of the complete file.
     */
    void complete(HttpServletResponse response, @Nonnull String sha1) throws IOException;

    /**
     * Get status for multipart upload request
     *
     * @return Pair of Task that includes Progress (0 - 100) and Status and Error message if exists.
     * The status of the multipart upload can be from the following: PARTS, QUEUED, PROCESSING, FINISHED, ABORTED,
     * RETRYABLE_ERROR, NON_RETRYABLE_ERROR
     */
    MultipartStatusResult status();

    /**
     * Abort a multipart upload session.
     */
    void abort();

    /**
     * Check if multipart upload is supported.
     */
    boolean isFeatureEnabled();
}
