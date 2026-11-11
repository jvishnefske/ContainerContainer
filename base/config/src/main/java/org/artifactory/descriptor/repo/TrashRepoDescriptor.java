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

package org.artifactory.descriptor.repo;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.LocalConfig;

import java.util.List;

/**
 * @author Shay Yaakov
 */
public class TrashRepoDescriptor extends LocalRepoDescriptor {

    public TrashRepoDescriptor(String repoKey, RepoLayout simpleLayout) {
        setKey(repoKey);
        setType(RepoType.Generic);
        setRepoLayout(simpleLayout);
    }

    public TrashRepoDescriptor(LocalConfig repositoryConfig, List<RepoLayout> repoLayouts,
            List<PropertySet> propertySets, List<KeyPairDescriptor> keyPairs) {
        super(repositoryConfig, repoLayouts, propertySets, keyPairs);
    }

    public static TrashRepoDescriptor fromRepositoryConfig(CentralConfigDescriptor centralConfigDescriptor,
            LocalConfig sourceRepoConfig) {
        return new TrashRepoDescriptor(sourceRepoConfig, centralConfigDescriptor.getRepoLayouts(),
                centralConfigDescriptor.getPropertySets(),
                centralConfigDescriptor.getKeyPairs());
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isCache() {
        return false;
    }
}