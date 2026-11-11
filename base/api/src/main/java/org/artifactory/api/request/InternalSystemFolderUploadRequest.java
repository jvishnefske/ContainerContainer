package org.artifactory.api.request;

import org.artifactory.repo.RepoPath;

import java.io.InputStream;

/**
 * A synthetic {@link org.artifactory.request.ArtifactoryRequest} used by the internal system-folder upload
 * endpoint ({@code PUT /api/internal/upload/{repoKey}/{filePath}}).
 *
 * <p>Subclassing {@link InternalArtifactoryRequest} follows the same pattern as
 * {@link ReplicationChecksumDeployRequest}. This concrete type is the single discriminator that tells the
 * upload pipeline it is handling an internal system-folder upload rather than a regular deploy: it is detected
 * by {@code UploadServiceUtils.getTargetRepository()}, which resolves remote repo keys to their {@code -cache}
 * half (instead of returning {@code null}) so the upload is admitted rather than rejected.
 *
 * <p>The request otherwise flows through the same {@code UploadServiceImpl.validateRequestAndUpload} pipeline as
 * a normal deploy — including {@code assertValidDeployPathAndPermissions} (deploy-permission enforcement),
 * plugins and the full interceptor chain.
 *
 * @author Fawaz Atwa
 */
public class InternalSystemFolderUploadRequest extends InternalArtifactoryRequest {

    private final InputStream inputStream;
    private final long contentLength;

    /**
     * @param contentLength pass {@code -1} when the size is unknown and must be computed from the
     *                      stream; the pipeline will measure it during the write.
     */
    public InternalSystemFolderUploadRequest(RepoPath repoPath, InputStream inputStream, long contentLength) {
        super(repoPath, true);
        this.inputStream = inputStream;
        this.contentLength = contentLength;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }
}
