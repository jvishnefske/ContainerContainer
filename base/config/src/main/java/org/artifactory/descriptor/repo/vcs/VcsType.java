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

package org.artifactory.descriptor.repo.vcs;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * Enum of the VCS type support for remote repositories.
 *
 * @author Shay Yaakov
 */
@XmlEnum
@GenerateDiffFunction
public enum VcsType {
    @XmlEnumValue("git") @JsonProperty("GIT") GIT("git");

    private final String val;

    VcsType(String val) {
        this.val = val;
    }

    @Override
    public String toString() {
        return val;
    }

    public String getVal() {
        return val;
    }

    public static VcsType fromString(String val) {
        for (VcsType vcsType : VcsType.values()) {
            if (vcsType.val.equalsIgnoreCase(val)) {
                return vcsType;
            }
        }
        return null;
    }
}