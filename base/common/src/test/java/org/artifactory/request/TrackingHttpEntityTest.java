package org.artifactory.request;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.conn.EofSensorInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Oleksandr Legenkiy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackingHttpEntityTest {

    @Mock
    private HttpEntity originalHttpEntityMock;
    @Mock
    private Runnable onCompleteMock;
    @Mock
    private InputStream inputStreamMock;

    private TrackingHttpEntity entity;

    @BeforeEach
    void setUp() throws IOException {
        when(originalHttpEntityMock.getContent()).thenReturn(inputStreamMock);
        entity = new TrackingHttpEntity(originalHttpEntityMock, onCompleteMock);
    }

    @Test
    void getContent() throws IOException {
        InputStream result = entity.getContent();
        assertInstanceOf(EofSensorInputStream.class, result);
        result.close();
        verify(onCompleteMock).run();
    }

    @Test
    void consumeContent() throws IOException {
        entity.consumeContent();
        verify(originalHttpEntityMock).consumeContent();
        verify(onCompleteMock).run();
    }

    @Test
    void writeTo() throws IOException {
        OutputStream outputStreamMock = Mockito.mock(OutputStream.class);
        entity.writeTo(outputStreamMock);
        verify(originalHttpEntityMock).writeTo(outputStreamMock);
        verify(onCompleteMock).run();
    }

    @Test
    void isRepeatable() {
        when(originalHttpEntityMock.isRepeatable()).thenReturn(true);
        assertTrue(entity.isRepeatable());
        verify(originalHttpEntityMock).isRepeatable();
    }

    @Test
    void isChunked() {
        when(originalHttpEntityMock.isChunked()).thenReturn(true);
        assertTrue(entity.isChunked());
        verify(originalHttpEntityMock).isChunked();
    }

    @Test
    void getContentLength() {
        when(originalHttpEntityMock.getContentLength()).thenReturn(123L);
        assertEquals(123L, entity.getContentLength());
        verify(originalHttpEntityMock).getContentLength();
    }

    @Test
    void getContentType() {
        Header headerMock = Mockito.mock(Header.class);
        when(originalHttpEntityMock.getContentType()).thenReturn(headerMock);
        assertEquals(headerMock, entity.getContentType());
        verify(originalHttpEntityMock).getContentType();
    }

    @Test
    void getContentEncoding() {
        Header headerMock = Mockito.mock(Header.class);
        when(originalHttpEntityMock.getContentEncoding()).thenReturn(headerMock);
        assertEquals(headerMock, entity.getContentEncoding());
        verify(originalHttpEntityMock).getContentEncoding();
    }

    @Test
    void isStreaming() {
        when(originalHttpEntityMock.isStreaming()).thenReturn(true);
        assertTrue(entity.isStreaming());
        verify(originalHttpEntityMock).isStreaming();
    }
}