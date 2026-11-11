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

import org.artifactory.common.repo.RepoType;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.ph.PackageSpecification;

/**
 * @author Aviad Shikloshi
 */
public class RemoteRepositoryInfo extends RepositoryInfo {

    private String url;
    private Boolean hasEnabledReplication; //for 'run now' in grid actions
    private Boolean blackedOut;
    private Boolean offline;

    public RemoteRepositoryInfo() {
    }

    public RemoteRepositoryInfo(RemoteRepoConfig repoConfig, CentralConfigDescriptor configDescriptor,
            PackageSpecification packageSpecification) {
        super(repoConfig, packageSpecification, RepoType.REMOTE);
        this.url = repoConfig.getRepoTypeConfig().getUrl();
        RemoteReplicationDescriptor replication = configDescriptor.getRemoteReplication(repoKey);
        this.hasEnabledReplication = (replication != null && replication.isEnabled());
        this.blackedOut = repoConfig.getRepoTypeConfig().isBlackedOut();
        this.offline = repoConfig.getRepoTypeConfig().isOffline();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean isHasEnabledReplication() {
        return hasEnabledReplication;
    }

    public void setHasEnabledReplication(Boolean replication) {
        this.hasEnabledReplication = replication;
    }

    public Boolean isBlackedOut() {
        return blackedOut;
    }

    public void setBlackedOut(Boolean enabled) {
        this.blackedOut = enabled;
    }

    public Boolean getOffline() {
        return offline;
    }

    public void setOffline(Boolean offline) {
        this.offline = offline;
    }
}
