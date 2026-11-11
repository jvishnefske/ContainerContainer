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

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.util.PackageTypeConfigFields;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import jakarta.validation.Valid;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

/**
 * @author Dan Feldman
 */
@Getter
@Setter
@XmlType(name = "CocoaPodsConfigurationType", propOrder = {"cocoaPodsSpecsRepoUrl", "specRepoProvider",
        "cocoaPodsForceModifyIndex"}, namespace = Descriptor.NS)
@GenerateDiffFunction
public class CocoaPodsConfiguration implements Descriptor {

    @XmlElement(defaultValue = "https://github.com/CocoaPods/Specs")
    @NotUnicode
    private String cocoaPodsSpecsRepoUrl = "https://github.com/CocoaPods/Specs";

    @XmlTransient
    @NotUnicode
    private String cocoaPodsCdnUrl = PackageTypeConfigFields.COCOA_PODS_CDN_URL.getDefaultValue();

    @XmlElement(name = "specRepoProvider")
    @Valid
    private VcsGitConfiguration specRepoProvider = new VcsGitConfiguration();

    @XmlElement(defaultValue = "false")
    private boolean cocoaPodsForceModifyIndex = false;

    public void setCocoaPodsCdnUrl(String cocoaPodsCdnUrl) {
        this.cocoaPodsCdnUrl = StringUtils.trim(cocoaPodsCdnUrl);
    }

    public void setCocoaPodsSpecsRepoUrl(String cocoaPodsSpecsRepoUrl) {
        this.cocoaPodsSpecsRepoUrl = StringUtils.trim(cocoaPodsSpecsRepoUrl);
    }
}
