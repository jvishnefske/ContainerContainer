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

package org.artifactory.search.property;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.property.PropertySearchControls;
import org.artifactory.api.search.property.PropertySearchResult;
import org.artifactory.aql.AqlConverts;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.result.AqlLazyResult;
import org.artifactory.aql.result.rows.AqlItem;
import org.artifactory.search.AqlSearcherBase;
import org.artifactory.search.stats.SearchStatsThreadLocal;

import java.util.List;
import java.util.Set;

import static org.artifactory.aql.api.internal.AqlBase.AndClause;
import static org.artifactory.aql.api.internal.AqlBase.CriteriaClause;
import static org.artifactory.aql.api.internal.AqlBase.OrClause;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.freezeJoin;
import static org.artifactory.aql.model.AqlComparatorEnum.equals;
import static org.artifactory.common.ConstantValues.aqlQueryTimeoutForUi;

/**
 * @author Gidi Shabat
 * @author Yossi Shaul
 */
@Slf4j
public class PropertySearcher extends AqlSearcherBase<PropertySearchControls, PropertySearchResult> {
    private static final String FAILED_EXEC_MSG = "Error executing search AQL query { } ";

    @Override
    public ItemSearchResults<PropertySearchResult> doSearch(PropertySearchControls controls) {
        Multimap<String, String> properties = controls.getProperties();

        AndClause<AqlApiItem> and = and();
        // Find files and folders
        and.append(AqlApiItem.type().equal("any"));
        OrClause<AqlApiItem> reposToSearchFilter = getSelectedReposForSearchClause(controls);
        if (!reposToSearchFilter.isEmpty()) {
            and.append(reposToSearchFilter);
        }
        Set<String> openness = controls.getPropertyKeysByOpenness(true);
        for (String key : openness) {
            for (String value : properties.get(key)) {
                if (value == null || "*".equals(value.trim()) || StringUtils.isEmpty(value)) {
                    createPropertyKeyCriteria(and, key);
                } else {
                    createPropertyCriteria(and, key, value);
                }
            }
        }
        Set<String> closeness = controls.getPropertyKeysByOpenness(false);
        for (String key : closeness) {
            for (String value : properties.get(key)) {
                if (value == null) {
                    and.append(
                            AqlApiItem.property().key().equal(key)
                    );
                } else {
                    and.append(
                            AqlApiItem.property().property(key, equals, value)
                    );
                }
            }
        }
        long start = System.currentTimeMillis();
        List<AqlItem> items = exceptionalQuery(and, controls);
        long end = System.currentTimeMillis();
        //Return global results list
        long totalResultCount = items.size();
        Set<PropertySearchResult> globalResults = Sets.newLinkedHashSet();
        for (AqlItem aqlItem : items) {
            globalResults.add(new PropertySearchResult(AqlConverts.toItemInfo.apply(aqlItem)));
        }
        SearchStatsThreadLocal.setSearchType("artifact-property");
        SearchStatsThreadLocal.addDbDuration(end - start);
        SearchStatsThreadLocal.addDbResults(totalResultCount);
        SearchStatsThreadLocal.setSearchControls(controls);
        return new ItemSearchResults<>(Lists.newArrayList(globalResults), totalResultCount);
    }

    private List<AqlItem> exceptionalQuery(AndClause<AqlApiItem> and, PropertySearchControls controls) {
        final AqlLazyResult<AqlItem> result = executeQuery(and, controls, aqlQueryTimeoutForUi.getLong());
        return result.asStream(e -> {
            if (e != null) {
                log.error(FAILED_EXEC_MSG, e);
            }
        }
        ).toList();
    }

    private void createPropertyCriteria(AndClause<AqlApiItem> and, String key, String value) {
        CriteriaClause<AqlApiItem> keyCriteria;
        CriteriaClause<AqlApiItem> valueCriteria;
        if (key.contains("*") || key.contains("?")) {
            keyCriteria = AqlApiItem.property().key().matches(key);
        } else {
            keyCriteria = AqlApiItem.property().key().equal(key);
        }
        if (value.contains("*") || value.contains("?")) {
            valueCriteria = AqlApiItem.property().value().matches(value);
        } else {
            valueCriteria = AqlApiItem.property().value().equal(value);
        }
        and.append(
                freezeJoin(
                        and(
                                keyCriteria,
                                valueCriteria
                        )
                )
        );
    }

    private void createPropertyKeyCriteria(AndClause<AqlApiItem> and, String key) {
        if (key.contains("*") || key.contains("?")) {
            and.append(
                    AqlApiItem.property().key().matches(key)
            );
        } else {
            and.append(
                    AqlApiItem.property().key().equal(key)
            );
        }
    }
}