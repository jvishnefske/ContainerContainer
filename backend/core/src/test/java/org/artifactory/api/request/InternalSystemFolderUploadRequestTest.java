package org.artifactory.api.request;

import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InternalSystemFolderUploadRequestTest {

    private static final String REPO_KEY = "my-local";
    private static final String FILE_PATH = ".evidence/sha256:abc/sig.json";

    @Test
    void getInputStream_returnsConstructorArgument() {
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, FILE_PATH);
        InputStream stream = InputStream.nullInputStream();

        InternalSystemFolderUploadRequest request =
                new InternalSystemFolderUploadRequest(repoPath, stream, 42L);

        assertSame(stream, request.getInputStream());
    }

    @Test
    void getContentLength_returnsConstructorArgument() {
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, FILE_PATH);

        InternalSystemFolderUploadRequest request =
                new InternalSystemFolderUploadRequest(repoPath, InputStream.nullInputStream(), 42L);

        assertEquals(42L, request.getContentLength());
    }

    @Test
    void getContentLength_negativeOneMeansUnknownSize() {
        RepoPath repoPath = RepoPathFactory.create(REPO_KEY, FILE_PATH);

        InternalSystemFolderUploadRequest request =
                new InternalSystemFolderUploadRequest(repoPath, InputStream.nullInputStream(), -1L);

        assertEquals(-1L, request.getContentLength());
    }
}
