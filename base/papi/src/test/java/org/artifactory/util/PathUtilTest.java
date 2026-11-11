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

import org.jfrog.client.util.PathUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for PathUtil
 *
 * @author Gidi Shabat
 */
class PathUtilTest {

    @Test
    void getPathNameTest() {
        assertEquals("a", PathUtils.getFileName("a"));
        assertEquals("a", PathUtils.getFileName("//a"));
        assertEquals("a", PathUtils.getFileName("a://a"));
        assertEquals("c", PathUtils.getFileName("a/b/c"));
        assertEquals("c", PathUtils.getFileName("a/b/////c"));
        assertEquals("c", PathUtils.getFileName("//a/b/////c//"));
        assertEquals("?", PathUtils.getFileName("//a/b/////?//"));
        assertEquals(".", PathUtils.getFileName("//a/b/////.//"));
        assertEquals("a:properties", PathUtils.getFileName("a:properties"));
        assertEquals("a:properties", PathUtils.getFileName("///a:properties"));
        assertEquals("c:properties", PathUtils.getFileName("///a/b/c:properties"));
        assertEquals("c", PathUtils.getFileName("///a\\b\\c"));
        assertEquals("c", PathUtils.getFileName("///a\\b\\\\c"));
        assertEquals("text.txt", PathUtils.getFileName("text.txt"));
        assertEquals("text.txt", PathUtils.getFileName("a/b/text.txt"));
        assertEquals("text.txt", PathUtils.getFileName("a/b/text.txt\\"));
    }
}
