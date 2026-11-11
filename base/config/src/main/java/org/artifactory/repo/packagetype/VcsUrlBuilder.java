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

package org.artifactory.repo.packagetype;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.repo.vcs.VcsGitProvider;

import java.text.MessageFormat;

/**
 * @author Michael Pasternak
 */
public class VcsUrlBuilder {

    /**
     * Formats single resource download URL based on
     * VcsGitProvider url definition
     *
     * @return formatted url
     */
    public static String resourceDownloadUrl(VcsGitProvider provider, String user, String repository, String file,
            String branch) {
        if (!StringUtils.isBlank(provider.getResourceDownloadUrl())) {
            return MessageFormat.format(provider.getResourceDownloadUrl(), user, repository, file, branch);
        }
        return null;
    }

    /**
     * Formats single resource download URL based on
     * VcsGitProvider url definition
     *
     * @return formatted url
     */
    public static String releaseDownloadUrl(VcsGitProvider provider, String user, String repository, String release,
            String ballType) {
        if (!StringUtils.isBlank(provider.getReleaseDownloadUrl())) {
            return MessageFormat.format(provider.getReleaseDownloadUrl(), user, repository, ballType, release);
        }
        return null;
    }

    /**
     * Formats repository download URL based on
     * VcsGitProvider url definition
     *
     * @return formatted url
     */
    public static String repositoryDownloadUrl(String urlTemplate, String gitOrg, String gitRepo, String version,
            String fileExt) {
        if (!StringUtils.isBlank(urlTemplate)) {
            return MessageFormat.format(urlTemplate, gitOrg, gitRepo, version, fileExt);
        }
        return null;
    }
}
