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

package com.jfrog.build.model.api.diff;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds diff model object which wraps dependencies diff
 *
 * @author Shay Yaakov
 */
public class BuildsDiffDependencyModel extends BuildsDiffBaseFileModel {

    @JsonIgnore
    private boolean internalDependency;

    private Set<String> scopes;

    private Set<BuildsDiffValue> buildsDiffValue = new HashSet<>();

    public boolean isInternalDependency() {
        return internalDependency;
    }

    public void setInternalDependency(boolean internalDependency) {
        this.internalDependency = internalDependency;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    @Override
    public String getName() {
        return name;
    }

    @JsonProperty(value = "id")
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDiffName() {
        return diffName;
    }

    @JsonProperty(value = "diffId")
    @Override
    public void setDiffName(String diffName) {
        this.diffName = diffName;
    }

    public Set<BuildsDiffValue> getBuildsDiffValue() {
        return buildsDiffValue;
    }

    public void addBuildsDiffValue(BuildsDiffValue buildsDiffValue) {
        this.buildsDiffValue.add(buildsDiffValue);
    }
}
