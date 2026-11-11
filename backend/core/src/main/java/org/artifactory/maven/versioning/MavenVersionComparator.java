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

package org.artifactory.maven.versioning;


import org.jfrog.common.legacy.maven.comparator.VersionComparator;
import org.jfrog.common.legacy.maven.comparator.VersionComparators;

import java.util.Comparator;

/**
 * Compares two version strings.
 *
 * @author Yossi Shaul
 */
public class MavenVersionComparator implements Comparator<String> {
    /**
     * The actual comparator
     */
    private final VersionComparator comparator =
            VersionComparators.getVersionComparator("mercury");

    @Override
    @SuppressWarnings({"unchecked"})
    public int compare(String v1, String v2) {
        return comparator.compare(v1, v2);
    }
}
