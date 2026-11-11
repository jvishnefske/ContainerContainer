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

package org.artifactory.descriptor.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * @author Yoav Landman
 */
@XmlEnum
@GenerateDiffFunction
public enum SnapshotVersionBehaviorDescriptor {
    @XmlEnumValue("unique") @JsonProperty("UNIQUE") UNIQUE("Unique"),
    @XmlEnumValue("non-unique") @JsonProperty("NONUNIQUE") NONUNIQUE("Non-unique"),
    @XmlEnumValue("deployer") @JsonProperty("DEPLOYER") DEPLOYER("Deployer");

    private String displayName;

    /**
     * Sets the display name of the element
     *
     * @param displayName The display name
     */
    SnapshotVersionBehaviorDescriptor(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name of the element
     *
     * @return String - Element display name
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName.toLowerCase();
    }
}
