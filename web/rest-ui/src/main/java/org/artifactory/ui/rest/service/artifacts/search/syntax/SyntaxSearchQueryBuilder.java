package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Amir Avidan
 */
class SyntaxSearchQueryBuilder {

    private final Filters filters;
    private final int nodeType;
    private final boolean needsJoin;
    private final boolean autoComplete;
    private final List<SqlFragment> conditions = new ArrayList<>();
    private boolean noNeedToQuery = false;

    SyntaxSearchQueryBuilder(Filters filters, int nodeType) {
        this(filters, nodeType, false);
    }

    SyntaxSearchQueryBuilder(Filters filters, int nodeType, boolean autoComplete) {
        this.filters = filters;
        this.nodeType = nodeType;
        this.needsJoin = SyntaxSearchQueryUtils.hasRepositoryFilters(filters);
        this.autoComplete = autoComplete;
    }

    private static final String SIMPLE_TABLE = "nodes_view";
    private static final String ALIASED_TABLE = "nodes_view n";
    private static final String JOIN_CLAUSE = """
             JOIN repository_config rc ON\s
                 n.repo = CASE\s
                     WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                     ELSE rc.repository_key
                 END
            \s""";


    /**
     * Adds a condition to the WHERE clause.
     * Conditions are expected to already have the appropriate table prefix applied.
     */
    public SyntaxSearchQueryBuilder addCondition(SqlFragment condition) {
        if (condition.isEmpty()) {
            return this;
        }
        conditions.add(condition);
        return this;
    }

    public boolean noConditions() {
        return noNeedToQuery || conditions.isEmpty();
    }

    public void addRepositoryFilters() {
        if (filters != null) {
            SqlFragment repoTypeFilter =
                    SyntaxSearchQueryUtils.createRepositoryTypeFilter(filters.repositoryTypes());
            SqlFragment pkgTypeFilter = SyntaxSearchQueryUtils.createPackageTypeFilter(filters.packageTypes());

            if (repoTypeFilter.isNotEmpty()) {
                conditions.add(SqlFragment.formatted("and %s", List.of(repoTypeFilter)));
            }
            if (pkgTypeFilter.isNotEmpty()) {
                conditions.add(pkgTypeFilter);
            }
        }
    }

    public PreparedQuery build(int limit) {
        SqlFragment selectColumns = SqlFragment.formatted("select %s", List.of(buildSelectColumns()));
        SqlFragment fromClause = SqlFragment.formatted("from %s", List.of(buildFromClause()));
        SqlFragment baseCondition = getBaseCondition();
        SqlFragment orderByClause = buildOrderByClause();
        SqlFragment limitClause = SqlFragment.of("limit ?", List.of(limit));

        PreparedQuery preparedQuery = new PreparedQuery();

        preparedQuery.add(selectColumns);
        preparedQuery.add(fromClause);
        preparedQuery.add("where");
        preparedQuery.add(baseCondition);
        preparedQuery.addAll(conditions);
        preparedQuery.add(orderByClause);
        preparedQuery.add(limitClause);

        return preparedQuery;
    }

    public void setNoNeedToQuery() {
        this.noNeedToQuery = true;
    }

    private SqlFragment buildFromClause() {
        if (needsJoin) {
            return SqlFragment.of(ALIASED_TABLE + "\n" + JOIN_CLAUSE);
        }
        return SqlFragment.of(SIMPLE_TABLE);
    }

    private SqlFragment buildSelectColumns() {
        if (needsJoin) {
            return SqlFragment.of("n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created");
        }
        return SqlFragment.of("node_id, repo, node_path, node_name, bin_length, modified, created");
    }

    private SqlFragment buildOrderByClause() {
        if (autoComplete) {
            return SqlFragment.EMPTY;
        }
        if (needsJoin) {
            return SqlFragment.of("order by n.modified desc, n.node_id desc");
        }
        return SqlFragment.of("order by modified desc, node_id desc");
    }

    private SqlFragment getBaseCondition() {
        String prefix = needsJoin ? "n." : "";
        String depthCondition = nodeType == 0 ? "\n    and " + prefix + "depth > 0" : "";
        return SqlFragment.of(
                prefix + "repo <> 'auto-trashcan'\n    and " + prefix + "node_type = " + nodeType + depthCondition);
    }
}

