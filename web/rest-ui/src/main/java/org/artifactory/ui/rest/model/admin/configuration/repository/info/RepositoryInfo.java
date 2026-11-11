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

package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.common.repo.RepoType;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.util.JsonUtil;

import java.util.Set;

/**
 * @author Aviad Shikloshi
 */
@Data
@NoArgsConstructor
public abstract class RepositoryInfo implements RestModel {

    protected String repoKey;
    protected String repoType;
    protected Boolean hasReindexAction;
    protected String projectKey;
    protected String projectName;
    protected Set<String> environments;
    protected Set<String> sharedWithProjects;
    protected boolean shareWithAllProjects;
    protected boolean isSharedReadOnly;
    protected boolean isTarget;
    protected Boolean hasMigrationAction;
    protected Boolean federated;

    RepositoryInfo(RepoConfig repoConfig, PackageSpecification packageSpecification,
            RepoType repoType) {
        this.repoKey = repoConfig.getKey();
        this.repoType = repoConfig.getPackageType().toString();
        this.hasReindexAction = packageSpecification.hasReindex(repoType, repoConfig.getPackageType());
        this.hasMigrationAction = packageSpecification.hasMigrationAction(repoConfig);
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }


}
