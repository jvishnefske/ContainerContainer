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

package org.artifactory.descriptor.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * Password encryption policy types.
 *
 * @author Yossi Shaul
 */
@GenerateDiffFunction(typeMethod = "getType")
public enum EncryptionPolicy {
    @XmlEnumValue("supported") @JsonProperty("SUPPORTED") SUPPORTED("supported"),
    @XmlEnumValue("required") @JsonProperty("REQUIRED") REQUIRED("required"),
    @XmlEnumValue("unsupported") @JsonProperty("UNSUPPORTED") UNSUPPORTED("unsupported");

    private final String type;

    EncryptionPolicy(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
