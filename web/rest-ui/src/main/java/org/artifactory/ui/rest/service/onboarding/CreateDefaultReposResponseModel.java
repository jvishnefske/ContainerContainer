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

package org.artifactory.ui.rest.service.onboarding;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Response model for default repositories created using REST UI
 *
 * @author nadavy
 */
@Data
@NoArgsConstructor
public class CreateDefaultReposResponseModel {
    private List<CreatedReposByType> createdReposByTypes;

    @JsonIgnore
    private Map<RepoType, CreatedReposByType> createdReposByTypesMap = new EnumMap<>(RepoType.class);

    @JsonIgnore
    private List<ProjectIdentifier> projectReposToAdd = new ArrayList<>();

    @JsonIgnore
    private boolean valid = true;

    /**
     * adds an empty model to the wrapper repo list for desired repo type
     */
    public CreatedReposByType addRepoType(RepoType repoType) {
        if (createdReposByTypes == null) {
            createdReposByTypes = Lists.newArrayList();
        }
        CreatedReposByType reposByType = createdReposByTypesMap.get(repoType);
        if (reposByType == null) {
            reposByType = new CreatedReposByType(repoType);
            createdReposByTypesMap.put(repoType, reposByType);
            createdReposByTypes.add(reposByType);
        }
        return reposByType;
    }

    public void addRepoToProject(String repoName, String project, Set<String> envs) {
        projectReposToAdd.add(new ProjectIdentifier(repoName, project, envs));
    }

    @Data
    @NoArgsConstructor
    public static class CreatedReposByType {

        private String type;
        private List<String> localRepos = Lists.newArrayList();
        private List<String> remoteRepos = Lists.newArrayList();
        private List<String> virtualRepos = Lists.newArrayList();

        CreatedReposByType(RepoType repoType) {
            this.type = repoType.getType();
        }

        public void addRepoToList(RepositoryType repoType, String repoKey) {
            switch (repoType) {
                case LOCAL -> localRepos.add(repoKey);
                case REMOTE -> remoteRepos.add(repoKey);
                case VIRTUAL -> virtualRepos.add(repoKey);
            }
        }
    }

    /**
     * Parameters needed to associate given repository with desired project and environment
     */
    public record ProjectIdentifier(String repoKey, String projectKey, Set<String> envs) {
    }
}
