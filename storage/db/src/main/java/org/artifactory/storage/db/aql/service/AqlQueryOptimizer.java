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

import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.aql.service.optimizer.DropDistinctIfUsingUniqueValues;
import org.artifactory.storage.db.aql.service.optimizer.FileTypeOptimization;
import org.artifactory.storage.db.aql.service.optimizer.FromLeftOuterJoinToExists;
import org.artifactory.storage.db.aql.service.optimizer.LowerNodeNameOptimizer;
import org.artifactory.storage.db.aql.service.optimizer.OptimizationStrategy;
import org.artifactory.storage.db.aql.service.optimizer.PropertyCriteriaRelatedWithOr;
import org.artifactory.storage.db.aql.service.optimizer.TopLevelOrToUnion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.jfrog.storage.DbType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.artifactory.common.ConstantValues.enableAqlDropDistinctOptimizationForNodes;
import static org.artifactory.common.ConstantValues.enableAqlNodeNameToLowerCaseOptimization;
import static org.jfrog.storage.DbType.DERBY;
import static org.jfrog.storage.DbType.MARIADB;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.MYSQL;
import static org.jfrog.storage.DbType.ORACLE;
import static org.jfrog.storage.DbType.POSTGRESQL;

/**
 * @author Gidi Shabat
 */
public class AqlQueryOptimizer {
    private QueryOptimizer optimizer;
    private static final Set<DbType> supportedDBTypes = Set.of(DERBY, POSTGRESQL, MYSQL, MARIADB, ORACLE, MSSQL);


    public AqlQueryOptimizer(DbType dbType) {
        // Since the optimisation in each database type is different we need to get the database type and accordingly
        //init it relevant optimizations
        optimizer = loadOptimizerForDatabase(dbType);
    }

    private QueryOptimizer loadOptimizerForDatabase(DbType dbType) {
        ArrayList<OptimizationStrategy> strategies =
                new ArrayList<>(List.of(new FileTypeOptimization(), new PropertyCriteriaRelatedWithOr()));
        if (supportedDBTypes.contains(dbType)) {
            if (ConstantValues.enableAqlOptimizationForFromLeftOuterJoinToSubSelect.getBoolean()) {
                strategies.addAll(List.of(new FromLeftOuterJoinToExists(), new TopLevelOrToUnion()));
            }
            if (enableAqlDropDistinctOptimizationForNodes.getBoolean()) {
                strategies.add(new DropDistinctIfUsingUniqueValues());
            }
            if (POSTGRESQL.equals(dbType) && enableAqlNodeNameToLowerCaseOptimization.getBoolean()){
                strategies.add(new LowerNodeNameOptimizer());
            }
            return new QueryOptimizer(strategies);
        }
        else {
            throw new RuntimeException("Unsupported database type" + dbType.name());
        }
    }

    public void optimize(AqlQuery aqlQuery) {
        optimizer.optimize(aqlQuery);
    }
}
