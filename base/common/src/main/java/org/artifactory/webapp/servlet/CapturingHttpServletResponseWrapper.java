package org.artifactory.webapp.servlet;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.artifactory.request.CountingServletOutputStream;
import org.artifactory.util.HttpUtils;

import java.io.IOException;
import java.util.function.LongSupplier;

/**
 * A custom response wrapper the helps capture the return code, content length and response length.
 */
@Slf4j
public class CapturingHttpServletResponseWrapper extends HttpServletResponseWrapper {
    private int status;
    private long contentLength;
    @Getter
    private Long timeToSetStatus;
    private CountingServletOutputStream countingStream;
    private final LongSupplier currentTimeSupplier;

    /**
     * Constructs a response adaptor wrapping the given response.
     *
     * @throws IllegalArgumentException if the response is null
     */
    CapturingHttpServletResponseWrapper(HttpServletResponse response, LongSupplier currentTimeSupplier) {
        super(response);
        this.status = 200;
        this.currentTimeSupplier = currentTimeSupplier;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (countingStream == null) {
            countingStream = new CountingServletOutputStream(getResponse().getOutputStream());
        }
        return countingStream;
    }

    public long getContentLength() {
        return contentLength > 0 ? contentLength : getByteCount();
    }

    private long getByteCount() {
        return countingStream == null ? 0 : countingStream.getByteCount();
    }

    @Override
    public int getStatus() {
        return status;
    }

    public int getStatusFromCommitedResponse() {
        return super.getStatus();
    }

    @Override
    public void setStatus(int sc) {
        if (notCommitted(sc)) {
            updateStatus(sc);
            super.setStatus(sc);
        }
    }

    void updateStatus(int sc) {
        this.status = sc;
        if (this.timeToSetStatus == null) {
            this.timeToSetStatus = this.currentTimeSupplier.getAsLong();
        }
    }

    /**
     * Records the status code and timestamp for metrics purposes, without setting the status
     * on the underlying response. This is used when an exception is caught, and we need to
     * track the error status for metrics, while allowing a filter higher in the chain to
     * send the actual error response.
     */
    public void recordStatusForMetrics(int statusCode) {
        updateStatus(statusCode);
    }

    @Override
    public void setContentLength(int len) {
        contentLength = len;
        super.setContentLength(len);
    }

    @Override
    public void addHeader(String name, String value) {
        super.addHeader(name, value);
        captureString(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        super.addIntHeader(name, value);
        captureInt(name, value);
    }

    @Override
    public void setHeader(String name, String value) {
        super.setHeader(name, value);
        captureString(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        super.setIntHeader(name, value);
        captureInt(name, value);
    }

    @Override
    public void sendError(int sc) throws IOException {
        if (notCommitted(sc)) {
            updateStatus(sc);
            HttpUtils.sendErrorResponse((HttpServletResponse) getResponse(), sc, null);
        }
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        if (notCommitted(sc, msg)) {
            updateStatus(sc);
            if (status == SC_NOT_FOUND) {
                msg = "Not Found";
            }
            HttpUtils.sendErrorResponse((HttpServletResponse) getResponse(), sc, msg);
        }
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        updateStatus(SC_FOUND);
        super.sendRedirect(location);
    }

    private boolean notCommitted(int status) {
        return notCommitted(status, null);
    }

    private boolean notCommitted(int status, String reason) {
        if (isCommitted()) {
            log.debug("Cannot change status " + status + (reason != null ? " (" + reason + ")" : "") + ": " +
                    "response already committed.");
            return false;
        }
        return true;
    }

    private void captureString(String name, String value) {
        if (name.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) && StringUtils.isNumeric(value)) {
            contentLength = Long.parseLong(value);
        }
    }

    private void captureInt(String name, int value) {
        captureString(name, value + "");
    }
}