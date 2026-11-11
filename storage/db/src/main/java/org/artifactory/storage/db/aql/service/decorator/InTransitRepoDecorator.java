/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2016 JFrog Ltd.
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

package org.artifactory.storage.db.aql.service.decorator;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlValue;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;

/**
 * @author Eli Skoran
 */
@Slf4j
public class InTransitRepoDecorator extends SpecialReposDecorator {

    @Override
    <T extends AqlRowResult> boolean shouldSkipDecoration(@NonNull AqlQuery<T> aqlQuery) {
        boolean filteredByRepoEqual = super.isFilteredByRepoEqual(aqlQuery);
        if (filteredByRepoEqual) {
            return !containsInTransitInAql(aqlQuery);
        } else {
            return false;
        }
    }

    private <T extends AqlRowResult> boolean containsInTransitInAql(@NonNull AqlQuery<T> aqlQuery) {
        // only if the request is for a repo equal, we need to check if it doesn't include _intransit.
        boolean containsInTransit;
        try {
            containsInTransit = aqlQuery.getAqlElements().stream()
                    .filter(SimpleCriterion.class::isInstance)
                    .map(Criterion.class::cast)
                    .filter(curr -> ((AqlField) curr.getVariable1()).getFieldEnum().getSignature().equals("repo"))
                    .map(curr -> ((AqlValue) curr.getVariable2()).getValue())
                    .toList()
                    .contains(getRepoName());
        } catch (Exception e) {
            log.error("Error while trying to check _intransit repo in the AQL.", e);
            // to be safe, we will assume it doesnt include "_intransit"
            containsInTransit = false;
        }

        return containsInTransit;
    }

    @Override
    <T extends AqlRowResult> void decoratePropertiesSearch(AqlQuery<T> aqlQuery) {
        // Do nothing, no need to add special props filter for _intransit repo.
    }

    @Override
    String getRepoName() {
        return "_intransit";
    }

    @Override
    String getPropertyName() {
        return "";
    }
}