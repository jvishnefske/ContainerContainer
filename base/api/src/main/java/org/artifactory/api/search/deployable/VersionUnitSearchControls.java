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

package org.artifactory.api.search.deployable;

import lombok.Getter;
import lombok.Setter;
import org.artifactory.api.search.SearchControlsBase;
import org.artifactory.repo.RepoPath;

/**
 * Holds the version unit search parameters
 *
 * @author Noam Y. Tenne
 */
@Getter
@Setter
public class VersionUnitSearchControls extends SearchControlsBase {

    private final RepoPath pathToSearchWithin;
    private boolean exactMatch;

    /**
     * Main constructor
     *
     * @param pathToSearchWithin Repo path to search version units within
     */
    public VersionUnitSearchControls(RepoPath pathToSearchWithin) {
        this.pathToSearchWithin = pathToSearchWithin;
        addRepoToSearch(pathToSearchWithin.getRepoKey());
    }

    @Override
    public boolean isEmpty() {
        return pathToSearchWithin == null;
    }

    @Override
    public boolean isWildcardsOnly() {
        // cannot really be a wildcards only search
        return isEmpty();
    }

}
