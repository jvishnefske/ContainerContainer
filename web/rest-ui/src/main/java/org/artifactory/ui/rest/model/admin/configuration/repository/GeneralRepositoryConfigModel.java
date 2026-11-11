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

package org.artifactory.ui.rest.model.admin.configuration.repository;

import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.util.JsonUtil;

import java.util.Set;

import static java.util.Optional.ofNullable;

/**
 * @author Dan Feldman
 * @author Aviad Shikloshi
 */
public class GeneralRepositoryConfigModel implements RestModel {

    protected String repoKey;
    protected String projectKey;
    private Set<String> environments;

    public String getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public String getProjectKey() {
        if (projectKey == null) {
            return ProjectsService.DEFAULT_PROJECT;
        }
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public Set<String> getEnvironments() {
        return ofNullable(environments).orElseGet(Set::of);
    }

    public void setEnvironments(Set<String> environments) {
        this.environments = environments;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }
}
