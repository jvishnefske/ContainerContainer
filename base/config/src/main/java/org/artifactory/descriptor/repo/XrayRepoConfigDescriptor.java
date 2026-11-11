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

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.common.config.metadata.MetadataInfo;
import org.artifactory.repo.xray.XrayNotAvailableMode;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.Objects;

/**
 * Xray specific configuration for repositories
 *
 * @author Dan Feldman
 */
@Setter
@Getter
@XmlType(name = "RepoXrayConfigType", propOrder = {"enabled", "dataTtl", "xrayNotAvailableMode"}, namespace =
        Descriptor.NS)
@GenerateDiffFunction
public class XrayRepoConfigDescriptor implements Descriptor {

    public static final int DEFAULT_DATA_TTL = 90;

    @XmlElement(defaultValue = "false", required = true)
    @MetadataInfo(defaultValue = "false")
    private boolean enabled = false;

    /**
     * The field will indicate for how long Xray data for artifacts in this repository shall be saved.
     */
    @XmlElement(required = true)
    @MetadataInfo(defaultValue = "90")
    private int dataTtl = DEFAULT_DATA_TTL;

    /**
     * The field will indicate how repository should act when xray is down.
     */
    @XmlElement(name = "xrayNotAvailableMode", nillable = true)
    private XrayNotAvailableMode xrayNotAvailableMode = null;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        XrayRepoConfigDescriptor that = (XrayRepoConfigDescriptor) o;
        return enabled == that.enabled && dataTtl == that.dataTtl && xrayNotAvailableMode == that.xrayNotAvailableMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, dataTtl, xrayNotAvailableMode);
    }
}
