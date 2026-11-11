package org.artifactory.request;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.conn.EofSensorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is a wrapper class for a HttpEntity. It allows to track when the request body consumption is completed, i.e.
 * when a "download" is fully completed. It also handles scenarios like "stream closed" and "stream aborted".
 * See {@link SingleExecCallbackEofWatcher} for the details.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
public class TrackingHttpEntity implements HttpEntity {

    private final HttpEntity originalEntity;
    private final Runnable onComplete;
    // Ensure that the callback is executed only once
    private final AtomicBoolean callbackCompleted = new AtomicBoolean(false);

    public TrackingHttpEntity(HttpEntity originalEntity, Runnable onComplete) {
        Objects.requireNonNull(originalEntity, "Param 'originalEntity' cannot be null");
        Objects.requireNonNull(onComplete, "Param 'onComplete' cannot be null");
        this.originalEntity = originalEntity;
        this.onComplete = onComplete;
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        SingleExecCallbackEofWatcher eofWatcher = new SingleExecCallbackEofWatcher(onComplete);
        return new EofSensorInputStream(originalEntity.getContent(), eofWatcher);
    }

    /**
     * @deprecated should not be used explicitly
     */
    @Override
    @Deprecated(since = "7.124.0")
    public void consumeContent() throws IOException {
        try {
            originalEntity.consumeContent();
        } finally {
            executeOnCompletion();
        }
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        try {
            originalEntity.writeTo(outStream);
        } finally {
            executeOnCompletion();
        }
    }

    @Override
    public boolean isRepeatable() {
        return originalEntity.isRepeatable();
    }

    @Override
    public boolean isChunked() {
        return originalEntity.isChunked();
    }

    @Override
    public long getContentLength() {
        return originalEntity.getContentLength();
    }

    @Override
    public Header getContentType() {
        return originalEntity.getContentType();
    }

    @Override
    public Header getContentEncoding() {
        return originalEntity.getContentEncoding();
    }

    @Override
    public boolean isStreaming() {
        return originalEntity.isStreaming();
    }

    private void executeOnCompletion() {
        if (callbackCompleted.compareAndSet(false, true)) {
            try {
                onComplete.run();
            } catch (Exception e) {
                log.warn("Failed to execute 'onComplete' callback", e);
            }
        }
    }
}
