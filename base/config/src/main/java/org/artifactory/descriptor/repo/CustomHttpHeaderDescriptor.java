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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

/**
 * JAXB/XML descriptor for one custom HTTP header on a remote repository (central config import/export and
 * diff). Runtime REST and persistence use {@link org.artifactory.repo.remote.CustomHttpHeader}; this type holds the
 * same logical fields for descriptor serialization.
 *
 * @author Fawaz Atwa
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "CustomHttpHeaderType",
        propOrder = {"name", "value", "sensitive"},
        namespace = Descriptor.NS)
@GenerateDiffFunction
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomHttpHeaderDescriptor implements Descriptor {
    @DiffKey
    @NotUnicode
    @XmlElement(required = true)
    private String name;
    @NotUnicode
    @XmlElement(required = true)
    private String value;
    @XmlElement(defaultValue = "false")
    private boolean sensitive;

    public CustomHttpHeaderDescriptor(CustomHttpHeaderDescriptor other) {
        this.name = other.name;
        this.value = other.value;
        this.sensitive = other.sensitive;
    }

    public CustomHttpHeaderDescriptor(org.artifactory.repo.remote.CustomHttpHeader other) {
        this.name = other.getName();
        this.value = other.getValue();
        this.sensitive = other.isSensitive();
    }
}
