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

package org.artifactory.repo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.artifactory.fs.RepoResource;
import org.artifactory.io.RemoteResourceStreamHandle;
import org.artifactory.md.Properties;
import org.artifactory.resource.ResourceStreamHandle;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;

import java.io.InputStream;
import java.util.Map;

/**
 * @author Noam Y. Tenne
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SaveResourceContext {

    private final RepoResource repoResource;
    @Getter(AccessLevel.NONE)
    private final ResourceStreamHandle handle;
    private final InputStream inputStream;
    private final Properties properties;
    private final long created;
    private final String createBy;
    private final String modifiedBy;
    private final String contentLength; // actual content length. do not use for encoded content length
    /**
     * Optional existing binary info. If this field is not null, the content is already in the binary store and there's
     * no need to re-read the input stream.
     */
    private final BinaryElement binaryInfo;

    private final Map<String, String> preCalculatedHeaders;

    public Map<String, String> getPreCalculatedHeaders() {
        return preCalculatedHeaders != null ? preCalculatedHeaders : Map.of();
    }

    public void setException(Throwable throwable) {
        if (handle instanceof RemoteResourceStreamHandle) {
            ((RemoteResourceStreamHandle) handle).setThrowable(throwable);
        }
    }

    public static class Builder {

        private RepoResource repoResource;
        private InputStream inputStream;
        private Properties properties;
        private long created;
        private String contentLength;
        private String createBy;
        private String modifiedBy;
        private ResourceStreamHandle handle;
        private BinaryElement binaryInfo;
        private Map<String, String> preCalculatedHeaders;

        public Builder(RepoResource repoResource, ResourceStreamHandle handle) {
            this(repoResource, handle.getInputStream());
            this.handle = handle;
        }

        public Builder(RepoResource repoResource, InputStream inputStream) {
            this.repoResource = repoResource;
            this.inputStream = inputStream;
        }

        public Builder(SaveResourceContext other) {
            repoResource = other.repoResource;
            inputStream = other.inputStream;
            handle = other.handle;
            properties = other.properties;
            createBy = other.createBy;
            created = other.created;
            contentLength = other.contentLength;
            modifiedBy = other.modifiedBy;
            binaryInfo = other.binaryInfo;
            preCalculatedHeaders = other.preCalculatedHeaders;
        }

        public Builder properties(Properties properties) {
            this.properties = properties;
            return this;
        }

        public Builder created(long created) {
            this.created = created;
            return this;
        }

        public Builder contentLength(String contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        public Builder createdBy(String createBy) {
            this.createBy = createBy;
            return this;
        }

        public Builder modifiedBy(String modifiedBy) {
            this.modifiedBy = modifiedBy;
            return this;
        }

        public Builder binaryInfo(BinaryElement binaryInfo) {
            this.binaryInfo = binaryInfo;
            if (binaryInfo.getLength() > 0) {
                contentLength = String.valueOf(binaryInfo.getLength());
            }
            return this;
        }

        public Builder preCalculatedHeaders(Map<String, String> preCalculatedHeaders) {
            this.preCalculatedHeaders = preCalculatedHeaders;
            return this;
        }

        public SaveResourceContext build() {
            return new SaveResourceContext(repoResource, handle, inputStream, properties, created, createBy,
                    modifiedBy, contentLength, binaryInfo, preCalculatedHeaders);
        }
    }
}
