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

package org.artifactory.api.request;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CountingInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.io.output.NullWriter;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.rest.exception.ExpiredRestException;
import org.artifactory.rest.exception.RestException;
import org.artifactory.rest.resource.ArtifactoryResourceInfo;
import org.artifactory.util.BasicHttpUtils;
import org.codehaus.plexus.util.StringUtils;

import jakarta.annotation.Nullable;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * @author Shay Yaakov
 */
@Slf4j
public class JerseyArtifactoryResponse extends ArtifactoryResponseBase {

    private final boolean debug;

    private Response.ResponseBuilder response;
    // reference ensuring we will consume the stream. In case of leak, printing this exception will reveal the
    //originator
    final private Exception createdTraceException;
    volatile MustBeConsumed mustConsume = null;
    // just for tests
    volatile private Stats stats = null;
    private final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public boolean usingDetailedDebug() {
        return debug || log.isTraceEnabled();
    }

    public JerseyArtifactoryResponse() {
        this(false);
    }

    public JerseyArtifactoryResponse(boolean debug) {
        this.debug = debug;
        response = Response.ok();
        // For log.trace debug leaks - store stack of created exception.
        if (usingDetailedDebug()) {
            createdTraceException = new RuntimeException("Created stacktrace");
        } else {
            createdTraceException = null;
        }
    }

    @Override
    public void sendStream(CountingInputStream is, LongConsumer callback) throws IOException {
        this.mustConsume = new MustBeConsumedWithCallback(is, callback);
        response.status(getStatus()).entity(this.mustConsume);
    }

    /**
     * Method used to provide the stream for Jersey.
     *
     * @throws IOException
     */
    @Override
    public void sendStream(final InputStream is) throws IOException {
        this.mustConsume = new MustBeConsumed(is);
        response.status(getStatus()).entity(this.mustConsume);
    }

    /**
     * Consume stream
     */
    private void consumeStream(boolean consumed) throws IOException {
        InputStream is = mustConsume != null ? mustConsume.is : null;
        if (is != null) {
            Stats lstats = stats;
            try {
                if (is.available() != 0) {
                    if (lstats != null) {
                        lstats.unconsumed();
                    }
                    log.debug("Close encountered consumed = {} with {} bytes - consume and close stream.", consumed,
                            is.available());
                    if (usingDetailedDebug()) {
                        if (createdTraceException != null) {
                            log.trace("Found unconsumed  {} bytes created by : ", is.available(),
                                    createdTraceException);
                        }
                    }
                    IOUtils.copy(is, OutputStream.nullOutputStream());
                }
            } finally {
                IOUtils.closeQuietly(is);
            }

            if (lstats != null) {
                lstats.closed();
            }
            mustConsume = null;
        }
    }

    // DO NOT MAKE it STATIC - we want it to hold the object and finalize

    /**
     * MustBeConsumed created when sendStream is called and prevents the finalize of the parent class.
     * When the stream is not used by jersey/entity the finalize would make sure any leftover is closed and consumed
     * A wrapper that actually copy the stream back to back when consumed (get entity)
     */
    class MustBeConsumed<TInputStream extends InputStream> implements StreamingOutput, Closeable {
        protected TInputStream is;
        private boolean consumed = false;

        MustBeConsumed(TInputStream is) {
            this.is = is;
        }

        @Override
        public void write(OutputStream output) throws IOException {
            try {
                IOUtils.copy(is, output);
            } finally {
                close();
            }
        }

        @Override
        public void close() throws IOException {
            if (is != null) {
                consumeStream(consumed);
            }
        }
    }

    class MustBeConsumedWithCallback extends MustBeConsumed<CountingInputStream> {

        private LongConsumer callback;

        MustBeConsumedWithCallback(CountingInputStream is, LongConsumer callback) {
            super(is);
            this.callback = callback;
        }

        @Override
        public void write(OutputStream output) throws IOException {
            long byteCount = 0;
            try {
                try {
                    IOUtils.copy(is, output);
                } finally {
                    byteCount = is.getByteCount();
                    super.close();
                }
            } finally {
                callback.accept(byteCount);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        checkWarnUnconsumedStream();
        IOUtils.closeQuietly(mustConsume);
        super.finalize();
    }

    /**
     * Method reporting
     */
    private void checkWarnUnconsumedStream() {
        if (mustConsume != null) {
            Stats lstats = stats;
            if (lstats != null) {
                lstats.unconsumedFinalized();
            }
            String msg = getClass().getName() + "Method finalize caught unconsumed stream by Jersey.";
            log.info(msg);
            if (usingDetailedDebug()) {
                log.trace(msg, createdTraceException);
            }
        }
    }


    /**
     * Delegates writing to this response's output stream to the {@link Consumer} given as {@param delegate}
     */
    public void sendStreamWithDelegation(Consumer<OutputStream> delegate) {
        response.status(getStatus()).entity((StreamingOutput) delegate::accept);
    }

    @Override
    protected void sendErrorInternalWithResource(int code, String reason,
            @Nullable ArtifactoryResourceInfo resourceInfo) throws IOException {
        if (resourceInfo != null && resourceInfo.isExpired()) {
            throw new ExpiredRestException(code, reason, headers);
        }
        sendErrorInternal(code, reason);
    }

    @Override
    protected void sendErrorInternal(int code, String reason) throws IOException {
        throw new RestException(code, reason, headers);
    }

    @Override
    public void setLastModified(long lastModified) {
        response.lastModified(new Date(lastModified));
    }

    @Override
    public void setContentLength(long length) {
        super.setContentLength(length);
        setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
    }

    @Override
    public void setEtag(String etag) {
        if (etag != null) {
            response.header("ETag", etag);
        } else {
            log.debug("Could not register a null etag with the response.");
        }
    }

    @Override
    public void setSha1(String sha1) {
        if (sha1 != null) {
            response.header(ArtifactoryHeaders.CHECKSUM_SHA1, sha1);
        } else {
            log.debug("Could not register a null sha1 tag with the response.");
        }
    }

    @Override
    public void setSha2(String sha2) {
        if (sha2 != null) {
            response.header(ArtifactoryHeaders.CHECKSUM_SHA256, sha2);
        } else {
            log.debug("Could not register a null sha256 tag with the response.");
        }
    }

    @Override
    public void setMd5(String md5) {
        if (md5 != null) {
            response.header(ArtifactoryHeaders.CHECKSUM_MD5, md5);
        } else {
            log.debug("Could not register a null md5 tag with the response.");
        }
    }

    @Override
    public void setRangeSupport(String rangeSupport) {
        if (rangeSupport != null) {
            response.header(ArtifactoryHeaders.ACCEPT_RANGES, rangeSupport);
        } else {
            log.debug("Could not register a null range support tag with the response.");
        }
    }

    @Override
    public void setContentType(String contentType) {
        response.type(contentType);
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new NullOutputStream();
    }

    @Override
    public void setContentDispositionAttachment(String filename) {
        if (StringUtils.isBlank(filename)) {
            response.header("Content-Disposition", "attachment");
        } else {
            response.header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        }
    }

    @Override
    public void setFilename(String filename) {
        if (StringUtils.isNotBlank(filename)) {
            response.header(ArtifactoryHeaders.FILE_NAME, BasicHttpUtils.encodeQuery(filename));
        } else {
            log.debug("Could not register a null filename with the response.");
        }
    }

    @Override
    public Writer getWriter() throws IOException {
        return new NullWriter();
    }

    @Override
    public void setStatus(int status) {
        super.setStatus(status);
        response.status(status);
    }

    @Override
    public void setHeader(String header, String value) {
        response.header(header, value);
        headers.put(header, value);
    }

    @Override
    public void addHeader(final String header, final String value) {
        setHeader(header, value);
    }

    @Override
    public void addAllHeaders(final String header, final List<String> values) {
        log.trace("AddAllHeaders called on JerseyArtifactoryResponse. Empty implementation.");
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Override
    public void flush() {
        log.trace("Flush called on JerseyArtifactoryResponse. Empty implementation.");
    }

    @Override
    public void sendAuthorizationRequired(String message, String realm) throws IOException {
        throw new AuthorizationRestException();
    }

    @Override
    public void close(Closeable closeable) {
        // NOP, we use Jersey's StreamingOutput so cannot close here
        // done by finalizer
        // temporary till we fix the Download service head treatment.
        if (mustConsume == null) {
            try {
                closeable.close();
            } catch (IOException e) {
                log.error("Could not close closeable", e);
            }
        }
    }

    public Response build() {
        return response.build();
    }


    /* =============================
     * Methods Just for tests.
     *
     */


    /**
     * for tests
     */
    public void setStatsForTests(Stats stats) {
        this.stats = stats;
    }

    /**
     * Used in tests
     */
    public boolean isClosed() {
        return mustConsume == null || mustConsume.is == null;
    }

    /**
     * used in ArtifactoryJerseyResponseTest and for dummy responses.
     */
    public void release() {
        try {
            if (this.mustConsume != null) {
                this.mustConsume.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class Stats {
        public int cntClosed;
        public int cntUnconsumed;
        public int cntFinalizeUnconsumed;

        public void closed() {
            cntClosed++;
        }

        public void unconsumed() {
            cntUnconsumed++;
        }

        public void unconsumedFinalized() {
            cntFinalizeUnconsumed++;
        }
    }
}
