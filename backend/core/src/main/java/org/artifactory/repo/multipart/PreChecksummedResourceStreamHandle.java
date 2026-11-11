package org.artifactory.repo.multipart;

import com.jfrog.storage.sonic.DownloadResult;
import org.apache.commons.io.IOUtils;
import org.artifactory.repo.HttpRepo;
import org.artifactory.repo.RemoteResourceTrafficContextHolder;
import org.artifactory.resource.ResourceStreamHandle;
import org.jfrog.storage.binstore.ifc.model.BinaryElementHeaders;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class PreChecksummedResourceStreamHandle implements ResourceStreamHandle, RemoteResourceTrafficContextHolder {

    private final DownloadResult result;
    private final HttpRepo.TrafficAwareRemoteResourceStreamHandle delegate;
    private InputStream inputStream;

    public PreChecksummedResourceStreamHandle(DownloadResult result,
            HttpRepo.TrafficAwareRemoteResourceStreamHandle delegate) {
        this.result = result;
        this.delegate = delegate;
    }

    @Override
    public InputStream getInputStream() {
        if (inputStream != null) {
            return inputStream;
        }
        try {
            inputStream = new FileInputStream(result.getFile());
            return inputStream;
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Downloaded file not found: " + result.getFile(), e);
        }
    }

    @Override
    public long getSize() {
        return result.getSize();
    }

    @Override
    public long getTotalBytesRead() {
        return result.getSize();
    }

    @Override
    public String getRemoteIp() {
        return result.getRemoteIp();
    }

    @Override
    public void close() {
        try {
            IOUtils.closeQuietly(inputStream);
            delegate.close();
        } finally {
            result.close();
        }
    }

    public Map<String, String> getPreCalculatedHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put(BinaryElementHeaders.PRE_CALCULATED_FILE_PATH.getHeaderName(),
                result.getFile().getAbsolutePath());
        headers.put(BinaryElementHeaders.PRE_CALCULATED_SHA1.getHeaderName(), result.getSha1());
        headers.put(BinaryElementHeaders.PRE_CALCULATED_SHA256.getHeaderName(), result.getSha256());
        headers.put(BinaryElementHeaders.PRE_CALCULATED_MD5.getHeaderName(), result.getMd5());
        headers.put(BinaryElementHeaders.PRE_CALCULATED_LENGTH.getHeaderName(), String.valueOf(result.getSize()));
        return headers;
    }
}
