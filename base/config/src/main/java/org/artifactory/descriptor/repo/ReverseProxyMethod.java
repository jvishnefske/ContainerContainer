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
 * @author Shay Yaakov
 */
@XmlEnum
@GenerateDiffFunction
public enum ReverseProxyMethod {

    @XmlEnumValue("subDomain") @JsonProperty("SUBDOMAIN") SUBDOMAIN("subDomain"),
    @XmlEnumValue("path") @JsonProperty("REPOPATHPREFIX") REPOPATHPREFIX("path"),
    @XmlEnumValue("portPerRepo") @JsonProperty("PORTPERREPO") PORTPERREPO("portPerRepo"),
    @XmlEnumValue("noValue") @JsonProperty("NOVALUE") NOVALUE("noValue");

    private final String val;

    ReverseProxyMethod(String val) {
        this.val = val;
    }

    public String toString() {
        return val;
    }

    // NOVALUE - was left for migraion.
    public boolean isRepoPathPrefix() {
        return this.equals(REPOPATHPREFIX);
    }

    static public ReverseProxyMethod defaultValue() {
        return SUBDOMAIN;
    }

    public String getVal() {
        return val;
    }
}