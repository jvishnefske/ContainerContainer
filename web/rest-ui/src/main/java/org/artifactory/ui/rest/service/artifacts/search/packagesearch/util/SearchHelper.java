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

package org.artifactory.ui.rest.service.artifacts.search.packagesearch.util;

import com.google.common.base.Strings;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.FieldSearchCriteria;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.criteria.PackageSearchCriteria;
import org.artifactory.ui.rest.model.artifacts.search.packagesearch.search.AqlUISearchModel;
import org.artifactory.ui.rest.service.artifacts.search.packagesearch.strategy.AqlUISearchStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Inbar Tal
 */
public class SearchHelper {

    private static final Logger log = LoggerFactory.getLogger(SearchHelper.class);

    static List<AqlUISearchStrategy> buildStrategiesFromSearchModel(List<AqlUISearchModel> searches) {
        return searches.stream()
                .map(search -> getStrategyByFieldId(search)
                        .comparator(search.getComparator())
                        .values(search.getValues()))
                .collect(Collectors.toList());
    }

    /**
     * Tries each of the criteria enums for the strategy of the given model
     */
    private static AqlUISearchStrategy getStrategyByFieldId(AqlUISearchModel search) {
        final String id = search.getId();
        if (Strings.isNullOrEmpty(id)) {
            throw new IllegalArgumentException("Search model ID cannot be null or empty");
        }
        try {
            return PackageSearchCriteria.getStrategyByFieldId(id);
        } catch (IllegalArgumentException iae) {
            log.warn("", iae);
            return FieldSearchCriteria.getStrategyByFieldId(id);
        }
    }
}
