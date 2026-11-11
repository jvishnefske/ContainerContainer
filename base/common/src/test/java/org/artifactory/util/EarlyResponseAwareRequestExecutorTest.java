package org.artifactory.util;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EarlyResponseAwareRequestExecutorTest {

    private static final HttpResponse RESPONSE_200 = new BasicHttpResponse(
            new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
    private static final HttpResponse RESPONSE_413 = new BasicHttpResponse(
            new BasicStatusLine(HttpVersion.HTTP_1_1, 413, "Request Entity Too Large"));

    private EarlyResponseAwareRequestExecutor executor;
    private HttpClientConnection conn;
    private HttpContext context;

    @BeforeEach
    void setUp() {
        executor = new EarlyResponseAwareRequestExecutor();
        conn = mock(HttpClientConnection.class);
        context = new BasicHttpContext();
    }

    /**
     * POST with body — no IOException during upload.
     * execute() calls doSendRequest (returns null) then doReceiveResponse (returns the mocked 200).
     * Asserts the final response from execute() matches what the connection returned.
     */
    @Test
    void execute_postRequest_noException_returnsServerResponse() throws Exception {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/test");
        when(conn.receiveResponseHeader()).thenReturn(RESPONSE_200);

        HttpResponse result = executor.execute(request, conn, context);

        assertEquals(200, result.getStatusLine().getStatusCode());
        assertSame(RESPONSE_200, result);
        verify(conn).sendRequestEntity(request);
        verify(conn).receiveResponseHeader();
    }

    /**
     * GET request — no body to send.
     * execute() skips sendRequestEntity entirely and reads the response normally.
     */
    @Test
    void execute_getRequest_noEntitySent_returnsServerResponse() throws Exception {
        BasicHttpRequest getRequest = new BasicHttpRequest("GET", "/test");
        when(conn.receiveResponseHeader()).thenReturn(RESPONSE_200);

        HttpResponse result = executor.execute(getRequest, conn, context);

        assertEquals(200, result.getStatusLine().getStatusCode());
        assertSame(RESPONSE_200, result);
        verify(conn, never()).sendRequestEntity(any());
        verify(conn).receiveResponseHeader();
    }

    /**
     * IOException during body upload + response already in receive buffer
     * (e.g. server sent 413 and closed connection) → returns the early response instead of throwing.
     */
    @Test
    void doSendRequest_ioException_responseAvailable_returnsEarlyResponse() throws Exception {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/test");

        doThrow(new SocketException("Broken pipe")).when(conn).sendRequestEntity(request);
        when(conn.isResponseAvailable(0)).thenReturn(true);
        when(conn.receiveResponseHeader()).thenReturn(RESPONSE_413);

        HttpResponse result = executor.doSendRequest(request, conn, context);

        assertSame(RESPONSE_413, result);
        assertEquals(413, result.getStatusLine().getStatusCode());
    }

    /**
     * IOException during body upload + receive buffer is empty
     * (genuine network failure, no early response from server) → re-throws the original IOException.
     */
    @Test
    void doSendRequest_ioException_noResponseAvailable_rethrowsIoException() throws Exception {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/test");
        SocketException brokenPipe = new SocketException("Broken pipe");

        doThrow(brokenPipe).when(conn).sendRequestEntity(request);
        when(conn.isResponseAvailable(0)).thenReturn(false);

        IOException thrown = assertThrows(IOException.class,
                () -> executor.doSendRequest(request, conn, context));
        assertSame(brokenPipe, thrown);
        verify(conn, never()).receiveResponseHeader();
    }

    /**
     * Any IOException type (not just SocketException) is handled — the check is purely
     * based on isResponseAvailable(), not on the exception type.
     */
    @Test
    void doSendRequest_anyIoException_responseAvailable_returnsEarlyResponse() throws Exception {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", "/test");

        doThrow(new IOException("connection reset"))
                .when(conn).sendRequestEntity(request);
        when(conn.isResponseAvailable(0)).thenReturn(true);
        when(conn.receiveResponseHeader()).thenReturn(RESPONSE_413);

        HttpResponse result = executor.doSendRequest(request, conn, context);

        assertSame(RESPONSE_413, result);
        assertEquals(413, result.getStatusLine().getStatusCode());
    }
}
