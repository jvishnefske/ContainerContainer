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

package org.artifactory.request;

import com.google.common.collect.Maps;
import jakarta.annotation.Nonnull;

import java.util.Map;

/**
 * @author Noam Y. Tenne
 */
public abstract class BaseRequestContext implements InternalRequestContext {

    protected final ArtifactoryRequest request;

    /**
     * Generic attributes which can be added and queried, this should not be mistaken with
     * request properties which are the matrix params of the request, the attributes are
     * essentially meant for internal usage.
     */
    protected Map<String, String> attributes;

    protected boolean skipCurationOnResource = true;

    protected BaseRequestContext(@Nonnull ArtifactoryRequest request) {
        this.request = request;
    }

    @Override
    public String getServletContextUrl() {
        return request.getServletContextUrl();
    }

    @Override
    public String getZipResourcePath() {
        return request.getZipResourcePath();
    }

    @Override
    @Nonnull
    public ArtifactoryRequest getRequest() {
        return request;
    }

    @Override
    public void setAttribute(String name, String value) {
        if (attributes == null) {
            attributes = Maps.newHashMap();
        }
        attributes.put(name, value);
    }

    @Override
    public String getAttribute(String name) {
        if (attributes == null) {
            return null;
        }
        return attributes.get(name);
    }

    @Override
    public boolean isSkipCurationOnResource() {
        return skipCurationOnResource;
    }

    @Override
    public void setSkipCurationOnResource(boolean skipCurationOnResource) {
        this.skipCurationOnResource = skipCurationOnResource;
    }
}
