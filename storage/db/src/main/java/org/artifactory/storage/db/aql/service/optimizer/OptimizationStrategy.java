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

package org.artifactory.storage.db.aql.service.optimizer;

import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlOperatorEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.CloseParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.MspAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OpenParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ResultFilterAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.List;

import static org.artifactory.aql.model.AqlFunctionFieldEnum.COUNT;

/**
 * @author Gidi Shabat
 */
public abstract class OptimizationStrategy {

    public <T extends AqlRowResult> void doJob(AqlQuery<T> aqlQuery) {
        String transformation = transformToCharacterRepresentation(aqlQuery);
        optimize(aqlQuery, transformation);
    }

    public abstract <T extends AqlRowResult> void optimize(AqlQuery<T> aqlQuery, String transformation);

    /**
     * AQL to string transformation function (method)
     * This method transforms the AqlQuery into string representation. The "function" is being used to
     * Detect patterns that can be optimize.
     * <p/>
     * Result
     * Each Aql query element is represented by one character:
     * SimpleCriteria on property key           =             k
     * SimpleCriteria on property value         =             v
     * SimpleCriteria on property key (result)  =             K
     * SimpleCriteria on property value (result)=             V
     * SimpleCriteria on any other field        =             c
     * PropertyCriteria                         =             p
     * OpenParenthesisAqlElement                =             (
     * CloseParenthesisAqlElement               =             )
     * OperatorQueryElement - and               =             a
     * OperatorQueryElement - or                =             o
     * MspAqlElement                            =             m
     * ResultFilterAqlElement                   =             r
     */
    private <T extends AqlRowResult> String transformToCharacterRepresentation(AqlQuery<T> aqlQuery) {
        List<AqlQueryElement> aqlElements = aqlQuery.getAqlElements();
        StringBuilder builder = new StringBuilder();
        for (AqlQueryElement aqlElement : aqlElements) {
            if (aqlElement instanceof ComplexPropertyCriterion) {
                builder.append("p");
            }
            if (aqlElement instanceof OpenParenthesisAqlElement) {
                builder.append("(");
            }
            if (aqlElement instanceof CloseParenthesisAqlElement) {
                builder.append(")");
            }
            if (aqlElement instanceof ResultFilterAqlElement) {
                builder.append("r");
            }
            if (aqlElement instanceof MspAqlElement) {
                builder.append("m");
            }
            if (aqlElement instanceof SimpleCriterion || aqlElement instanceof SimplePropertyCriterion) {
                AqlField field = (AqlField) ((Criterion) aqlElement).getVariable1();
                SqlTable table1 = ((Criterion) aqlElement).getTable1();
                if (table1.getId() >= 100) {
                    if (AqlPhysicalFieldEnum.isPropsKey(field.getFieldEnum())) {
                        builder.append("k");
                    } else if (AqlPhysicalFieldEnum.isPropsValue(field.getFieldEnum())) {
                        builder.append("v");
                    } else {
                        builder.append("c");
                    }
                } else {
                    if (AqlPhysicalFieldEnum.isPropsKey(field.getFieldEnum())) {
                        builder.append("K");
                    } else if (AqlPhysicalFieldEnum.isPropsValue(field.getFieldEnum())) {
                        builder.append("V");
                    } else {
                        builder.append("C");
                    }
                }
            }
            if (aqlElement instanceof OperatorQueryElement operatorQueryElement) {
                AqlOperatorEnum operator = operatorQueryElement.getOperatorEnum();
                if (AqlOperatorEnum.and == operator) {
                    builder.append("a");
                } else {
                    builder.append("o");
                }
            }
        }
        return builder.toString();
    }

    protected static <T extends AqlRowResult> boolean shouldSkipOptimization(AqlQuery<T> aqlQuery) {
        return (isCountQuery(aqlQuery) && !aqlQuery.isDistinct()) || aqlQuery.isNullExists() ||
                isBuildsTableExist(aqlQuery);
    }

    private static <T extends AqlRowResult> boolean isBuildsTableExist(AqlQuery<T> aqlQuery) {
        return ConstantValues.disableAqlOptimizationForBuilds.getBoolean() && aqlQuery.getAqlElements().stream()
                .filter(Criterion.class::isInstance)
                .map(Criterion.class::cast)
                .anyMatch(criterion -> isBuildsTable(criterion.getTable1()) || isBuildsTable(criterion.getTable2()));
    }

    private static boolean isBuildsTable(SqlTable table) {
        return table.getTable().equals(SqlTableEnum.builds);
    }

    private static <T extends AqlRowResult> boolean isCountQuery(AqlQuery<T> aqlQuery) {
        return aqlQuery.getSqlFunction() != null &&
                COUNT.equals(aqlQuery.getSqlFunction().functionFieldEnum());
    }
}
