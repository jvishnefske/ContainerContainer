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

package org.artifactory.api.rest.search.result;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.artifactory.api.config.VersionInfo;

import java.util.List;
import java.util.Map;

/**
 * A wrapper class for a JSON object representation of Artifactory's version, revision, and a list of enabled addons.
 *
 * @author Tomer Cohen
 */
@Value
@AllArgsConstructor
public class VersionRestResult {

    String version;
    String revision;
    Map<String, String> servicesVersions;
    List<String> addons;
    String license;
    Map<String, Boolean> entitlements;

    public VersionRestResult(String version, String revision, List<String> addons, String license,
            Map<String, Boolean> entitlements) {
        this.version = version;
        this.revision = revision;
        this.servicesVersions = Map.of();
        this.addons = addons;
        this.license = license;
        this.entitlements = entitlements;
    }

    /**
     * Converts {@link VersionRestResult} to {@link VersionInfo}
     *
     * @return {@link VersionInfo}
     */
    public VersionInfo toVersionInfo() {
        return new VersionInfo(version, revision);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("version: ");
        sb.append(version);
        sb.append(" | ");
        sb.append("revision: ");
        sb.append(revision);
        for (Map.Entry<String, String> entry : servicesVersions.entrySet()) {
            sb.append(" | ");
            sb.append(entry.getKey() + ": ");
            sb.append(entry.getValue());
        }
        sb.append(" | ");
        sb.append("addons: ");
        sb.append(addons);
        sb.append(" | ");
        sb.append("license: ");
        sb.append(license);
        sb.append(" | ");
        sb.append("entitlements: ");
        sb.append(entitlements);
        return sb.toString();
    }
}
