package org.artifactory.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

/**
 * @author Amir Avidan
 */
@ExtendWith(MockitoExtension.class)
class CountingServletOutputStreamTest {

    @Mock
    private ServletOutputStream delegate;
    @Mock
    private WriteListener writeListener;
    private CountingServletOutputStream countingServletOutputStream;

    @BeforeEach
    void setUp() {
        countingServletOutputStream = new CountingServletOutputStream(delegate);
    }

    @Test
    void testWriteInt() throws IOException {
        countingServletOutputStream.write(1);
        assertEquals(1, countingServletOutputStream.getByteCount());
        verify(delegate).write(1);
    }

    @Test
    void testWriteByteArray() throws IOException {
        byte[] data = new byte[5];
        countingServletOutputStream.write(data);
        assertEquals(5, countingServletOutputStream.getByteCount());
        verify(delegate).write(data);
    }

    @Test
    void testWriteByteArrayWithOffsetAndLength() throws IOException {
        byte[] data = new byte[10];
        countingServletOutputStream.write(data, 2, 5);
        assertEquals(5, countingServletOutputStream.getByteCount());
        verify(delegate).write(data, 2, 5);
    }

    @Test
    void testIsReady() {
        Mockito.when(delegate.isReady()).thenReturn(true);
        assertTrue(countingServletOutputStream.isReady());
        verify(delegate).isReady();
    }

    @Test
    void testSetWriteListener() {
        countingServletOutputStream.setWriteListener(writeListener);
        verify(delegate).setWriteListener(writeListener);
    }

    @Test
    void testFlush() throws IOException {
        countingServletOutputStream.flush();
        verify(delegate).flush();
    }

    @Test
    void testClose() throws IOException {
        countingServletOutputStream.close();
        verify(delegate).close();
    }
}