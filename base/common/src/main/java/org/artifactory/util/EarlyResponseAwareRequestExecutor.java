package org.artifactory.util;

import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * An {@link HttpRequestExecutor} that handles the case where a server sends an early HTTP response
 * (e.g. 413 Request Entity Too Large) and closes the connection before the client has finished
 * uploading the request body.
 *
 * <p>In the default executor, an {@link IOException} (typically {@code Broken pipe}) thrown during
 * body upload causes the response to be lost and an opaque 500 to be returned to the caller.
 * This executor catches the {@link IOException}, checks whether a response is already available
 * in the socket receive buffer (non-blocking), and if so reads and returns it — preserving the
 * real status code (e.g. 413) for the caller to handle correctly.
 */
public class EarlyResponseAwareRequestExecutor extends HttpRequestExecutor {

    /**
     * Non-blocking poll: the early response is already in the OS receive buffer by the time
     * the write-side IOException fires, so no wait is needed.
     */
    public static final int NON_BLOCKING_POLL_MS = 0;

    private static final Logger log = LoggerFactory.getLogger(EarlyResponseAwareRequestExecutor.class);


    @Override
    protected HttpResponse doSendRequest(
            HttpRequest request, HttpClientConnection conn, HttpContext context)
            throws IOException, HttpException {

        conn.sendRequestHeader(request);
        conn.flush();

        if (!(request instanceof HttpEntityEnclosingRequest entityRequest)) {
            return null;
        }

        try {
            conn.sendRequestEntity(entityRequest);
            conn.flush();
        } catch (IOException e) {
            // Non-blocking check (0ms): if the server already sent an early response
            // (e.g. 413) it is already in the OS receive buffer — no need to wait.
            // The response is always written by the server before it closes the connection,
            // so if data is available it is the actual response, not a spurious read.
            // If the buffer is empty (genuine network failure), re-throw immediately.
            try {
                if (conn.isResponseAvailable(NON_BLOCKING_POLL_MS)) {
                    HttpResponse earlyResponse = conn.receiveResponseHeader();
                    if (canResponseHaveBody(request, earlyResponse)) {
                        conn.receiveResponseEntity(earlyResponse);
                    }
                    // Returning non-null skips doReceiveResponse() in execute()
                    return earlyResponse;
                }
            } catch (IOException e1) {
                log.debug("Failed to read early response after upload I/O failure", e1);
                // Recovery attempt failed — attach as suppressed so both
                // the original upload failure and the read failure are visible.
                e.addSuppressed(e1);
            }
            throw e;
        }
        return null;
    }
}
