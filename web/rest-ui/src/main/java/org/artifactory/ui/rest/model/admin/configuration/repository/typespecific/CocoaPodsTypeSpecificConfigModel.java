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
import org.artifactory.repo.packagetype.configuration.VcsGitConfiguration;
import org.artifactory.rest.common.exception.RepoConfigException;

import java.util.List;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_PODS_CDN_URL;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_PODS_EXTERNAL_DEPENDENCIES_PATTERNS;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_PODS_SPECS_REPO;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_VCS_GIT_CONFIG;

/**
 * @author Dan Feldman
 */
@Getter
@Setter
public class CocoaPodsTypeSpecificConfigModel extends VcsTypeSpecificConfigModel {

    //remote
    private String specsRepoUrl = DEFAULT_PODS_SPECS_REPO;
    private String specsCdnUrl = DEFAULT_PODS_CDN_URL;
    private VcsGitConfiguration specsRepoProvider = DEFAULT_VCS_GIT_CONFIG;
    private Boolean enableExternalDependencies = false;
    private List<String> externalPatterns = DEFAULT_PODS_EXTERNAL_DEPENDENCIES_PATTERNS;
    private Boolean forceModifyIndex = false;

    @Override
    public void validateRemoteTypeSpecific() throws RepoConfigException {
        setSpecsRepoUrl(ofNullable(getSpecsRepoUrl()).orElse(DEFAULT_PODS_SPECS_REPO));
        setSpecsRepoProvider(ofNullable(getSpecsRepoProvider()).orElse(DEFAULT_VCS_GIT_CONFIG));
        setSpecsCdnUrl(ofNullable(getSpecsCdnUrl()).orElse(DEFAULT_PODS_CDN_URL));
        super.validateRemoteTypeSpecific();
    }

    @Override
    public void validateVirtualTypeSpecific(AddonsManager addonsManager) throws RepoConfigException {
        // no implementation by default
    }

    @Override
    public RepoType getRepoType() {
        return RepoType.CocoaPods;
    }

}
