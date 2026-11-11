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

package org.artifactory.checksum;

import java.util.List;

/**
 * @author Yoav Landman
 */
public enum ChecksumType {
    sha1("SHA-1", ".sha1", 40),
    md5("MD5", ".md5", 32),
    sha256("SHA-256", ".sha256", 64);

    public static final List<ChecksumType> BASE_CHECKSUM_TYPES = List.of(sha1, md5, sha256);

    private final String alg;
    private final String ext;
    private final int length;    // length of the hexadecimal string representation of the checksum

    ChecksumType(String alg, String ext, int length) {
        this.alg = alg;
        this.ext = ext;
        this.length = length;
    }

    public String alg() {
        return alg;
    }

    /**
     * @return The filename extension of the checksum, including the dot prefix.
     */
    public String ext() {
        return ext;
    }

    /**
     * @return The length of a valid checksum for this checksum type.
     */
    public int length() {
        return length;
    }

    /**
     * @param filePath The checksum file path (assumed to end with the checksum extension).
     * @return Checksum type for the given file path. Null if not found.
     */
    public static ChecksumType forFilePath(String filePath) {
        if (filePath == null) {
            return null;
        }
        final String lowerCasePath = filePath.toLowerCase();

        if (lowerCasePath.endsWith(sha1.ext)) {
            return sha1;
        } else if (lowerCasePath.endsWith(sha256.ext)) {
            return sha256;
        } else if (lowerCasePath.endsWith(md5.ext)) {
            return md5;
        } else {
            return null;
        }
    }

    public static ChecksumType forAlgorithm(String algorithm) {
        for (ChecksumType type : values()) {
            if (type.alg.equalsIgnoreCase(algorithm)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No checksum type exists for algorithm '" + algorithm + "'");
    }

    /**
     * @param candidate Checksum candidate
     * @return True if this string is a checksum value for this type
     */
    public boolean isValid(String candidate) {
        if (candidate == null || candidate.length() != length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (!isValidHexadecimalCharacter(candidate.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidHexadecimalCharacter(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    @Override
    public String toString() {
        return alg;
    }
}
