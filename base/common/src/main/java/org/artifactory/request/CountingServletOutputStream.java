package org.artifactory.request;

import lombok.Getter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;

/**
 * Count number of bytes written to response when using HttpServletResponse.
 *
 * @author Amir Avidan
 */
public class CountingServletOutputStream extends ServletOutputStream {

    @Getter
    private long byteCount = 0;
    private final ServletOutputStream delegate;


    public CountingServletOutputStream(ServletOutputStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        byteCount++;
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        byteCount += len;
        delegate.write(b, off, len);
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        delegate.setWriteListener(writeListener);
    }

    @Override
    public void flush() throws IOException {
        this.delegate.flush();
    }

    @Override
    public void close() throws IOException {
        this.delegate.close();
    }

    @Override
    public void write(byte[] b) throws IOException {
        byteCount += b.length;
        this.delegate.write(b);
    }
}