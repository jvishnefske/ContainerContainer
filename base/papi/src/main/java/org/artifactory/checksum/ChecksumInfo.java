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

import lombok.Getter;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Objects;

/**
 * Holds original and calculated values of a checksum.
 *
 * @author Yossi Shaul
 */
@XmlRootElement(name = "checksum")
@XmlAccessorType(XmlAccessType.FIELD)
public class ChecksumInfo implements Serializable {

    // markers a checksum type with no original checksum to be safe.
    // this marker is used when a file is deployed, and we don't have the remote
    // checksum, but we have the actual file
    public static final String TRUSTED_FILE_MARKER = "NO_ORIG";

    @Getter
    private ChecksumType type;
    private String original;
    @Getter
    private String actual;

    public ChecksumInfo() {
        // for JAXB
    }

    public ChecksumInfo(ChecksumType type, String original, String actual) {
        this(type, original, actual, true);
    }

    public ChecksumInfo(ChecksumType type, String original, String actual, boolean validateChecksum) {
        this.type = type;
        if (validateChecksum) {
            if (actual != null && !type.isValid(actual)) {
                throw new IllegalStateException(
                        "Actual " + type + " checksum invalid: '" + actual + "' : '" + original + "'");
            }
            // The original checksum comes from remote servers which we can't control their formats, therefore we
            // have to normalize the original checksum value.
            this.original = normalize(original);
        } else {
            this.original = (original == null || original.length() != type.length()) ? original :
                    original.toLowerCase();
        }
        this.actual = actual;
    }

    /**
     * @return The client (original) checksum or the actual if the checksum is marked as trusted
     */
    public String getOriginal() {
        if (isMarkedAsTrusted()) {
            return getActual();
        } else {
            return original;
        }
    }

    /**
     * @return Always returns the client (original) recorded checksum, even if it is org.artifactory.checksum
     * .ChecksumInfo#TRUSTED_FILE_MARKER
     */
    public String getOriginalOrNoOrig() {
        return original;
    }

    public boolean checksumsMatch() {
        return original != null && actual != null && (isMarkedAsTrusted() || actual.equals(original));
    }

    /**
     * Normalize checksum value.
     * Checksum comparisons shouldn't be case-sensitive therefore we have to normalize the original checksum to lower
     * case.
     */
    private String normalize(String candidate) {
        return type.isValid(candidate) ? candidate.toLowerCase() : candidate;
    }

    /**
     * Checks if the checksum is marked as trusted by Artifactory.
     * <p/>
     * For internal use only.
     */
    //TODO: [by yl] Remove from papi
    public boolean isMarkedAsTrusted() {
        return TRUSTED_FILE_MARKER.equals(original);
    }

    /**
     * Compares checksum by type and value.
     */
    public boolean isIdentical(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChecksumInfo info = (ChecksumInfo) o;
        if (type != info.type) {
            return false;
        }
        if (!Objects.equals(actual, info.actual)) {
            return false;
        }
        return Objects.equals(original, info.original);
    }

    /**
     * Compares checksum by type only
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChecksumInfo info = (ChecksumInfo) o;
        return type == info.type;
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return "ChecksumInfo{" +
                "type=" + type +
                ", original='" + original + '\'' +
                ", actual='" + actual + '\'' +
                '}';
    }
}