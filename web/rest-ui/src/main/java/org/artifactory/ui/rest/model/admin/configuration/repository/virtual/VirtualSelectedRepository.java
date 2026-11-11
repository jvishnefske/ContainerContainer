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

package org.artifactory.ui.rest.model.admin.configuration.repository.virtual;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

/**
 * @author Shay Yaakov
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
public class VirtualSelectedRepository {

    private String repoName;
    private String type; // local/federated/remote/virtual

    public VirtualSelectedRepository(RepoConfig repoConfig) {
        this.repoName = repoConfig.getKey();
        if (repoConfig instanceof FederatedRepoConfig) {
            type = FEDERATED.typeNameLowercase();
        } else if (repoConfig instanceof LocalRepoConfig) {
            this.type = LOCAL.typeNameLowercase();
        } else if (repoConfig instanceof RemoteRepoConfig) {
            this.type = REMOTE.typeNameLowercase();
        } else {
            this.type = VIRTUAL.typeNameLowercase();
        }
    }
}
