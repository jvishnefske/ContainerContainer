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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.artifactory.fs.RepoResource;
import org.artifactory.repo.RepoPath;

import java.util.function.Function;


/**
 * An internal resource request with function that determines the path to download based on response parameters
 *
 * @author Nadav Yogev
 * @author Tomer Nir
 */
public class ConditionalInternalArtifactoryRequest extends InternalArtifactoryRequest {

    private final Function<CloseableHttpResponse, String> overridePath;
    private final Function<RepoResource, Boolean> shouldUnExpireFunc;

    public ConditionalInternalArtifactoryRequest(RepoPath repoPath,
            Function<CloseableHttpResponse, String> overridePath, Function<RepoResource, Boolean> shouldUnexpireFunc) {
        super(repoPath, false);
        this.overridePath = overridePath;
        this.shouldUnExpireFunc = shouldUnexpireFunc;
    }

    public Function<CloseableHttpResponse, String> getAlteredFunction() {
        return overridePath;
    }

    public Function<RepoResource, Boolean> getShouldUnExpireFunc() {
        return shouldUnExpireFunc;
    }
}
