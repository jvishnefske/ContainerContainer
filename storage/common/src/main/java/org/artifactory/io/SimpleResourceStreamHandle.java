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

package org.artifactory.io;

import org.apache.commons.io.IOUtils;
import org.artifactory.checksum.ChecksumsInfo;
import org.artifactory.resource.ResourceWithChecksumStreamHandle;

import java.io.InputStream;
import java.util.Optional;

/**
 * @author Yoav Landman
 */
public class SimpleResourceStreamHandle implements ResourceWithChecksumStreamHandle {
    private final InputStream is;
    private final long size;
    private final ChecksumsInfo checksumsInfo;

    public SimpleResourceStreamHandle(InputStream is) {
        this(is, -1);
    }

    public SimpleResourceStreamHandle(InputStream is, long size) {
        this(is, size, null);
    }

    public SimpleResourceStreamHandle(InputStream is, long size, ChecksumsInfo checksumsInfo) {
        this.is = is;
        this.size = size;
        this.checksumsInfo = checksumsInfo;
    }

    @Override
    public InputStream getInputStream() {
        return is;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(is);
    }

    @Override
    public Optional<ChecksumsInfo> getChecksumsInfo() {
        return Optional.ofNullable(checksumsInfo);
    }
}