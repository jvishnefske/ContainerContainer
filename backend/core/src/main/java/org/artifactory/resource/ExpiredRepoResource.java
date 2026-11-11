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

package org.artifactory.resource;

import org.apache.http.HttpStatus;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;

/**
 * @author Fred Simon
 */
public class ExpiredRepoResource implements RepoResource, UnfoundRepoResourceReason {

    private RepoResource wrappedResource;

    public ExpiredRepoResource(RepoResource wrappedResource) {
        this.wrappedResource = wrappedResource;
    }

    @Override
    public String getOriginRemotePath() {
        return wrappedResource.getOriginRemotePath();
    }

    @Override
    public int getStatusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }

    @Override
    public String getDetail() {
        return "Resource has expired";
    }

    @Override
    public RepoPath getRepoPath() {
        return wrappedResource.getRepoPath();
    }

    @Override
    public RepoPath getResponseRepoPath() {
        return wrappedResource.getResponseRepoPath();
    }

    @Override
    public void setResponseRepoPath(RepoPath responsePath) {
        wrappedResource.setResponseRepoPath(responsePath);
    }

    @Override
    public RepoResourceInfo getInfo() {
        return wrappedResource.getInfo();
    }

    @Override
    public long getSize() {
        return wrappedResource.getSize();
    }

    @Override
    public long getCacheAge() {
        return wrappedResource.getCacheAge();
    }

    @Override
    public long getLastModified() {
        return wrappedResource.getLastModified();
    }

    @Override
    public String getEtag() {
        return wrappedResource.getEtag();
    }

    @Override
    public String getMimeType() {
        return wrappedResource.getMimeType();
    }

    @Override
    public boolean isFound() {
        return false;
    }

    @Override
    public boolean isBlocked() {
        return false;
    }

    @Override
    public boolean isExactQueryMatch() {
        return wrappedResource.isExactQueryMatch();
    }

    @Override
    public String getRepoKey() {
        return wrappedResource.getRepoKey();
    }

    @Override
    public String getPath() {
        return wrappedResource.getPath();
    }

    @Override
    public boolean isExpired() {
        return true;
    }

    @Override
    public boolean isMetadata() {
        return wrappedResource.isMetadata();
    }

    @Override
    public boolean isExpirable() {
        return true;
    }

    @Override
    public void expirable() {
    }

    @Override
    public String toString() {
        return wrappedResource.getRepoPath().toString();
    }

    @Override
    public Reason getReason() {
        return Reason.EXPIRED;
    }

    /**
     * This is used to check information on the found resource itself. Such as if the resource is found or expired.
     * Those values are hardcoded in the ExpiredResource. Use this when you want to find information on the resource
     * itself.
     * For example: If you want to know if the resource is found, the expired always will return false while the
     * cached one may return true.
     * @return the original resource
     */
    public RepoResource getWrappedResource() {
        return wrappedResource;
    }
}