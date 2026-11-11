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

package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.repo;

import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.VirtualRepoConfig;
import org.artifactory.rest.common.util.JsonUtil;

import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

/**
 * @author Chen  Keinan
 */
public class VirtualRemoteRepositoryNode extends RepositoryNode {

    private boolean hasDefaultDeploymentRepo;

    public VirtualRemoteRepositoryNode(String key, RepoType repoPkgType, String repoType,
            boolean hasDefaultDeployRepo) {
        super(key, repoPkgType, repoType);
        setType("virtualRemoteRepository");
        setLocal(false);
        setHasChild(true);
        setHasDefaultDeploymentRepo(hasDefaultDeployRepo);
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    @SuppressWarnings("unused")
    public boolean isHasDefaultDeploymentRepo() {
        return hasDefaultDeploymentRepo;
    }

    private void setHasDefaultDeploymentRepo(boolean hasDefaultDeploymentRepo) {
        this.hasDefaultDeploymentRepo = hasDefaultDeploymentRepo;
    }

    public static VirtualRemoteRepositoryNode fromRemoteDescriptor(RemoteRepoDescriptor repo) {
        return new VirtualRemoteRepositoryNode(repo.getKey(), repo.getType(), REMOTE.typeNameLowercase(), false);
    }

    public static VirtualRemoteRepositoryNode fromRemoteRepoConfig(RepoConfig repo) {
        return new VirtualRemoteRepositoryNode(repo.getKey(), repo.getPackageType(), REMOTE.typeNameLowercase(), false);
    }

    public static VirtualRemoteRepositoryNode fromVirtualDescriptor(VirtualRepoDescriptor repo) {
        return new VirtualRemoteRepositoryNode(repo.getKey(), repo.getType(), VIRTUAL.typeNameLowercase(),
                repo.getDefaultDeploymentRepo() != null);
    }

    public static VirtualRemoteRepositoryNode fromVirtualRepoConfig(VirtualRepoConfig repo) {
        return new VirtualRemoteRepositoryNode(repo.getKey(), repo.getPackageType(), VIRTUAL.typeNameLowercase(),
                repo.getRepoTypeConfig().getDefaultDeploymentRepoRef() != null);
    }
}
