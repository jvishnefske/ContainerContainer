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

package org.artifactory.aol.store;

import org.apache.http.auth.AuthenticationException;
import org.artifactory.aol.store.response.StoreResponse;

import java.io.IOException;

/**
 * Connecting to Store in case of aol
 *
 * @author Asaf Zalcman
 */
public interface StoreService {

    StoreResponse createDockerRepository(String serverName, String dockerApiVersion, String repoName);

    StoreResponse deleteDockerRepository(String serverName, String dockerApiVersion, String repoName);

    StoreResponse getPurchaseURL(String serverName) throws IOException, AuthenticationException;
}
