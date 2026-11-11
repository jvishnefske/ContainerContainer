package org.artifactory.request;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author Oleksandr Legenkiy
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SingleExecCallbackEofWatcherTest {

    private static final String EXP_ERR_MESSAGE = "Failed to execute 'onClose' callback";

    @Mock
    private Runnable onCompleteMock;
    @Mock
    private InputStream inputStreamMock;

    private ListAppender<ILoggingEvent> listAppender;
    private SingleExecCallbackEofWatcher watcher;

    @BeforeEach
    void setUp() {
        watcher = new SingleExecCallbackEofWatcher(onCompleteMock);
    }

    @Test
    void testEofDetected() throws IOException {
        assertTrue(watcher.eofDetected(inputStreamMock));
        assertTrue(watcher.eofDetected(inputStreamMock));
        verify(onCompleteMock).run(); // only called once
        verifyNoInteractions(inputStreamMock);
    }

    @Test
    void testStreamClosed() throws IOException {
        assertTrue(watcher.streamClosed(inputStreamMock));
        assertTrue(watcher.streamClosed(inputStreamMock));
        verify(onCompleteMock).run(); // only called once
        verifyNoInteractions(inputStreamMock);
    }

    @Test
    void testStreamAbort() throws IOException {
        assertTrue(watcher.streamAbort(inputStreamMock));
        assertTrue(watcher.streamAbort(inputStreamMock));
        verify(onCompleteMock).run(); // only called once
        verifyNoInteractions(inputStreamMock);
    }

    @Test
    void testExceptionHandledOnEofDetected() throws IOException {
        initLoggerAppender();
        doThrow(new RuntimeException("TEST")).when(onCompleteMock).run();
        assertTrue(watcher.eofDetected(inputStreamMock));
        assertEquals(EXP_ERR_MESSAGE, listAppender.list.getFirst().getMessage());
        verifyNoInteractions(inputStreamMock);
    }

    @Test
    void testExceptionHandledOnStreamClosed() throws IOException {
        initLoggerAppender();
        doThrow(new RuntimeException("TEST")).when(onCompleteMock).run();
        assertTrue(watcher.streamClosed(inputStreamMock));
        assertEquals(EXP_ERR_MESSAGE, listAppender.list.getFirst().getMessage());
        verifyNoInteractions(inputStreamMock);
    }

    @Test
    void testExceptionHandledOnStreamAbort() throws IOException {
        initLoggerAppender();
        doThrow(new RuntimeException("TEST")).when(onCompleteMock).run();
        assertTrue(watcher.streamAbort(inputStreamMock));
        assertEquals(EXP_ERR_MESSAGE, listAppender.list.getFirst().getMessage());
        verifyNoInteractions(inputStreamMock);
    }

    private void initLoggerAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(SingleExecCallbackEofWatcher.class);
        listAppender = new ListAppender<>();
        logger.addAppender(listAppender);
        listAppender.start();
    }
}