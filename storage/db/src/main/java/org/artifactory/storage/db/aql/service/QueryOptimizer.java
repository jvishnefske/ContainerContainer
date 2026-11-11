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

package org.artifactory.storage.db.aql.service;

import org.artifactory.storage.db.aql.service.optimizer.OptimizationStrategy;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;

import java.util.List;

/**
 * @author Gidi Shabat
 */
public class QueryOptimizer {
    private final List<OptimizationStrategy> strategies;

    public QueryOptimizer(List<OptimizationStrategy> strategies) {
        this.strategies = strategies;
    }

    public void optimize(AqlQuery aqlQuery) {
        for (OptimizationStrategy strategy : strategies) {
            strategy.doJob(aqlQuery);
        }
    }
}
