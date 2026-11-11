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

package org.artifactory.ui.rest.model.admin.configuration.repository.typespecific;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.vcs.VcsGitProvider;
import org.artifactory.rest.common.exception.RepoConfigException;

import java.util.List;

import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_GO_METADATA_URLS;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_GO_REMOTE_METADATA_MISSED_RETRIEVAL_CACHE_PERIOD;

/**
 * @author Yuval Reches
 */
@Setter
@Getter
public class GoTypeSpecificConfigModel extends VcsTypeSpecificConfigModel {

    public GoTypeSpecificConfigModel() {
        setGitProvider(VcsGitProvider.ARTIFACTORY); // Default
    }

    //remote
    private Long missedRetrievalCachePeriodSecs = DEFAULT_GO_REMOTE_METADATA_MISSED_RETRIEVAL_CACHE_PERIOD;
    private Boolean gitLabResolveSubgroups = false;

    //virtual
    private Boolean enableExternalDependencies = true;
    private List<String> externalPatterns = DEFAULT_GO_METADATA_URLS;

    public String getExternalRemoteRepo() {
        return null;
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.Go;
    }

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) throws RepoConfigException {
        // no implementation by default
    }
}
