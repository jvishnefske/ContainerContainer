/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2020 JFrog Ltd.
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

package org.artifactory.ui.rest.model.admin.security.keys;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.artifactory.rest.common.model.BaseModel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yuval Reches
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KeyPairIModel extends BaseModel {
    @JsonProperty
    private String pairName;
    @JsonProperty
    private String alias;
    @JsonProperty
    private String pairType;
    @JsonProperty
    private String vaultKey;
    @JsonProperty
    private String source;
    @JsonProperty
    private List<String> resources = new ArrayList<>();
    @JsonProperty
    private List<String> resourcesAsSecondary = new ArrayList<>();
    @JsonProperty
    private Boolean unavailable;
}