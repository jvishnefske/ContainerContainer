package org.artifactory.repo;

import org.artifactory.repo.HttpRepo.TrafficAwareRemoteResourceStreamHandle;
import org.artifactory.repo.multipart.PreChecksummedResourceStreamHandle;
import com.jfrog.storage.sonic.DownloadResult;
import org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreChecksummedResourceStreamHandleTest {

    @Test
    void getPreCalculatedHeaders_usesDedicatedHeadersOnly(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.tmp");
        Files.write(tempFile, "content".getBytes());

        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(tempFile.toFile());
        when(result.getSha1()).thenReturn("abc123sha1");
        when(result.getSha256()).thenReturn("def456sha256");
        when(result.getMd5()).thenReturn("789md5");
        when(result.getSize()).thenReturn(1000L);

        PreChecksummedResourceStreamHandle handle = createHandle(result);

        Map<String, String> headers = handle.getPreCalculatedHeaders();

        assertEquals(5, headers.size());
        assertEquals(tempFile.toFile().getAbsolutePath(),
                headers.get(BinaryElementHeaders.PRE_CALCULATED_FILE_PATH.getHeaderName()));
        assertEquals("abc123sha1", headers.get(BinaryElementHeaders.PRE_CALCULATED_SHA1.getHeaderName()));
        assertEquals("def456sha256", headers.get(BinaryElementHeaders.PRE_CALCULATED_SHA256.getHeaderName()));
        assertEquals("789md5", headers.get(BinaryElementHeaders.PRE_CALCULATED_MD5.getHeaderName()));
        assertEquals("1000", headers.get(BinaryElementHeaders.PRE_CALCULATED_LENGTH.getHeaderName()));

        assertFalse(headers.containsKey(BinaryElementHeaders.CHECKSUM_SHA1.getHeaderName()));
        assertFalse(headers.containsKey(BinaryElementHeaders.CHECKSUM_SHA256.getHeaderName()));
        assertFalse(headers.containsKey(BinaryElementHeaders.CHECKSUM_MD5.getHeaderName()));
        assertFalse(headers.containsKey(BinaryElementHeaders.CONTENT_LENGTH.getHeaderName()));
    }

    @Test
    void close_closesResultAndDelegate(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.tmp");
        Files.write(tempFile, "content".getBytes());

        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(tempFile.toFile());

        TrafficAwareRemoteResourceStreamHandle delegate = mock(TrafficAwareRemoteResourceStreamHandle.class);

        PreChecksummedResourceStreamHandle handle = createHandle(result, delegate);
        handle.close();

        verify(delegate).close();
        verify(result).close();
    }

    @Test
    void getRemoteIp_returnsResultRemoteIp(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.tmp");
        Files.write(tempFile, "content".getBytes());

        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(tempFile.toFile());
        when(result.getRemoteIp()).thenReturn("192.168.1.100");

        PreChecksummedResourceStreamHandle handle = createHandle(result);

        assertEquals("192.168.1.100", handle.getRemoteIp());
    }

    @Test
    void getInputStream_returnsFileInputStream(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.tmp");
        Files.write(tempFile, "content".getBytes());

        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(tempFile.toFile());

        PreChecksummedResourceStreamHandle handle = createHandle(result);

        try (InputStream is = handle.getInputStream()) {
            byte[] content = is.readAllBytes();
            assertEquals("content", new String(content));
        }
    }

    @Test
    void getInputStream_returnsSameInstance(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.tmp");
        Files.write(tempFile, "content".getBytes());

        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(tempFile.toFile());

        PreChecksummedResourceStreamHandle handle = createHandle(result);

        InputStream first = handle.getInputStream();
        InputStream second = handle.getInputStream();

        assertSame(first, second);
        handle.close();
    }

    @Test
    void getInputStream_throwsIllegalState_whenFileNotFound() {
        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(new java.io.File("/nonexistent/path/file.tmp"));

        PreChecksummedResourceStreamHandle handle = createHandle(result);

        assertThrows(IllegalStateException.class, () -> handle.getInputStream());
    }

    @Test
    void getSize_returnsResultSize(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.tmp");
        Files.write(tempFile, "content".getBytes());

        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(tempFile.toFile());
        when(result.getSize()).thenReturn(12345L);

        PreChecksummedResourceStreamHandle handle = createHandle(result);

        assertEquals(12345L, handle.getSize());
    }

    @Test
    void getTotalBytesRead_returnsResultSize(@TempDir Path tempDir) throws IOException {
        Path tempFile = tempDir.resolve("test.tmp");
        Files.write(tempFile, "content".getBytes());

        DownloadResult result = mock(DownloadResult.class);
        when(result.getFile()).thenReturn(tempFile.toFile());
        when(result.getSize()).thenReturn(99999L);

        PreChecksummedResourceStreamHandle handle = createHandle(result);

        assertEquals(99999L, handle.getTotalBytesRead());
    }

    private PreChecksummedResourceStreamHandle createHandle(DownloadResult result) {
        return createHandle(result, mock(TrafficAwareRemoteResourceStreamHandle.class));
    }

    private PreChecksummedResourceStreamHandle createHandle(DownloadResult result,
            TrafficAwareRemoteResourceStreamHandle delegate) {
        return new PreChecksummedResourceStreamHandle(result, delegate);
    }
}
