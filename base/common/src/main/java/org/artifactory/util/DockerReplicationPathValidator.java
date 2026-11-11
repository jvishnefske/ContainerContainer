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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * Validates relative paths used by the Docker replication endpoint. Legitimate paths only need
 * {@code A-Za-z0-9}, {@code /}, {@code .}, {@code -}, {@code _}, and {@code :} (digest form).
 * All other characters are rejected, including percent encoding and matrix parameter separators.
 * Whole {@code .} and {@code ..} segments and absolute paths are rejected separately.
 * <p>
 * The endpoint path parameter can already be URL-decoded by Jersey. This remains safe because decoded
 * disallowed characters are rejected, decoded dot segments are detected, and nested encodings retain a
 * percent character that the allowlist rejects.
 *
 * @author oriya
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class DockerReplicationPathValidator {

    private static final String SEGMENT_SEPARATOR = "/";
    private static final String CURRENT_DIR = ".";
    private static final String PARENT_DIR = "..";
    private static final Pattern DISALLOWED_CHAR = Pattern.compile("[^A-Za-z0-9/._:-]");

    /**
     * @param path Docker replication path, relative to the repository root
     * @return true when the path is absolute or contains a disallowed character or dot segment
     */
    public static boolean isEscapingPath(String path) {
        if (StringUtils.isBlank(path)) {
            return false;
        }
        return path.startsWith(SEGMENT_SEPARATOR)
                || containsDisallowedChar(path)
                || containsDotSegment(path);
    }

    private static boolean containsDisallowedChar(String path) {
        return DISALLOWED_CHAR.matcher(path).find();
    }

    private static boolean containsDotSegment(String path) {
        for (String segment : StringUtils.split(path, SEGMENT_SEPARATOR)) {
            if (CURRENT_DIR.equals(segment) || PARENT_DIR.equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
