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

package org.artifactory.ui.rest.model.setmeup;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author chen keinan
 */
@NoArgsConstructor
@Getter
@Setter
public class IvySettingModel extends MavenGroupBaseModel {

    private Set<RepoKeyType> libsRepository = Sets.newTreeSet(releaseRepoKeyComparator);
    private List<String> libsRepositoryLayout = new ArrayList<>();
    private String libsRepo;
    private String libsRepoLayout;
    private String libsResolverName;
    private Boolean useIbiblioResolver;
    private Boolean m2Compatible;
    private String ivySnippet;
    private String settings;
    private String savedSnippetName;
    private String password;

    public IvySettingModel(String ivySnippet) {
        this.ivySnippet = ivySnippet;
    }

    public IvySettingModel(String settings, String savedSnippetName) {
        this.settings = settings;
        this.savedSnippetName = savedSnippetName;
    }

    public void clearProps() {
        libsRepository = null;
        libsRepositoryLayout = null;
    }
}
