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

package org.artifactory.util;

import jakarta.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;

/**
 * @author asafz
 */
public abstract class MailUtils {

    private MailUtils() {
        // utility class
    }

    /**
     * create mail message
     *
     * @param artifactoryUrl - artifactory base url
     * @return test message
     */
    @Nonnull
    public static StringBuilder getMessage(String artifactoryUrl) {
        StringBuilder artifactoryUrlMessage = new StringBuilder();
        fillMessageFromArtifactoryUrl(artifactoryUrlMessage, artifactoryUrl);
        return artifactoryUrlMessage;
    }

    /**
     * create test mail message
     *
     * @param artifactoryUrl - artifactory base url
     * @return test message
     */
    @Nonnull
    public static StringBuilder getTestMessage(String artifactoryUrl) {
        StringBuilder message = new StringBuilder();
        message.append("This is a test message from Artifactory").append("<br/>");
        fillMessageFromArtifactoryUrl(message, artifactoryUrl);
        return message;
    }

    private static void fillMessageFromArtifactoryUrl(StringBuilder message, String artifactoryUrl) {
        if (StringUtils.isNotBlank(artifactoryUrl)) {
            String artifactoryLink = createArtifactoryLinkFromUrl(artifactoryUrl);
            message.append("Your Artifactory base URL is: ").append(artifactoryLink);
        } else {
            message.append("No Artifactory base URL is configured");
        }
    }

    private static String createArtifactoryLinkFromUrl(String artifactoryUrl) {
        return "<a href=" + artifactoryUrl + " target=\"blank\"" + ">" + artifactoryUrl + "</a>";
    }
}
