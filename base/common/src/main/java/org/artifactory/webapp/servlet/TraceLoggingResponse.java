/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.webapp.servlet;

import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.NullWriter;
import org.apache.http.HttpStatus;
import org.artifactory.api.request.ArtifactoryResponse;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.springframework.http.MediaType;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

import static org.artifactory.util.BasicHttpUtils.isInSuccessOrRedirectResponseCodeRange;
import static org.artifactory.util.BasicHttpUtils.isSuccessfulResponseCode;

/**
 * A response wrapper that maintains but ignores the activities of the request process and collects the request trace
 * messages for later response. To be used in conjunction with {@link org.artifactory.request.RequestTraceLogger}
 *
 * @author Noam Y. Tenne
 */
public class TraceLoggingResponse implements ArtifactoryResponse {

    private final String threadName;
    private final String time;

    private long length = -1;
    private int statusCode;
    private ArtifactoryResponse artifactoryResponse;
    private List<String> logAggregator = Lists.newArrayList();
    private Map<String, String> requestHeaders;

    public TraceLoggingResponse(ArtifactoryResponse artifactoryResponse) {
        threadName = Thread.currentThread().getName();
        time = ISODateTimeFormat.dateTime().print(System.currentTimeMillis());
        this.artifactoryResponse = artifactoryResponse;
    }

    @Override
    public void sendStream(CountingInputStream is, LongConsumer callback) throws IOException {
        // empty
    }

    @Override
    public boolean isError() {
        return ((statusCode > 0) && !isSuccessfulResponseCode(statusCode));
    }

    @Override
    public void setLastModified(long lastModified) {
    }

    @Override
    public void setEtag(String etag) {
    }

    @Override
    public void setSha1(String sha1) {
    }

    @Override
    public void setSha2(String sha2) {

    }

    @Override
    public void setMd5(String md5) {
    }

    @Override
    public void setRangeSupport(String bytes) {
    }

    @Override
    public long getContentLength() {
        return length;
    }

    @Override
    public void setContentLength(long length) {
        this.length = length;
    }

    @Override
    public void setContentType(String contentType) {
    }

    @Override
    public Writer getWriter() throws IOException {
        return new NullWriter();
    }

    @Override
    public void sendInternalError(Exception exception, Logger logger) throws IOException {
        // TODO: [by fsi] Trace should have heavy tracing on error
    }

    @Override
    public void sendError(int statusCode, String reason, Logger logger) throws IOException {
        this.statusCode = statusCode;
    }

    @Override
    public void sendError(int statusCode, String message) throws IOException {
        this.statusCode = statusCode;
    }

    @Override
    public void sendStream(InputStream is) throws IOException {
        if (statusCode <= 0) {
            statusCode = HttpStatus.SC_OK;
        }
    }

    @Override
    public void sendSuccess() {
        this.statusCode = HttpStatus.SC_OK;
    }

    @Override
    public int getStatus() {
        return statusCode;
    }

    @Override
    public void setStatus(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public void setHeader(String header, String value) {
        artifactoryResponse.setHeader(header, value);
    }

    @Override
    public void setRedirect(String url) {

    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public boolean isSuccessful() {
        return ((statusCode <= 0) || isSuccessfulResponseCode(statusCode));
    }

    @Override
    public boolean isSuccessfulOrRedirect() {
        return ((statusCode <= 0) || isInSuccessOrRedirectResponseCodeRange(statusCode));
    }

    @Override
    public boolean isPropertiesQuery() {
        return false;
    }

    @Override
    public String getPropertiesMediaType() {
        return null;
    }

    @Override
    public void setPropertiesMediaType(String propsQueryFormat) {
    }

    @Override
    public void close(Closeable closeable) {
        IOUtils.closeQuietly(closeable);
    }

    @Override
    public void flush() {
    }

    @Override
    public void sendAuthorizationRequired(String message, String realm) throws IOException {
        statusCode = HttpStatus.SC_UNAUTHORIZED;
    }

    /**
     * Appends a request trace log message
     *
     * @param message Message to log
     */
    public void log(String message) {
        logAggregator.add(message);
    }

    /**
     * Sets the whitelisted inbound request headers to include in the trace metadata block. Package-private so only
     * the request-scoped caller in this package ({@code RepoFilter}) can populate it, keeping the whitelist the sole
     * entry point. A defensive, unmodifiable copy is stored so the trace output cannot be mutated after being set.
     *
     * @param requestHeaders Ordered map of header name to value (only headers that were present)
     */
    void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(requestHeaders));
    }

    /**
     * Strips CR/LF characters from a header value so a crafted upstream proxy header cannot inject extra lines
     * into the trace output (response/log line injection).
     *
     * @param value Raw header value
     * @return The value with any carriage-return / line-feed characters replaced by a single space
     */
    private static String sanitizeHeaderValue(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ");
    }

    /**
     * Writes the request info and messages to the response
     *
     * @param requestId   Request trace context ID
     * @param methodName  HTTP method name
     * @param username    Authenticated user name
     * @param requestPath Request repo path id
     * @throws IOException
     */
    public void sendResponse(String requestId, String methodName, String username, String requestPath)
            throws IOException {
        Writer writer = null;
        try {
            artifactoryResponse.setContentType(MediaType.TEXT_PLAIN.toString());
            writer = artifactoryResponse.getWriter();
            writer.append("Request ID: ").append(requestId).append("\n");
            writer.append("Repo Path ID: ").append(requestPath).append("\n");
            writer.append("Method Name: ").append(methodName).append("\n");
            writer.append("User: ").append(username).append("\n");
            if (requestHeaders != null && !requestHeaders.isEmpty()) {
                writer.append("Headers:\n");
                for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                    writer.append("  ").append(header.getKey()).append(": ")
                            .append(sanitizeHeaderValue(header.getValue())).append("\n");
                }
            }
            writer.append("Time: ").append(time).append("\n");
            writer.append("Thread: ").append(threadName).append("\n");
            writer.flush();
            writer.append("Steps: ").append("\n");
            IOUtils.writeLines(logAggregator, null, writer);
            writer.flush();
            artifactoryResponse.sendSuccess();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
