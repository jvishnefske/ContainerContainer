package org.artifactory.util;

import jakarta.annotation.Nonnull;
import org.jfrog.storage.binstore.ifc.SkippableInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 */
public class LazyStreamLoader extends InputStream {
    private final StreamProvider streamProvider;
    private InputStream stream;

    public LazyStreamLoader(StreamProvider streamProvider) {
        this.streamProvider = streamProvider;
    }

    /**
     * The method is not thread save
     */
    private InputStream getStreamInternal() throws IOException {
        if (stream == null) {
            stream = streamProvider.getStream();
        }
        return stream;
    }

    @Override
    public int read() throws IOException {
        return getStreamInternal().read();
    }

    @Override
    public int read(@Nonnull byte[] data) throws IOException {
        return getStreamInternal().read(data);
    }

    @Override
    public int read(@Nonnull byte[] data, int off, int len) throws IOException {
        return getStreamInternal().read(data, off, len);
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            getStreamInternal().close();
        }
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        return getStreamInternal().readAllBytes();
    }

    @Override
    public byte[] readNBytes(int len) throws IOException {
        return getStreamInternal().readNBytes(len);
    }

    @Override
    public int readNBytes(byte[] b, int off, int len) throws IOException {
        return getStreamInternal().readNBytes(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return getStreamInternal().skip(n);
    }

    @Override
    public void skipNBytes(long n) throws IOException {
        getStreamInternal().skipNBytes(n);
    }

    @Override
    public int available() throws IOException {
        return getStreamInternal().available();
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        return getStreamInternal().transferTo(out);
    }

    public boolean isRangeAware() throws IOException {
        return this.getStreamInternal() instanceof SkippableInputStream skippableInputStream &&
                skippableInputStream.isRangedStream();
    }

    public interface StreamProvider {
        InputStream getStream() throws IOException;
    }
}