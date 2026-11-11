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

import lombok.NonNull;
import com.jfrog.ph.acl.virtual.resolution.PackageRequestContext;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.cache.expirable.CacheExpiryChecker;

import java.util.Map;

/**
 * @author Yoav Landman
 */
public interface InternalRequestContext extends RequestContext, PackageRequestContext {

    /**
     * Force resource expiry check (regardless of the resource cache age), usually triggered by a user plugin
     * to add expired resources others than those who implements CacheExpirable.
     *
     * @return True if should force expiry check
     * @see CacheExpiryChecker
     * @see org.artifactory.addon.plugin.download.BeforeDownloadRequestAction
     */
    boolean isForceExpiryCheck();

    @Override
    default @NonNull Map<String, String> getRequestHeaders() {
        Request request = getRequest();
        if (request != null) {
            return request.getHeaders();
        }
        return PackageRequestContext.super.getRequestHeaders();
    }

    /**
     * Checks if a cached RepoResource can be unexpired based on given conditions.
     */
    default boolean shouldUnExpireCachedResource(RepoResource resource) {
        return false;
    }
}
