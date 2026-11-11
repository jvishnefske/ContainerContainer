package org.artifactory.traffic;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.artifactory.common.ConstantValues;
import org.artifactory.traffic.entry.OutboundRequestEntry;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_REQUEST_TIMEOUT;

/**
 * Logger class for outbound requests made by Remote Repositories.
 *
 * @author Andrey Tashlitsky
 * @author Amir Avidan
 */
@Slf4j
@UtilityClass
public class OutboundRequestLogger {

    /**
     * Log the outbound requests made by the Remote Repositories to artifactory-request-out.log.
     */
    public void log(String repoName, String userName, String method, String url, int statusCode,
            long requestContentLength, long responseContentLength, long duration, boolean usesAuthentication) {
        OutboundRequestEntry outboundRequestEntry =
                createEntry(repoName, userName, method, url, statusCode, requestContentLength, responseContentLength,
                        duration, usesAuthentication);
        logEntry(outboundRequestEntry);
    }

    public void logFailingRequestWithoutResponse(String repoKey, String userName, HttpRequestBase request,
            long requestStartTime, boolean usesAuthentication, Exception exception) {
        long duration = System.currentTimeMillis() - requestStartTime;
        String method = request.getMethod();
        String uri = request.getURI().toString();
        int statusCode = getStatusCodeFromRequestWithoutResponse(exception);
        log(repoKey, userName, method, uri, statusCode, 0, 0, duration, usesAuthentication);
    }

    private OutboundRequestEntry createEntry(String repoName, String userName, String method, String url,
            int statusCode, long requestContentLength, long responseContentLength, long duration,
            boolean usesAuthentication) {
        OutboundRequestEntry.OutboundRequestEntryBuilder builder = OutboundRequestEntry.builder()
                .repoName(repoName)
                .userName(userName)
                .method(method)
                .url(url)
                .statusCode(statusCode)
                .requestContentLength(requestContentLength)
                .responseContentLength(responseContentLength)
                .duration(duration);
        if (ConstantValues.remoteRepoRequestAdditionalLogFieldsEnabled.getBoolean()) {
            builder.metadata(Map.of(
                    "auth", usesAuthentication,
                    "host", getHost(url)));
        }
        return builder.build();
    }

    private void logEntry(OutboundRequestEntry outboundRequestEntry) {
        // Note: ideally, we should use log.atLevel().addKeyValue("entry", entry) to pass data to logger.
        //       Unfortunately, features of Logback 2.0 are unavailable for Artifactory until we solve RTDEV-58341
        //       As a temporary solution, we will pass the dava as an extra argument.
        log.info("{}|{}|{}|{}|{}|{}|{}|{}|{}|{}",
                outboundRequestEntry.getFormattedDate(),
                outboundRequestEntry.getTraceId(),
                outboundRequestEntry.getRepoName(),
                outboundRequestEntry.getUserName(),
                outboundRequestEntry.getMethod(),
                outboundRequestEntry.getUrl(),
                outboundRequestEntry.getStatusCode(),
                outboundRequestEntry.getRequestContentLength(),
                outboundRequestEntry.getResponseContentLength(),
                outboundRequestEntry.getDuration(),
                outboundRequestEntry.getMetadata());
    }

    private int getStatusCodeFromRequestWithoutResponse(Exception exception) {
        return exception instanceof ConnectTimeoutException || exception instanceof SocketTimeoutException ?
                SC_REQUEST_TIMEOUT : SC_INTERNAL_SERVER_ERROR;
    }

    private String getHost(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() != null ? uri.getHost() : "";
        } catch (Exception e) {
            log.debug("Failed to parse host url", e);
            return "";
        }
    }
}
