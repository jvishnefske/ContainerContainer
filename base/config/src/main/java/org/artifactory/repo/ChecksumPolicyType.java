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

package org.artifactory.repo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import jakarta.xml.bind.annotation.XmlEnumValue;

/**
 * Enum of the checksum policies for repositories.
 *
 * @author Yossi Shaul
 */
@GenerateDiffFunction(typeMethod = "getMessage")
public enum ChecksumPolicyType {
    @XmlEnumValue("generate-if-absent") @JsonProperty("GEN_IF_ABSENT") GEN_IF_ABSENT("generate-if-absent"),
    @XmlEnumValue("fail") @JsonProperty("FAIL") FAIL("fail"),
    @XmlEnumValue("ignore-and-generate") @JsonProperty("IGNORE_AND_GEN") IGNORE_AND_GEN("ignore-and-generate"),
    @XmlEnumValue("pass-thru") @JsonProperty("PASS_THRU") PASS_THRU("pass-thru");

    String message;

    ChecksumPolicyType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    /**
     * return ChecksumPolicyType if found from message ignore case, otherwise return null.
     * @param message
     * @return ChecksumPolicyType
     */
    public static ChecksumPolicyType fromString(String message) {
        for (ChecksumPolicyType checksumPolicyType : ChecksumPolicyType.values()) {
            if (checksumPolicyType.message.equalsIgnoreCase(message)) {
                return checksumPolicyType;
            }
        }
        return null;
    }
}