package org.artifactory.request;

import lombok.NonNull;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.artifactory.repo.RepoPath;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.webapp.servlet.HttpArtifactoryRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;

import static org.artifactory.checksum.ChecksumInfo.TRUSTED_FILE_MARKER;
import static org.artifactory.common.ConstantValues.buildInfoManifestFileSizeLimitBytes;

/**
 * BE EXTREMELY CAREFUL
 * It is a necessity of the build-info repo to buffer the entire content of a build json - whatever {@param httpRequest}
 * you initialize this class with will be read into memory in its entirety!
 *
 * @author Yuval Reches
 * @author Gidi Shabat
 */
public class BuildArtifactoryRequestImpl extends HttpArtifactoryRequest implements BuildArtifactoryRequest {

    private byte[] content;

    public BuildArtifactoryRequestImpl(HttpServletRequest httpRequest) throws IOException {
        super(httpRequest);
        readBuildInfoManifestContent(httpRequest);
    }

    private void readBuildInfoManifestContent(@NonNull HttpServletRequest httpRequest) throws IOException {
        long maxLength = buildInfoManifestFileSizeLimitBytes.getLong();
        InputStream input = httpRequest.getInputStream();
        BoundedInputStream boundedInput = new BoundedInputStream(input, maxLength + 1);
        content = IOUtils.toByteArray(boundedInput);
        if (content.length == maxLength + 1) {
            throw new BadRequestException("Build-info manifest file size must not exceed " + maxLength + " bytes");
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void replaceContent(byte[] newContent) {
        content = newContent.clone();
    }

    @Override
    public long getContentLength() {
        return content.length;
    }

    @Override
    public Enumeration<String> getHeaderValues(@NonNull String headerName) {
        switch (headerName) {
            case CHECKSUM_SHA1:
            case CHECKSUM_MD5:
            case CHECKSUM_SHA256:
                return Collections.enumeration(Collections.singletonList(TRUSTED_FILE_MARKER));
            default:
                return super.getHeaderValues(headerName);
        }
    }

    @Override
    public void setRepoPath(RepoPath repoPath) {
        super.setRepoPath(repoPath);
    }
}
