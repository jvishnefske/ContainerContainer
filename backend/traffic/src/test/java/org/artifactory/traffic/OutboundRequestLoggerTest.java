package org.artifactory.traffic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class OutboundRequestLoggerTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Logger logger = (Logger) LoggerFactory.getLogger(OutboundRequestLogger.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockAppender);
        homeStub.setProperty(ConstantValues.remoteRepoRequestAdditionalLogFieldsEnabled.getPropertyName(), "true");
    }

    @Test
    void testLog() {
        OutboundRequestLogger.log("repo1", "user1", "GET", "http://example.com", 200,
                100L, 200L, 300L, true);
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        String logMessage = loggingEvent.getFormattedMessage();
        assertTrue(logMessage.contains("repo1|user1|GET|http://example.com|200|100|200|300"));
    }

    @Test
    void testLogFailingRequestWithoutResponse() throws Exception {
        homeStub.setProperty(ConstantValues.remoteRepoRequestAdditionalLogFieldsEnabled.getPropertyName(), "true");
        HttpRequestBase mockRequest = mock(HttpRequestBase.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getURI()).thenReturn(new java.net.URI("http://example.com"));
        Exception exception = new ConnectTimeoutException();
        OutboundRequestLogger.logFailingRequestWithoutResponse("repo1", "user1", mockRequest,
                System.currentTimeMillis(), true, exception);
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        String logMessage = loggingEvent.getFormattedMessage();
        assertTrue(logMessage.contains("repo1|user1|GET|http://example.com|408|0|0"));
        var actualMetadata = loggingEvent.getArgumentArray()[10];
        assertNotNull(actualMetadata);
        var metadataMap = (Map<String, Object>) actualMetadata;
        assertEquals(true, metadataMap.get("auth"));
        assertEquals("example.com", metadataMap.get("host"));
    }

    @Test
    void testLogFailingRequestWithoutResponseWithSocketTimeoutException() throws Exception {
        HttpRequestBase mockRequest = mock(HttpRequestBase.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getURI()).thenReturn(new java.net.URI("http://example.com"));
        Exception exception = new SocketTimeoutException();
        OutboundRequestLogger.logFailingRequestWithoutResponse("repo1", "user1", mockRequest,
                System.currentTimeMillis(), true, exception);
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        String logMessage = loggingEvent.getFormattedMessage();
        assertTrue(logMessage.contains("repo1|user1|GET|http://example.com|408|0|0"));
    }

    @Test
    void testLogFailingRequestWithoutResponseWithOtherException() throws Exception {
        HttpRequestBase mockRequest = mock(HttpRequestBase.class);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getURI()).thenReturn(new java.net.URI("http://example.com"));
        Exception exception = new Exception();
        OutboundRequestLogger.logFailingRequestWithoutResponse("repo1", "user1", mockRequest,
                System.currentTimeMillis(), true, exception);
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        String logMessage = loggingEvent.getFormattedMessage();
        assertTrue(logMessage.contains("repo1|user1|GET|http://example.com|500|0|0"));
    }

    @Test
    void testMetadataLogged() {
        homeStub.setProperty(ConstantValues.remoteRepoRequestAdditionalLogFieldsEnabled.getPropertyName(), "true");
        OutboundRequestLogger.log("repo1", "user1", "GET", "http://example.com:8080/artifactory/repo", 200,
                100L, 200L, 300L, true);
        verify(mockAppender).doAppend(captorLoggingEvent.capture());
        ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        var metadata = loggingEvent.getArgumentArray()[10];
        assertNotNull(metadata);
        var metadataMap = (Map<String, Object>) metadata;
        assertEquals(true, metadataMap.get("auth"));
        assertEquals("example.com", metadataMap.get("host"));
    }

    @Test
    void testMetadataNotLoggedWhenPropertyDisabled() {
        homeStub.setProperty(ConstantValues.remoteRepoRequestAdditionalLogFieldsEnabled.getPropertyName(), "false");
        OutboundRequestLogger.log("repo1", "user1", "GET", "http://example.com:8080/artifactory/repo", 200,
                100L, 200L, 300L, true);
        verify(mockAppender).doAppend(captorLoggingEvent.capture());
        ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        assertNull(loggingEvent.getArgumentArray()[10]);
    }
}