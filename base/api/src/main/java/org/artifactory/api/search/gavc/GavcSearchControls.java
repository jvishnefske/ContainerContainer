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

package org.artifactory.api.search.gavc;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.api.search.SearchControlsBase;

import java.util.List;
import java.util.Set;

/**
 * Holds the GAVC search parameters
 *
 * @author Noam Tenne
 */
@Getter
@Setter
@ToString
public class GavcSearchControls extends SearchControlsBase {
    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private List<String> extensions;
    private Long limitOverride;
    private Direction sortDirection = Direction.NONE;
    private boolean specific;
    private long createBefore;
    private long lastDownloadedBefore;
    private long lastModifiedBefore;
    private boolean includeRemotes;
    private Pair<String, String> includedProperty; // A custom (key, value) property to filter artifacts by
    private Set<String> includedFolderPaths; // A set of folder path patterns to filter artifacts by

    /**
     * Default constructor
     */
    public GavcSearchControls() {
    }

    /**
     * Copy constructor
     *
     * @param gavcSearchControls Controls to copy
     */
    public GavcSearchControls(GavcSearchControls gavcSearchControls) {
        this.groupId = gavcSearchControls.groupId;
        this.artifactId = gavcSearchControls.artifactId;
        this.version = gavcSearchControls.version;
        this.classifier = gavcSearchControls.classifier;
        this.selectedRepoForSearch = gavcSearchControls.selectedRepoForSearch;
        this.specific = gavcSearchControls.specific;
        this.sortDirection = gavcSearchControls.sortDirection;
        this.includeRemotes = gavcSearchControls.includeRemotes;
        this.includedProperty = gavcSearchControls.includedProperty;
        this.extensions = gavcSearchControls.extensions;
        setLimitSearchResults(gavcSearchControls.isLimitSearchResults());
    }

    @Override
    public boolean isEmpty() {
        return isEmpty(groupId) && isEmpty(artifactId) && isEmpty(version) && isEmpty(classifier);
    }

    @Override
    public boolean isWildcardsOnly() {
        String expression = StringUtils.remove(getSearchExpression(), '-');
        return StringUtils.isBlank(expression) || isWildcardsOnly(expression);
    }

    public String getSearchExpression() {
        StringBuilder sb = new StringBuilder();
        if (!isEmpty(groupId)) {
            sb.append(groupId).append("-");
        }
        if (!isEmpty(artifactId)) {
            sb.append(artifactId).append("-");
        }
        if (!isEmpty(version)) {
            sb.append(version).append("-");
        }
        if (!isEmpty(classifier)) {
            sb.append(classifier);
        }

        String expression = sb.toString();
        if (expression.endsWith("-")) {
            expression = expression.substring(0, (expression.length() - 1));
        }

        return expression;
    }

    private boolean isEmpty(String field) {
        return StringUtils.isEmpty(field);
    }

    public enum Direction {
        NONE, ASC, DESC;
    }
}