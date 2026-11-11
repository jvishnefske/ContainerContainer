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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.rest.common.model.RestModel;
import org.artifactory.rest.common.util.JsonUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Dan Feldman
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AqlUISearchModel implements RestModel {

    //both
    protected String id;

    //out
    protected String displayName;
    private String uiSearchKey;
    protected String fullName;
    protected boolean visibleByDefault;
    protected AqlComparatorEnum[] allowedComparators;

    //in
    protected AqlComparatorEnum comparator;
    protected List<String> values;

    public AqlUISearchModel() {
    }

    //Used for Serialization
    public AqlUISearchModel(String id, String displayName, String uiSearchKey, String fullName,
            boolean visibleByDefault, AqlComparatorEnum[] allowedComparators) {
        this.id = id;
        this.displayName = displayName;
        this.uiSearchKey = uiSearchKey;
        this.fullName = fullName;
        this.visibleByDefault = visibleByDefault;
        this.allowedComparators = allowedComparators.clone();
    }


    @JsonCreator //Used for Deserialization
    public AqlUISearchModel(@JsonProperty("id") String id, @JsonProperty("comparator") AqlComparatorEnum comparator,
            @JsonProperty("values") List<String> values) {
        this.id = id;
        this.values = values;
        if (values.stream().anyMatch(value -> value.contains("*") || value.contains("?"))) {
            this.comparator = AqlComparatorEnum.matches;
        } else { this.comparator = Objects.requireNonNullElse(comparator, AqlComparatorEnum.equals); }
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    @SuppressWarnings("unused")
    public String getUiSearchKey() {
        return uiSearchKey;
    }

    public String getFullName() {
        return fullName;
    }

    public AqlComparatorEnum[] getAllowedComparators() {
        return allowedComparators;
    }

    @JsonIgnore
    public AqlComparatorEnum getComparator() {
        return comparator;
    }

    @JsonIgnore
    public List<String> getValues() {
        return values;
    }

    public boolean isVisibleByDefault() {
        return visibleByDefault;
    }

    public void setVisibleByDefault(boolean visibleByDefault) {
        this.visibleByDefault = visibleByDefault;
    }

    @Override
    public String toString() {
        return JsonUtil.jsonToString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AqlUISearchModel aqlUISearchModel = (AqlUISearchModel) o;

        if (!Objects.equals(id, aqlUISearchModel.id)) {
            return false;
        }

        if (!Objects.equals(displayName, aqlUISearchModel.displayName)) {
            return false;
        }

        if (!Objects.equals(uiSearchKey, aqlUISearchModel.uiSearchKey)) {
            return false;
        }

        if (!Objects.equals(fullName, aqlUISearchModel.fullName)) {
            return false;
        }

        if (visibleByDefault != aqlUISearchModel.visibleByDefault) {
            return false;
        }

        if (allowedComparators != null ? !Arrays.equals(allowedComparators, aqlUISearchModel.allowedComparators) :
                aqlUISearchModel.allowedComparators != null) { return false; }

        if (!Objects.equals(comparator, aqlUISearchModel.comparator)) {
            return false;
        }

        return Objects.equals(values, aqlUISearchModel.values);
    }
}
