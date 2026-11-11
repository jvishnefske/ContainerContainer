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

import java.util.Set;

/**
 * @author chen keinan
 */
@NoArgsConstructor
@Getter
@Setter
public class MavenSettingModel extends MavenGroupBaseModel {

    private String release;
    private String snapshot;
    private String pluginRelease;
    private String pluginSnapshot;
    private String mirror;
    private String mavenSnippet;
    private String settings;
    private String savedSnippetName;
    private String password;
    private Set<RepoKeyType> releases = Sets.newTreeSet(releaseRepoKeyComparator);
    private Set<RepoKeyType> pluginReleases = Sets.newTreeSet(pluginReleaseRepoKeyComparator);
    private Set<RepoKeyType> snapshots = Sets.newTreeSet(snapshotRepoKeyComparator);
    private Set<RepoKeyType> pluginSnapshots = Sets.newTreeSet(pluginSnapshotRepoKeyComparator);
    private Set<RepoKeyType> anyMirror = Sets.newTreeSet(anyMirrorComparator);

    public MavenSettingModel(String mavenSnippet) {
        this.mavenSnippet = mavenSnippet;
    }

    public MavenSettingModel(String settings, String savedSnippetName) {
        this.settings = settings;
        this.savedSnippetName = savedSnippetName;
    }

    public void clearProps() {
        releases = null;
        pluginReleases = null;
        snapshots = null;
        pluginSnapshots = null;
        anyMirror = null;
    }
}