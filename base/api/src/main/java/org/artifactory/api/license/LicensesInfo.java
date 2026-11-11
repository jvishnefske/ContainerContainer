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

package org.artifactory.api.license;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.List;

/**
 * A container licenses class, contains a list of {@link LicenseInfo} each of which represent a license.
 *
 * @author Tomer Cohen
 */
@XmlRootElement(name = LicensesInfo.ROOT)
@XmlAccessorType(XmlAccessType.FIELD)
public class LicensesInfo implements Serializable {
    public static final String ROOT = "licenses";

    public LicensesInfo() {
        // JAXB needs it
    }

    @XmlElementWrapper(name = LicensesInfo.ROOT)
    @XmlElement(name = LicenseInfo.ROOT_STR)
    private List<LicenseInfo> licenses;

    public List<LicenseInfo> getLicenses() {
        return licenses;
    }

    public void setLicenses(List<LicenseInfo> licenses) {
        this.licenses = licenses;
    }
}
