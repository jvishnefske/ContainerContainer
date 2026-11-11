package org.artifactory.engine;

import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.NullWriter;
import org.artifactory.api.request.ArtifactoryResponse;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.checksum.ChecksumInfo;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.fs.FileInfo;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.slf4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.function.LongConsumer;

import static org.artifactory.request.ArtifactoryRequest.CHECKSUM_SHA256;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Tamir Hadad
 */
class SuccessfulDeploymentResponseHelperTest {
    private static AutoCloseable mocks;
    @Mock
    InternalRepositoryService repositoryService;
    @Spy
    ArtifactoryResponse response = new InternalTestResponse();

    @BeforeEach
    public void init() throws IOException {
        mocks = openMocks(this);
        FileInfo fileInfo = mock(FileInfo.class);
        when(repositoryService.getFileInfo(any())).thenReturn(fileInfo);
        ChecksumsInfo checksumsInfo = mock(ChecksumsInfo.class);
        ChecksumInfo md5ChecksumsInfo = mock(ChecksumInfo.class);
        ChecksumInfo sha1ChecksumsInfo = mock(ChecksumInfo.class);
        ChecksumInfo sha2ChecksumsInfo = mock(ChecksumInfo.class);
        when(fileInfo.getChecksumsInfo()).thenReturn(checksumsInfo);

        when(checksumsInfo.getChecksumInfo(ChecksumType.md5)).thenReturn(md5ChecksumsInfo);
        when(checksumsInfo.getChecksumInfo(ChecksumType.sha1)).thenReturn(sha1ChecksumsInfo);
        when(checksumsInfo.getChecksumInfo(ChecksumType.sha256)).thenReturn(sha2ChecksumsInfo);

        when(md5ChecksumsInfo.getActual()).thenReturn("md5");
        when(sha1ChecksumsInfo.getActual()).thenReturn("sha1");
        when(sha2ChecksumsInfo.getActual()).thenReturn("sha2");

        when(fileInfo.getCreated()).thenReturn(System.currentTimeMillis());
        when(fileInfo.getCreatedBy()).thenReturn("admin");
        when(fileInfo.getMimeType()).thenReturn("text/plain");
        when(fileInfo.getSize()).thenReturn(6L);
    }

    @AfterEach
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testDeploymentReturnsSha2Header() throws IOException {
        SuccessfulDeploymentResponseHelper successfulDeploymentResponseHelper =
                new SuccessfulDeploymentResponseHelper();
        successfulDeploymentResponseHelper.writeSuccessfulDeploymentResponse(repositoryService, response,
                RepoPathFactory.create("path"), "url", false, true);
        verify(response).setHeader(CHECKSUM_SHA256, "sha2");
    }

    @Test
    void testDeploymentReturnsUtf8ContentType()
            throws IOException {
        SuccessfulDeploymentResponseHelper helper = new SuccessfulDeploymentResponseHelper();
        ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);

        helper.writeSuccessfulDeploymentResponse(repositoryService, response,
                RepoPathFactory.create("repo", "测试22.txt"), "http://localhost/repo/测试22.txt", false, false);

        verify(response).setContentType(contentTypeCaptor.capture());
        String contentType = contentTypeCaptor.getValue();
        assertTrue(contentType.contains(ArtifactRestConstants.MT_ITEM_CREATED),
                "Content type should contain the expected media type");
        assertTrue(contentType.contains("charset=UTF-8"),
                "Content type should include UTF-8 charset to preserve non-ASCII characters in JSON response");
    }

    @Test
    void testContentTypeSetBeforeWriter() throws IOException {
        SuccessfulDeploymentResponseHelper helper = new SuccessfulDeploymentResponseHelper();
        InOrder inOrder = inOrder(response);

        helper.writeSuccessfulDeploymentResponse(repositoryService, response,
                RepoPathFactory.create("repo", "测试22.txt"), "http://localhost/repo/测试22.txt", false, false);

        inOrder.verify(response).setContentType(contains("charset=UTF-8"));
        inOrder.verify(response).getWriter();
    }

    public static class InternalTestResponse implements ArtifactoryResponse {

        @Override
        public void sendStream(CountingInputStream is, LongConsumer callback) throws IOException {
            // empty
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public void setLastModified(long lastModified) {

        }

        @Override
        public void setEtag(String etag) {

        }

        @Override
        public void setSha1(String sha1) {

        }

        @Override
        public void setSha2(String sha2) {

        }

        @Override
        public void setMd5(String md5) {

        }

        @Override
        public void setRangeSupport(String bytes) {

        }

        @Override
        public long getContentLength() {
            return 0;
        }

        @Override
        public void setContentLength(long length) {

        }

        @Override
        public void setContentType(String contentType) {

        }

        @Override
        public Writer getWriter() throws IOException {
            return new NullWriter();
        }

        @Override
        public void sendInternalError(Exception exception, Logger logger) throws IOException {

        }

        @Override
        public void sendError(int statusCode, String reason, Logger logger) throws IOException {

        }

        @Override
        public void sendError(int statusCode, String message) throws IOException {

        }

        @Override
        public void sendStream(InputStream is) throws IOException {

        }

        @Override
        public void sendSuccess() {

        }

        @Override
        public int getStatus() {
            return 0;
        }

        @Override
        public void setStatus(int statusCode) {

        }

        @Override
        public void setHeader(String header, String value) {

        }

        @Override
        public void setRedirect(String url) {

        }

        @Override
        public boolean isCommitted() {
            return false;
        }

        @Override
        public boolean isSuccessful() {
            return false;
        }

        @Override
        public boolean isSuccessfulOrRedirect() {
            return false;
        }

        @Override
        public void flush() {

        }

        @Override
        public void sendAuthorizationRequired(String message, String realm) throws IOException {

        }

        @Override
        public boolean isPropertiesQuery() {
            return false;
        }

        @Override
        public String getPropertiesMediaType() {
            return null;
        }

        @Override
        public void setPropertiesMediaType(String propsQueryFormat) {

        }

        @Override
        public void close(Closeable closeable) {

        }
    }
}