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

package org.artifactory.ui.rest.model.artifacts.search.packagesearch.search;

import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.descriptor.repo.DockerApiVersion;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author Dan Feldman
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AqlUIDockerSearchModel extends AqlUISearchModel {

    private DockerApiVersion version;

    //Used for Serialization
    @SuppressWarnings("unused")
    public AqlUIDockerSearchModel(String id, String displayName, String uiSearchKey, String fullName,
            boolean isVisibleByDefault, AqlComparatorEnum[] allowedComparators) {
        super(id, displayName, uiSearchKey, fullName, isVisibleByDefault, allowedComparators);
    }

    @JsonCreator //Used for Deserialization
    public AqlUIDockerSearchModel(@JsonProperty("id") String id,
            @JsonProperty("comparator") AqlComparatorEnum comparator,
            @JsonProperty("values") List<String> values, @JsonProperty("dockerVersion") String version) {
        super(id, comparator, values);
        this.version = DockerApiVersion.valueOf(version);
    }

    @JsonIgnore
    public DockerApiVersion getVersion() {
        return version;
    }
}