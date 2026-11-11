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
public class GradleSettingModel extends MavenGroupBaseModel {

    private String pluginRepoKey;
    private String libsResolverRepoKey;
    private String libsPublisherRepoKey;
    private Boolean pluginUseMaven;
    private Boolean resolverUseMaven;
    private Boolean publisherUseMaven;
    private Boolean pluginUseIvy;
    private Boolean resolverUseIvy;
    private Boolean publisherUseIvy;
    private String pluginResolverLayout;
    private String libsResolverLayout;
    private String libsPublisherLayouts;
    private String snippetName;
    private String savedSnippetName;
    private String password;
    private String gradleVersion;
    private Set<RepoKeyType> libsPublisher = Sets.newTreeSet(releaseRepoKeyComparator);
    private Set<RepoKeyType> pluginResolver = Sets.newTreeSet(pluginResolverRepoKeyComparator);
    private Set<RepoKeyType> libsResolver = Sets.newTreeSet(releaseRepoKeyComparator);
    private List<String> layouts = new ArrayList<>();
    private String gradleSnippet;

    public GradleSettingModel(String gradleSnippet) {
        this.gradleSnippet = gradleSnippet;
    }

    public GradleSettingModel(String snippetName, String savedSnippetName) {
        this.snippetName = snippetName;
        this.savedSnippetName = savedSnippetName;
    }

    public void clearProps() {
        pluginResolver = null;
        libsResolver = null;
        libsPublisher = null;
        layouts = null;
    }
}
