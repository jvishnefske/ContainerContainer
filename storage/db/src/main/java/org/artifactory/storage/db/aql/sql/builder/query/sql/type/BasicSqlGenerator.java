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

package org.artifactory.storage.db.aql.sql.builder.query.sql.type;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.artifactory.aql.AqlException;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlFieldEnum;
import org.artifactory.aql.model.AqlFieldEnumSwitch;
import org.artifactory.aql.model.AqlFunctionFieldEnum;
import org.artifactory.aql.model.AqlLogicalFieldEnum;
import org.artifactory.aql.model.AqlOperatorEnum;
import org.artifactory.aql.model.AqlPhysicalFieldEnum;
import org.artifactory.aql.model.AqlSqlFunction;
import org.artifactory.aql.model.AqlTableFieldsEnum;
import org.artifactory.aql.model.DomainSensitiveField;
import org.artifactory.aql.result.rows.AqlRowResult;
import org.artifactory.storage.db.aql.sql.builder.links.TableLink;
import org.artifactory.storage.db.aql.sql.builder.links.TableLinkBrowser;
import org.artifactory.storage.db.aql.sql.builder.links.TableLinkRelation;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQuery;
import org.artifactory.storage.db.aql.sql.builder.query.aql.AqlQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.CloseParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.ComplexPropertyCriterionExists;
import org.artifactory.storage.db.aql.sql.builder.query.aql.Criterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OpenParenthesisAqlElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.OperatorQueryElement;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimpleCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterion;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SimplePropertyCriterionExists;
import org.artifactory.storage.db.aql.sql.builder.query.aql.SortDetails;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.AqlFieldExtensionEnum;
import org.artifactory.storage.db.aql.sql.model.AqlFunctionFieldExtensionEnum;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;
import org.jfrog.security.util.Pair;
import org.springframework.util.StringUtils;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph.domainToTable;
import static org.artifactory.storage.db.aql.sql.builder.query.sql.type.AqlTableGraph.tablesLinksMap;
import static org.artifactory.storage.db.aql.sql.model.AqlFieldExtensionEnum.getExtensionFor;

/**
 * This is actually the class that contains all the code that converts the AqlQuery to sqlQuery.
 *
 * @author Gidi Shabat
 */
public abstract class BasicSqlGenerator {
    private static final Function<DomainSensitiveField, DomainSensitiveTable> toTables =
            new Function<>() {
                @Nullable
                @Override
                public DomainSensitiveTable apply(@Nullable DomainSensitiveField input) {
                    if (input == null) {
                        return null;
                    }
                    return input.getField().doSwitch(new AqlFieldEnumSwitch<>() {
                        @Override
                        public DomainSensitiveTable caseOf(AqlLogicalFieldEnum fieldEnum) {
                            return null;
                        }

                        @Override
                        public DomainSensitiveTable caseOf(AqlPhysicalFieldEnum fieldEnum) {
                            AqlFieldExtensionEnum extension = getExtensionFor(fieldEnum);
                            List<SqlTableEnum> tables = generateTableListFromSubDomainAndField(input.getSubDomains());
                            SqlTable table = tablesLinksMap.get(extension.table).getTable();
                            return new DomainSensitiveTable(table, tables);
                        }

                        @Override
                        public DomainSensitiveTable caseOf(AqlFunctionFieldEnum fieldEnum) {
                            return null;
                        }
                    });
                }
            };
    private static final Function<AqlQueryElement, DomainSensitiveTable> firstTableFromCriteria =
            new Function<>() {
                @Nullable
                @Override
                public DomainSensitiveTable apply(@Nullable AqlQueryElement input) {
                    SqlTable table = input != null ? ((Criterion) input).getTable1() : null;
                    if (table != null) {
                        List<AqlDomainEnum> subDomains = ((Criterion) input).getSubDomains();

                        // When we are having Sub Select criteria, then we need to remove the last left outer join
                        // from the link tables. If we have two tables or less that mean we don't need to have left
                        // outer join at all. For more than two we will remove the last table (it will be prop table)
                        if (input instanceof SimplePropertyCriterionExists ||
                                input instanceof ComplexPropertyCriterionExists) {
                            if (subDomains.size() > 2) {
                                AqlDomainEnum val = subDomains.get(subDomains.size() - 2);
                                table = new SqlTable(domainToTable(val));
                            } else {
                                return null;
                            }
                        }
                        List<SqlTableEnum> tables =
                                generateTableListFromSubDomainAndField(subDomains);
                        return new DomainSensitiveTable(table, tables);

                    }
                    return null;
                }
            };
    private static final Predicate<DomainSensitiveTable> notNull = Objects::nonNull;
    private static final Predicate<AqlQueryElement> criteriasOnly = Criterion.class::isInstance;
    private static final Function<DomainSensitiveTable, SqlTableEnum> toTableEnum =
            new Function<>() {
                @Nullable
                @Override
                public SqlTableEnum apply(@Nullable DomainSensitiveTable input) {
                    return input != null ? input.getTable().getTable() : null;
                }
            };
    private static final ThreadLocal<Boolean> USE_INNER_JOIN = new ThreadLocal<>();
    public final Map<SqlTableEnum, Map<SqlTableEnum, List<TableLinkRelation>>> tableRouteMap;

    /**
     * The constructor scans the table schema and creates a map that contains the shortest route between two tables
     */
    protected BasicSqlGenerator() {
        Map<SqlTableEnum, Map<SqlTableEnum, List<TableLinkRelation>>> routeMap = new EnumMap<>(SqlTableEnum.class);
        for (TableLink from : tablesLinksMap.values()) {
            for (TableLink to : tablesLinksMap.values()) {
                List<TableLinkRelation> route = findShortestPathBetween(from, to);
                Map<SqlTableEnum, List<TableLinkRelation>> toRouteMap =
                        routeMap.computeIfAbsent(from.getTableEnum(), k -> new HashMap<>());
                toRouteMap.put(to.getTableEnum(), route);
            }
        }
        tableRouteMap = routeMap;
    }

    private static List<SqlTableEnum> generateTableListFromSubDomainAndField(List<AqlDomainEnum> subDomains) {
        if (subDomains.size() > 1) {
            return subDomains.stream().limit(subDomains.size() - 1L).map(AqlTableGraph::domainToTable).toList();
        }

        return List.of(domainToTable(subDomains.get(0)));
    }

    /**
     * The method generates the result part of the SQL query
     */
    public String results(AqlQuery aqlQuery) {
        if (!aqlQuery.getResultFields().isEmpty() && aqlQuery.getSqlFunction() != null) {
            throw new IllegalStateException("A SQL function bearing AQL query cannot have an 'include' element");
        }
        StringBuilder result = new StringBuilder();
        result.append(" ");

        // Physical fields
        if (!aqlQuery.getResultFields().isEmpty()) {
            List<DomainSensitiveField> resultFields = aqlQuery.getResultFields();
            boolean[] first = new boolean[] {true};
            for (DomainSensitiveField nextField : resultFields) {
                applyFieldEnumSwitchOnResultString(result, nextField.getField(), first, aqlQuery);
            }
            result.append(" ");
            return result.toString();
        }
        // Function field
        if (aqlQuery.getSqlFunction() != null && aqlQuery.getSqlFunction().functionFieldEnum() != null) {
            applyFieldEnumSwitchOnResultString(result, aqlQuery.getSqlFunction().functionFieldEnum(),
                    new boolean[] {false}, aqlQuery);
            result.append(" ");
        }
        return result.toString();
    }

    private void applyFieldEnumSwitchOnResultString(StringBuilder result, AqlFieldEnum field,
            boolean[] omitLeadingComma, AqlQuery aqlQuery) {
        field.doSwitch(new AqlFieldEnumSwitch<Void>() {
            @Override
            public Void caseOf(AqlLogicalFieldEnum fieldEnum) {
                return null;
            }

            @Override
            public Void caseOf(AqlPhysicalFieldEnum fieldEnum) {
                if (!omitLeadingComma[0]) {
                    result.append(",");
                } else {
                    omitLeadingComma[0] = false;
                }
                AqlFieldExtensionEnum next = getExtensionFor(fieldEnum);
                SqlTable table = tablesLinksMap.get(next.table).getTable();
                result.append(table.getAlias()).append(next.tableField);
                result.append(" as ").append(fieldEnum.name());
                return null;
            }

            @Override
            public Void caseOf(AqlFunctionFieldEnum fieldEnum) {
                AqlSqlFunction sqlFunction = aqlQuery.getSqlFunction();
                AqlFunctionFieldEnum functionFieldEnum = sqlFunction.functionFieldEnum();
                AqlFunctionFieldExtensionEnum functionFieldExtension =
                        AqlFunctionFieldExtensionEnum.getExtensionFor(functionFieldEnum);
                String canonicalFunctionSqlForm = functionFieldExtension.toCanonicalSqlForm(sqlFunction);
                if (!aqlQuery.getUnionSubQueries().isEmpty()) {
                    canonicalFunctionSqlForm =
                            StringUtils.replace(canonicalFunctionSqlForm, functionFieldEnum.getSignature() + "(", "");
                    canonicalFunctionSqlForm = StringUtils.trimTrailingCharacter(canonicalFunctionSqlForm, ')');
                }
                result.append(canonicalFunctionSqlForm);
                return null;
            }

        });
    }

    /**
     * This is one of the most important and complicated parts in the Aql mechanism
     * Its task is to create the tables declaration part in the SQL query
     * the method does this with the help "sub domains" : Each field in the result fields and in the criteria
     * contains a list of domain that represent the route to the main domain, so basically, in order to bind one field
     * to the other we can trace the sub domains and bind each field to the "Main Table"
     * The problem with tracing the sub domain is that there is no injective match between the tables and the domains
     * therefore we use the tablesLinksMap that contain the shortest route between two tables and help us to ensure
     * that in "threaded form" we will bind all the tables needed from the
     * "Field table" to the "Main table"
     */
    public String tables(AqlQuery aqlQuery) {
        Set<SqlTable> usedTables = new HashSet<>();
        StringBuilder join = new StringBuilder();
        join.append(" ");
        // Get all Result tables
        Iterable<DomainSensitiveTable> resultTables = Iterables.transform(aqlQuery.getResultFields(), toTables);
        // Find all the criterias
        Iterable<AqlQueryElement> filter = Iterables.filter(aqlQuery.getAqlElements(), criteriasOnly);
        // Get the tables from the criterias
        Iterable<DomainSensitiveTable> criteriasTables = Iterables.transform(filter, firstTableFromCriteria);
        // Concatenate the resultTables and the criteriasTables
        Iterable<DomainSensitiveTable> allTables = Iterables.concat(resultTables, criteriasTables);
        // Resolve  Join type (inner join or left outer join) for better performance
        AqlJoinTypeEnum joinTypeEnum = resolveJoinType(allTables);
        // Clean null tables if exists
        allTables = Iterables.filter(allTables, notNull);
        SqlTableEnum[] mainTables = getMainTables();
        Map<Pair<SqlTable, AqlTableFieldsEnum>, Pair<String, String>> overrideJoins = new HashMap<>();
        if (mainTables.length > 1) {
            joinMainTables(mainTables, join, usedTables, overrideJoins);
        } else {
            SqlTable mainTable = tablesLinksMap.get(mainTables[0]).getTable();
            // Add the single main table as first table
            appendFirstTable(mainTable, join, usedTables);
        }
        for (DomainSensitiveTable table : allTables) {
            TableLink to;
            // Resolve the first table : which is always the "Main Table"
            SqlTableEnum fromTableEnum = table.getTables().get(0);
            // Find the route to the target ("to") table and add a join for each table in the route
            TableLink from = tablesLinksMap.get(fromTableEnum);
            for (int i = 1; i < table.getTables().size(); i++) {
                SqlTableEnum toTableEnum = table.getTables().get(i);
                to = tablesLinksMap.get(toTableEnum);
                List<TableLinkRelation> relations = tableRouteMap.get(from.getTableEnum()).get(to.getTableEnum());
                generateJoinTables(relations, usedTables, join, joinTypeEnum, overrideJoins);
                from = to;
            }
            // Finally add a join to the field table
            to = tablesLinksMap.get(table.getTable().getTable());
            List<TableLinkRelation> relations = tableRouteMap.get(from.getTableEnum()).get(to.getTableEnum());
            generateJoinTables(relations, usedTables, join, joinTypeEnum, overrideJoins, table.getTable());
        }
        return join.toString() + " ";
    }

    private void joinMainTables(SqlTableEnum[] mainTables, StringBuilder joinBuilder, Set<SqlTable> usedTables,
            Map<Pair<SqlTable, AqlTableFieldsEnum>, Pair<String, String>> overrideJoins) {
        Map<SqlTableEnum, AqlTableFieldsEnum> tableIdField = new EnumMap<>(SqlTableEnum.class);
        /* a union for example:
        (select node_id as id from stats
         union
         select node_id as id from stats_remote) mainTableIds
         */
        String unionAlias = "mainTableIds";
        String unionIdAlias = "id";
        joinBuilder.append("(");
        for (int i = 0; i < mainTables.length; i++) {
            SqlTableEnum table = mainTables[i];
            if (i > 0) {
                joinBuilder.append("union ");
            }
            AqlTableFieldsEnum idField = Stream.of(table.getFields())
                    .filter(field -> field.getExtendedField().isId())
                    .findFirst()
                    .get().tableField;
            tableIdField.put(table, idField);
            joinBuilder.append("select ").append(idField).append(" as ").append(unionIdAlias).append(" from ")
                    .append(table.name()).append(" ");
        }
        joinBuilder.append(") ").append(unionAlias);
        /* join main tables with the ids union. for example:
        (select ... id from ... union ...) mainTableIds
        left outer join stats s on s.node_id = mainTableIds.id
        left outer join stats_remote sr on sr.node_id = mainTableIds.id
         */
        for (SqlTableEnum mainTable : mainTables) {
            SqlTable toTable = tablesLinksMap.get(mainTable).getTable();
            AqlTableFieldsEnum tableJoinField = tableIdField.get(toTable.getTable());
            String unionIdFieldPrefix = unionAlias + ".";
            appendTableJoin(toTable, tableJoinField, unionIdFieldPrefix, unionIdAlias, joinBuilder,
                    AqlJoinTypeEnum.leftOuterJoin);
            overrideJoins.put(new Pair<>(toTable, tableJoinField), new Pair<>(unionIdFieldPrefix, unionIdAlias));
            usedTables.add(toTable);
        }
    }

    /**
     * The method create the where part of the SQL query.
     * It actually scan all the criterias and Parenthesis elements in the AQL Query
     * and transform does elements into SQL syntax.
     *
     * @throws AqlException
     */
    public <T extends AqlRowResult> Pair<String, List<Object>> conditions(AqlQuery<T> aqlQuery) throws AqlException {
        StringBuilder condition = new StringBuilder();
        List<Object> params = new ArrayList<>();
        List<AqlQueryElement> aqlElements = aqlQuery.getAqlElements();
        for (AqlQueryElement aqlQueryElement : aqlElements) {
            if (aqlQueryElement instanceof ComplexPropertyCriterion || aqlQueryElement instanceof SimpleCriterion ||
                    aqlQueryElement instanceof SimplePropertyCriterion) {
                Criterion criterion = (Criterion) aqlQueryElement;
                condition.append(criterion.toSql(params));
            }
            if (aqlQueryElement instanceof OperatorQueryElement operatorQueryElement) {
                AqlOperatorEnum operatorEnum = operatorQueryElement.getOperatorEnum();
                condition.append(" ").append(operatorEnum.name());
            }
            if (aqlQueryElement instanceof OpenParenthesisAqlElement) {
                condition.append("(");
            }
            if (aqlQueryElement instanceof CloseParenthesisAqlElement) {
                condition.append(")");
            }
        }
        return new Pair(condition.toString() + " ", params);
    }

    private List<TableLinkRelation> findShortestPathBetween(TableLink from, TableLink to) {
        List<TableLinkRelation> relations = TableLinkBrowser.create().findPathTo(from, to, getExclude());
        if (relations == null) {
            ArrayList<TableLink> excludes = new ArrayList<>();
            relations = TableLinkBrowser.create().findPathTo(from, to, excludes);
        }
        relations = overrideRoute(relations);
        return relations;
    }

    protected abstract List<TableLink> getExclude();

    protected List<TableLinkRelation> overrideRoute(List<TableLinkRelation> route) {
        return route;
    }

    protected void generateJoinTables(List<TableLinkRelation> relations, Set<SqlTable> usedTables, StringBuilder join,
            AqlJoinTypeEnum joinTypeEnum, Map<Pair<SqlTable, AqlTableFieldsEnum>, Pair<String, String>> overrideJoins) {
        if (relations == null) {
            return;
        }
        for (TableLinkRelation relation : relations) {
            AqlTableFieldsEnum fromField = relation.getFromField();
            SqlTable fromTable = relation.getFromTable().getTable();
            AqlTableFieldsEnum toField = relation.getToFiled();
            SqlTable toTable = relation.getToTable().getTable();
            joinTable(toTable, toField, fromTable, fromField, usedTables, join, joinTypeEnum, overrideJoins);
        }
    }

    protected void generateJoinTables(List<TableLinkRelation> relations, Set<SqlTable> usedTables, StringBuilder join,
            AqlJoinTypeEnum joinTypeEnum, Map<Pair<SqlTable, AqlTableFieldsEnum>, Pair<String, String>> overrideJoins,
            SqlTable sqlTable) {
        if (relations == null) {
            return;
        }
        for (TableLinkRelation relation : relations) {
            AqlTableFieldsEnum fromField = relation.getFromField();
            SqlTable fromTable = relation.getFromTable().getTable();
            AqlTableFieldsEnum toFiled = relation.getToFiled();
            SqlTable toTable = relation.getToTable().getTable();
            toTable = toTable.getTable() == sqlTable.getTable() ? sqlTable : toTable;
            joinTable(toTable, toFiled, fromTable, fromField, usedTables, join, joinTypeEnum, overrideJoins);
        }
    }

    protected void joinTable(SqlTable table, AqlTableFieldsEnum tableJoinField, SqlTable onTable,
            AqlTableFieldsEnum onJoinField,
            Set<SqlTable> declaredTables, StringBuilder join, AqlJoinTypeEnum joinTypeEnum,
            Map<Pair<SqlTable, AqlTableFieldsEnum>, Pair<String, String>> overrideJoins) {
        if (!declaredTables.contains(table)) {
            Pair<String, String> overrideJoin = overrideJoins.get(new Pair<>(onTable, onJoinField));
            String onTablePrefix = overrideJoin == null ? onTable.getAlias() : overrideJoin.getFirst();
            String onField = overrideJoin == null ? onJoinField.name() : overrideJoin.getSecond();
            appendTableJoin(table, tableJoinField, onTablePrefix, onField, join, joinTypeEnum);
            declaredTables.add(table);
        }
    }

    private void appendFirstTable(SqlTable table, StringBuilder joinBuilder, Set<SqlTable> declaredTables) {
        joinBuilder.append(table.getTableName()).append(" ").append(table.getAliasDeclaration());
        declaredTables.add(table);
    }

    protected void appendTableJoin(SqlTable table, AqlTableFieldsEnum tableJoinField, String onTablePrefix,
            String onJoinField,
            StringBuilder joinBuilder, AqlJoinTypeEnum joinTypeEnum) {
        joinBuilder.append(" ").append(joinTypeEnum.signature).append(" ")
                .append(table.getTableName()).append(" ").append(table.getAliasDeclaration())
                .append(" on ").append(table.getAlias()).append(tableJoinField)
                .append(" = ").append(onTablePrefix).append(onJoinField);
    }

    public String sort(AqlQuery aqlQuery) {
        SortDetails sortDetails = aqlQuery.getSort();
        if (sortDetails == null || sortDetails.getFields().isEmpty()) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        List<AqlPhysicalFieldEnum> fields = sortDetails.getFields();
        Iterator<AqlPhysicalFieldEnum> iterator = fields.iterator();
        while (iterator.hasNext()) {
            AqlPhysicalFieldEnum sortField = iterator.next();
            stringBuilder.append(sortField.name());
            stringBuilder.append(" ").append(sortDetails.getSortType().getSqlName());
            if (iterator.hasNext()) {
                stringBuilder.append(",");
            } else {
                stringBuilder.append(" ");
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Query performance optimisation:
     * In case of single table join such as multiple properties table join
     * without the usage of any other table we can use inner join for better performance.
     */
    private AqlJoinTypeEnum resolveJoinType(Iterable<DomainSensitiveTable> allTables) {
        Iterable<SqlTableEnum> tables = Iterables.transform(allTables, toTableEnum);
        HashSet<SqlTableEnum> tableEnums = new HashSet<>();
        for (SqlTableEnum table : tables) {
            if (table != null) {
                tableEnums.add(table);
            }
        }
        boolean useInnerJoin = USE_INNER_JOIN.get() != null && USE_INNER_JOIN.get();
        if (tableEnums.size() == 1 || useInnerJoin) {
            return AqlJoinTypeEnum.innerJoin;
        } else {
            return AqlJoinTypeEnum.leftOuterJoin;
        }
    }

    /**
     * Use with caution and test carefully. This is for rare cases when you would like to control the join type.
     */
    @Deprecated
    public static void setUseInnerJoin(boolean useInnerJoin) {
        if (useInnerJoin) {
            USE_INNER_JOIN.set(Boolean.TRUE);
        } else {
            USE_INNER_JOIN.remove();
        }
    }

    protected abstract SqlTableEnum[] getMainTables();

}
