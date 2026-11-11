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

package org.artifactory.ui.rest.model.onboarding;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;

import java.util.List;
import java.util.Set;

import static org.artifactory.projects.ProjectsService.DEFAULT_PROJECT;

/**
 * List of requested repo types to create quick repos for
 * url /api/onboarding/createQuickRepos
 *
 * @author nadavy
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class QuickRepositoryModel extends BaseModel {
    String repoName;
    RepoType packageType;
    RepositoryType repoType;
    String project = DEFAULT_PROJECT;
    Set<String> envs;
    boolean xrayEnabled;

    List<String> includedLocalRepositories;
    List<String> includedRemoteRepositories;
    String defaultDeploymentRepo;
}
