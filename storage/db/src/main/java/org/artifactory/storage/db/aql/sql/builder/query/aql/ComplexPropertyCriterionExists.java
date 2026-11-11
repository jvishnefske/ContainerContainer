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
import org.artifactory.aql.model.AqlVariable;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;

import java.util.List;

import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph.getFromFieldToProperties;
import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph.getLinkTable;

/**
 * @author Gidi Shabat
 */
public class ComplexPropertyCriterionExists extends ComplexPropertyCriterion {

    public ComplexPropertyCriterionExists(List<AqlDomainEnum> subDomains, AqlVariable variable1, SqlTable table1,
            String comparatorName, AqlVariable variable2, SqlTable table2, boolean mspOperator) {
        super(subDomains, variable1, table1, comparatorName, variable2, table2, mspOperator);
    }

    /**
     * Converts propertyCriteria to Sql criteria
     */
    @Override
    public String toSql(List<Object> params) throws AqlException {
        // Get both variable which are Values (this is property criteria)
        AqlVariable value1 = getVariable1();
        AqlVariable value2 = getVariable2();
        // Get both tables which are node_props tables (this is property criteria)
        SqlTable table1 = getTable1();
        // Get the ComparatorEnum
        AqlComparatorEnum comparatorEnum = AqlComparatorEnum.value(getComparatorName());
        return createSqlComplexPropertyCriteria(comparatorEnum, value1, table1, value2, params);
    }

    private String createSqlComplexPropertyCriteria(AqlComparatorEnum comparatorEnum, AqlVariable variable1,
            SqlTable table1,
            AqlVariable variable2, List<Object> params) {
        switch (comparatorEnum) {
            case notMatches: {
                return createWhereForLikeCriteria(variable1, table1, variable2, params, NOT_EXISTS, false);
            }
            case notEquals: {
                return createWhereForEqualsCriteria(variable1, table1, variable2, params, NOT_EXISTS, false);
            }
            case equals: {
                return createWhereForEqualsCriteria(variable1, table1, variable2, params, EXISTS, false);
            }
            case equalsValueIgnoreCase: {
                // Exact key, case-folded value: prop_key = ? AND LOWER(prop_value) = ?
                return createWhereForEqualsCriteria(variable1, table1, variable2, params, EXISTS, true);
            }
            case matches: {
                return createWhereForLikeCriteria(variable1, table1, variable2, params, EXISTS, false);
            }
            case matchesIgnoreCase: {
                return createWhereForLikeCriteria(variable1, table1, variable2, params, EXISTS, true);
            }
            default:
                throw new IllegalStateException("Should not reach to the point of code");
        }
    }

    @Nonnull
    private String createWhereForEqualsCriteria(AqlVariable variable1, SqlTable table1, AqlVariable variable2,
            List<Object> params, String condition, boolean caseInsensitiveValue) {
        StringBuilder where = createWhere(table1, condition, caseInsensitiveValue);
        Object param1 = resolveParam(variable1);
        Object param2 = resolveParam(variable2);
        params.add(param1);
        params.add(param2);
        where.append(" = ")
                .append(PARAMETER_PLACEHOLDER).append(" ))");
        return where.toString();
    }

    @Nonnull
    private String createWhereForLikeCriteria(AqlVariable variable1, SqlTable table1, AqlVariable variable2,
            List<Object> params, String condition, boolean caseInsensitive) {
        StringBuilder where = createWhere(table1, condition, caseInsensitive);
        Object param1 = resolveParam(variable1);
        String param2 = resolveParamForSqlLikeQuery(variable2);
        params.add(param1);
        params.add(param2);
        where.append(" like ")
                .append(PARAMETER_PLACEHOLDER).append(generateEscapingCriteria(param2)).append(" ))");
        return where.toString();
    }

    private StringBuilder createWhere(SqlTable table, String condition, boolean caseInsensitive) {
        String tableName = table.getTableName();
        String fieldName = getFromFieldToProperties(table.getTable()).name();

        String subSelectAlias = getSubSelectAlias();
        String shortAlias = getTable1().getTable().alias + "sub.";
        StringBuilder where = new StringBuilder(" ( ");
        where.append(condition).append("( ").append("select 1")
                .append(" from ")
                .append(tableName).append(" ")
                .append(getTable1().getTable().alias).append("sub").append(" where ")
                .append(fieldToSql(shortAlias, fieldName)).append(" = ").append(subSelectAlias).append(fieldName)
                .append(" and ")
                .append(fieldToSql(shortAlias, "prop_key"))
                .append(" = ").append(PARAMETER_PLACEHOLDER).append(" and ");
        if (caseInsensitive) {
            where.append("LOWER(").append(fieldToSql(shortAlias, "prop_value")).append(")");
        } else {
            where.append(fieldToSql(shortAlias, "prop_value"));
        }

        return where;
    }

    @Nonnull
    private String getSubSelectAlias() {
        // if only one table in subdomain it means we need to have the alias of the prop table, else we need the one
        // table prior props
        return (getSubDomains().size() == 1) ?
                getLinkTable(getTable1().getTable(), getSubDomains().get(getSubDomains().size() - 1)).getAlias() :
                getLinkTable(getTable1().getTable(), getSubDomains().get(getSubDomains().size() - 2)).getAlias();
    }
}
