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

import org.apache.commons.io.input.CountingInputStream;
import org.artifactory.rest.resource.ArtifactoryResourceInfo;
import org.slf4j.Logger;

import jakarta.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * @author Fred Simon
 */
public interface ArtifactoryResponse {

    String ARTIFACTORY_ID = "X-Artifactory-Id";
    String ARTIFACTORY_TRACE_ID = "X-Artifactory-Trace-Id";

    boolean isError();

    void setLastModified(long lastModified);

    void setEtag(String etag);

    void setSha1(String sha1);

    void setSha2(String sha2);

    void setMd5(String md5);

    void setRangeSupport(String bytes);

    long getContentLength();

    void setContentLength(long length);

    void setContentType(String contentType);

    Writer getWriter() throws IOException;

    void sendInternalError(Exception exception, Logger logger) throws IOException;

    default void sendInternalError(Exception exception, Logger logger, @Nullable ArtifactoryResourceInfo resourceInfo)
            throws IOException {
        sendInternalError(exception, logger);
    }

    void sendError(int statusCode, String reason, Logger logger) throws IOException;

    void sendError(int statusCode, String message) throws IOException;

    default void sendError(int statusCode, String message, Logger logger,
            @Nullable ArtifactoryResourceInfo resourceInfo) throws IOException {
        sendError(statusCode, message, logger);
    }

    void sendStream(InputStream is) throws IOException;

    void sendStream(CountingInputStream is, LongConsumer callback) throws IOException;

    void sendSuccess();

    int getStatus();

    void setStatus(int statusCode);

    void setHeader(String header, String value);

    void setRedirect(String url);

    boolean isCommitted();

    boolean isSuccessful();

    boolean isSuccessfulOrRedirect();

    default void checksumDeployed() {

    }

    default boolean isChecksumDeployed() {
        return false;
    }

    void flush();

    void sendAuthorizationRequired(String message, String realm) throws IOException;

    boolean isPropertiesQuery();

    String getPropertiesMediaType();

    void setPropertiesMediaType(String propsQueryFormat);

    void close(Closeable closeable);

    default void addHeader(String header, String value) {

    }

    default void addAllHeaders(String header, List<String> values) {

    }

    default void setThirdPartyOriginHeader(String value) {
        setHeader(ArtifactoryHeaders.THIRD_PARTY_ORIGIN, value);
    }

    default void setXrayOriginHeader(String value) {
        setHeader(ArtifactoryHeaders.XRAY_ORIGIN, value);
    }

    default void setCurationOriginHeader(String value) {
        setHeader(ArtifactoryHeaders.CURATION_ORIGIN, value);
    }
}