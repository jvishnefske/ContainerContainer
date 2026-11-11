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

package org.artifactory.addon.p2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;

import java.io.Serializable;

/**
 * Model of a local or remote repository for the UI
 *
 * @author Shay Yaakov
 */
public class P2Repo implements Serializable {

    private RepoConfig repoConfig;
    private String repoKey;

    private String repoUrl;

    @JsonIgnore
    private RepoDescriptor descriptor;

    public P2Repo() {

    }

    public P2Repo(RepoDescriptor descriptor, RepoConfig repoConfig, String repoKey, String repoUrl) {
        this.descriptor = descriptor;
        this.repoConfig = repoConfig;
        this.repoKey = repoKey;
        this.repoUrl = repoUrl;
    }

    @JsonIgnore
    public boolean isRemote() {
        return repoConfig instanceof RemoteRepoConfig;
    }

    @JsonIgnore
    public RepoConfig getRepoConfig() {
        return repoConfig;
    }

    @JsonIgnore
    public RepoDescriptor getDescriptor() {
        return descriptor;
    }

    public String getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }
}
