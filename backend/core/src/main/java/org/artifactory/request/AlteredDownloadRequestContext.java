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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

import jakarta.annotation.Nonnull;
import java.util.function.Function;

/**
 * Implementation based on a download http request
 * Used for cases that the download path can be changed
 *
 * @author Nadav Yogev
 * @author Tomer Nir
 */
public class AlteredDownloadRequestContext extends DownloadRequestContext {

    private final Function<CloseableHttpResponse, String> overrideFunction;
    private final Function<RepoResource, Boolean> shouldUnExpireFunc;
    private RepoPath alteredPath;

    public AlteredDownloadRequestContext(@Nonnull ArtifactoryRequest artifactoryRequest, boolean expired,
            Function<CloseableHttpResponse, String> overrideFunction,
            Function<RepoResource, Boolean> shouldUnExpireFunc) {
        super(artifactoryRequest, expired);
        this.overrideFunction = overrideFunction;
        this.shouldUnExpireFunc = shouldUnExpireFunc;
        alteredPath = artifactoryRequest.getRepoPath();
    }

    @Override
    public String getResourcePath() {
        return alteredPath.getPath();
    }

    @Nonnull
    @Override
    public RepoPath alterPath(CloseableHttpResponse response, RepoPath repoPath) {
        alteredPath = RepoPathFactory.create(repoPath.getRepoKey(), overrideFunction.apply(response));
        return alteredPath;
    }

    @Override
    public boolean shouldUnExpireCachedResource(RepoResource resource) {
        return shouldUnExpireFunc.apply(resource);
    }
}
