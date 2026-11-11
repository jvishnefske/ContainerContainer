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

package org.artifactory.storage.db.aql.sql.builder.query.aql;

import jakarta.annotation.Nonnull;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlComparatorEnum;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlField;
import org.artifactory.aql.model.AqlVariable;
import org.artifactory.storage.db.aql.sql.builder.query.sql.AqlToSqlQueryBuilderException;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;

import java.util.List;

import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph.getFromFieldToProperties;
import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph.getLinkTable;

/**
 * This class represent simple criteria which contains field comparator and value
 * For example "_artifact_repo" "$equals" "libs-release-local"
 *
 * @author Gidi Shabat
 */
public class SimplePropertyCriterionExists extends SimplePropertyCriterion {

    public SimplePropertyCriterionExists(List<AqlDomainEnum> subDomains, AqlVariable variable1, SqlTable table1,
            String comparatorName, AqlVariable variable2, SqlTable table2, boolean mspOperator) {
        super(subDomains, variable1, table1, comparatorName, variable2, table2, mspOperator);
    }

    /**
     * Convert simpleCriteria to sql criteria
     *
     * @throws AqlException
     */
    @Override
    public String toSql(List<Object> params) throws AqlException {
        // Get both variable which are Field and Value (this is simple criteria)
        AqlVariable variable1 = getVariable1();
        AqlVariable variable2 = getVariable2();
        // Convert criteria into Sql
        AqlComparatorEnum comparatorEnum = AqlComparatorEnum.value(getComparatorName());
        return createSqlPropertyCriteria(comparatorEnum, variable1, variable2, params);
    }

    public String createSqlPropertyCriteria(AqlComparatorEnum comparatorEnum, AqlVariable variable1,
            AqlVariable variable2, List<Object> params) {
        switch (comparatorEnum) {
            case notMatches: {
                return generatePropertyNotMatchQuery(variable1, variable2, params);
            }
            case notEquals: {
                return createWhereEqualsCriteria(variable1, variable2, params, NOT_EXISTS).toString();
            }
            case matches: {
                return generatePropertyMatchQuery(variable1, variable2, params, false);
            }
            case matchesIgnoreCase: {
                return generatePropertyMatchQuery(variable1, variable2, params, true);
            }
            case equals: {
                return createWhereEqualsCriteria(variable1, variable2, params, EXISTS).toString();
            }
            default:
                throw new IllegalStateException("Should not reach to the point of code");
        }
    }

    String generatePropertyNotMatchQuery(AqlVariable variable1, AqlVariable variable2, List<Object> params) {
        String param = resolveParamForSqlLikeQuery(variable2);
        params.add(param);
        if (variable2 instanceof AqlField) {
            throw new AqlToSqlQueryBuilderException(
                    "Illegal syntax the 'not match' operator is allowed only with 'value' in right side of the " +
                            "criteria.");
        }

        return createWhereMatchCriteria(variable1, param, NOT_EXISTS, false).toString();
    }

    String generatePropertyMatchQuery(AqlVariable variable1, AqlVariable variable2, List<Object> params,
            boolean isCaseInsensitive) {
        String param = resolveParamForSqlLikeQuery(variable2);
        params.add(param);
        if (variable2 instanceof AqlField) {
            throw new AqlToSqlQueryBuilderException(
                    "Illegal syntax the 'match' operator is allowed only with 'value' in right side of the criteria.");
        }
        return createWhereMatchCriteria(variable1, param, EXISTS, isCaseInsensitive).toString();
    }

    private StringBuilder createWhereMatchCriteria(AqlVariable variable, String param, String condition,
            boolean caseInsensitive) {
        String tableName = getTable1().getTableName();
        String fieldName = getFromFieldToProperties(getTable1().getTable()).name();
        String subSelectAlias = getSubSelectAlias();
        String shortAlias = getTable1().getTable().alias + "sub.";
        StringBuilder where = new StringBuilder(" ( ");

        where.append(condition).append("( ").append("select 1")
                .append(" from ")
                .append(tableName).append(" ").append(getTable1().getTable().alias).append("sub").append(" where ")
                .append(fieldToSql(shortAlias, fieldName)).append(" = ").append(subSelectAlias).append(fieldName)
                .append(" and ");
        if (caseInsensitive) {
            where.append("LOWER(").append(fieldToSql(shortAlias, variable)).append(")");
        } else {
            where.append(fieldToSql(shortAlias, variable));
        }
        where.append(" like ").append(PARAMETER_PLACEHOLDER).append(generateEscapingCriteria(param)).append(" ))");

        return where;
    }

    private StringBuilder createWhereEqualsCriteria(AqlVariable variable1, AqlVariable variable2, List<Object> params,
            String condition) {
        String tableName = getTable1().getTableName();
        String fieldName = getFromFieldToProperties(getTable1().getTable()).name();
        String subSelectAlias = getSubSelectAlias();

        String shortAlias = getTable1().getTable().alias + "sub.";
        Object param = resolveParam(variable2);
        StringBuilder where = new StringBuilder(" ( ");
        where.append(condition).append("( ").append("select 1")
                .append(" from ")
                .append(tableName).append(" ").append(getTable1().getTable().alias).append("sub").append(" where ")
                .append(fieldToSql(shortAlias, fieldName)).append(" = ").append(subSelectAlias).append(fieldName)
                .append(" and ");
        if (param != null) {
            params.add(param);
            where.append(fieldToSql(shortAlias, variable1)).append(" = ").append(PARAMETER_PLACEHOLDER)
                    .append(" ))");
        } else {
            where.append(isNullSql((AqlField) variable1)).append(" ))");
        }
        return where;
    }

    @Nonnull
    // if only one table in subdomain it means we need to have the alias of the prop table, else we need the one
    // table prior props
    private String getSubSelectAlias() {
        String propsTableForSelect =
                getLinkTable(getTable1().getTable(), getSubDomains().get(getSubDomains().size() - 1)).getAlias();
        return (getSubDomains().size() == 1) ? propsTableForSelect :
                getLinkTable(getTable1().getTable(), getSubDomains().get(getSubDomains().size() - 2)).getAlias();
    }
}
