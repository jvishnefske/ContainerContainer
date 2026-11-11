package org.artifactory.rest.interceptor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Andrey Tashlitsky
 */
class ClientAbortWriterInterceptorTest {

    private final ClientAbortWriterInterceptor clientAbortWriterInterceptor = new ClientAbortWriterInterceptor();
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(ClientAbortWriterInterceptor.class);
        listAppender = new ListAppender<>();
        logger.addAppender(listAppender);
        listAppender.start();
    }

    @Test
    void aroundWriteTo() throws Exception {
        WriterInterceptorContext writerInterceptorContext = mock(WriterInterceptorContext.class);
        doNothing().when(writerInterceptorContext).proceed();
        assertDoesNotThrow(() -> clientAbortWriterInterceptor.aroundWriteTo(writerInterceptorContext));
        verify(writerInterceptorContext, times(1)).proceed();
        assertFalse(listAppender.list.stream()
                .anyMatch(event -> event.getMessage().contains("The request was aborted by the client")));
    }

    @Test
    void aroundWriteToClientAbortException() throws Exception {
        WriterInterceptorContext writerInterceptorContext = mock(WriterInterceptorContext.class);
        doThrow(ClientAbortException.class).when(writerInterceptorContext).proceed();
        assertDoesNotThrow(() -> clientAbortWriterInterceptor.aroundWriteTo(writerInterceptorContext));
        verify(writerInterceptorContext, times(1)).proceed();
        assertTrue(listAppender.list.stream()
                .anyMatch(event -> event.getMessage().contains("The request was aborted by the client")));
    }

    @Test
    void aroundWriteToOtherException() throws Exception {
        WriterInterceptorContext writerInterceptorContext = mock(WriterInterceptorContext.class);
        doThrow(IOException.class).when(writerInterceptorContext).proceed();
        assertThrows(IOException.class, () -> clientAbortWriterInterceptor.aroundWriteTo(writerInterceptorContext));
        verify(writerInterceptorContext, times(1)).proceed();
        assertFalse(listAppender.list.stream()
                .anyMatch(event -> event.getMessage().contains("The request was aborted by the client")));
    }
}
