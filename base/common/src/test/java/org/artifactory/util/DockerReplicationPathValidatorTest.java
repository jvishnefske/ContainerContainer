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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author oriya
 */
class DockerReplicationPathValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            ".dependencies/some.gem;/../../v2/image/blobs/sha256:abc",
            ".dependencies/some.gem%3B/../../v2/image/blobs/sha256:abc",
            ".dependencies/some.gem%3B/%2e%2e/%2e%2e/v2/image/blobs/sha256:abc",
            ".dependencies/some.gem%253B/%252e%252e/v2/image/blobs/sha256:abc",
            "v2/image/blobs/../../../etc/passwd",
            "v2/image/blobs/..\\..\\etc\\passwd",
            "../v2/image/blobs/sha256:abc",
            "/v2/image/blobs/sha256:abc",
            "v2/image/./blobs/sha256:abc",
            "v2/image/blobs/sha256:abc;prop=value",
            "..",
            "path/with%20space/file.txt",
            "path/with+plus/file.txt",
            "repositories/100%25/image.json",
            "v2/image@sha256:abc",
            "v2/image/blobs/sha256:abc?query=1",
            "v2/image/blobs/sha256:abc#frag"
    })
    void escapingPathsAreDetected(String path) {
        assertTrue(DockerReplicationPathValidator.isEscapingPath(path),
                "Path must be rejected as escaping: " + path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "v2/image/blobs/sha256:abcdef0123456789",
            "v2/library/nginx/manifests/latest",
            "repository/library/nginx/_uploads/some-uuid",
            ".dependencies/some.gem",
            "..hidden/file.txt",
            "a..b/file.txt",
            "aicp/1.2.5/manifest.json",
            "aicp/1.2.5/sha256__8b960257b1014cd0ff72594c5a8ad7454ab5cfe8603d7f0c59812170015fef8a"
    })
    void legitimatePathsAreAccepted(String path) {
        assertFalse(DockerReplicationPathValidator.isEscapingPath(path),
                "Path must be accepted: " + path);
    }

    @Test
    void blankPathsAreAccepted() {
        assertFalse(DockerReplicationPathValidator.isEscapingPath(null));
        assertFalse(DockerReplicationPathValidator.isEscapingPath(""));
        assertFalse(DockerReplicationPathValidator.isEscapingPath("   "));
    }
}
