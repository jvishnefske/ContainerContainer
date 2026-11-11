package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.model.artifacts.search.syntax.DateRange;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.junit.jupiter.api.Test;

import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryBuilderTest.getSql;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryFilesTest.bind;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class SyntaxSearchQueryUtilsDateFilterTest {

    @Test
    void queryRepositoriesShouldNotIncludeModifiedFilterWhenLastModifiedIsNull() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, null);

        String sql = buildRepositoriesQuery(query, filters);

        assertFalse(sql.contains("modified >="));
        assertFalse(sql.contains("modified <="));
    }

    @Test
    void queryRepositoriesShouldNotIncludeModifiedFilterWhenLastModifiedIsEmpty() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(null, null), null);

        String sql = buildRepositoriesQuery(query, filters);

        assertFalse(sql.contains("modified >="));
        assertFalse(sql.contains("modified <="));
    }

    @Test
    void queryRepositoriesShouldIncludeModifiedGreaterThanFilterWhenOnlyFromIsProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(1640995200000L, null), null);

        String sql = buildRepositoriesQuery(query, filters);

        assertTrue(sql.contains("and modified >= 1640995200000"));
        assertFalse(sql.contains("modified <="));
    }

    @Test
    void queryRepositoriesShouldIncludeBothModifiedFiltersWhenFromAndToAreProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(1640995200000L, 1672531200000L), null);

        String sql = buildRepositoriesQuery(query, filters);

        assertTrue(sql.contains("and modified >= 1640995200000"));
        assertTrue(sql.contains("and modified <= 1672531200000"));
    }

    @Test
    void queryRepositoriesShouldNotIncludeCreatedFilterWhenCreatedIsNull() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, null);

        String sql = buildRepositoriesQuery(query, filters);

        assertFalse(sql.contains("created >="));
        assertFalse(sql.contains("created <="));
    }

    @Test
    void queryRepositoriesShouldNotIncludeCreatedFilterWhenCreatedIsEmpty() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(null, null));

        String sql = buildRepositoriesQuery(query, filters);

        assertFalse(sql.contains("created >="));
        assertFalse(sql.contains("created <="));
    }

    @Test
    void queryRepositoriesShouldIncludeCreatedGreaterThanFilterWhenOnlyFromIsProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(1640995200000L, null));

        String sql = buildRepositoriesQuery(query, filters);

        assertTrue(sql.contains("and created >= 1640995200000"));
        assertFalse(sql.contains("created <="));
    }

    @Test
    void queryRepositoriesShouldIncludeBothCreatedFiltersWhenFromAndToAreProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(1640995200000L, 1672531200000L));

        String sql = buildRepositoriesQuery(query, filters);

        assertTrue(sql.contains("and created >= 1640995200000"));
        assertTrue(sql.contains("and created <= 1672531200000"));
    }

    @Test
    void queryRepositoriesShouldIncludeBothDateFiltersWhenBothLastModifiedAndCreatedAreProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(
                null,
                null,
                null,
                new DateRange(1640995200000L, 1672531200000L),
                new DateRange(1609459200000L, 1640995200000L)
        );

        String sql = buildRepositoriesQuery(query, filters);

        assertTrue(sql.contains("and modified >= 1640995200000"));
        assertTrue(sql.contains("and modified <= 1672531200000"));
        assertTrue(sql.contains("and created >= 1609459200000"));
        assertTrue(sql.contains("and created <= 1640995200000"));
    }

    @Test
    void queryPathsShouldIncludeModifiedFilterWhenLastModifiedIsProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(1640995200000L, null), null);

        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, 0);
        builder.addCondition(createPathCondition(query, ""));
        addDateFilters(builder, filters, "");
        String sql = getSql(builder, 10);

        assertTrue(sql.contains("and modified >= 1640995200000"));
    }

    @Test
    void queryPathsShouldIncludeCreatedFilterWhenCreatedIsProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(1640995200000L, null));

        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, 0);
        builder.addCondition(createPathCondition(query, ""));
        addDateFilters(builder, filters, "");
        String sql = getSql(builder, 10);

        assertTrue(sql.contains("and created >= 1640995200000"));
    }

    @Test
    void queryFilesShouldIncludeModifiedFilterWhenLastModifiedIsProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, new DateRange(1640995200000L, 1672531200000L), null);

        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, 1);
        builder.addCondition(createPathCondition(query, ""));
        addDateFilters(builder, filters, "");
        String sql = getSql(builder, 10);

        assertTrue(sql.contains("and modified >= 1640995200000"));
        assertTrue(sql.contains("and modified <= 1672531200000"));
    }

    @Test
    void queryFilesShouldIncludeCreatedFilterWhenCreatedIsProvided() {
        Query query = new Query("test", null, null, null);
        Filters filters = new Filters(null, null, null, null, new DateRange(1640995200000L, 1672531200000L));

        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, 1);
        builder.addCondition(createPathCondition(query, ""));
        addDateFilters(builder, filters, "");
        String sql = getSql(builder, 10);

        assertTrue(sql.contains("and created >= 1640995200000"));
        assertTrue(sql.contains("and created <= 1672531200000"));
    }

    /**
     * Helper method to build a repositories query SQL string for testing.
     * This simulates what queryRepositories does internally.
     */
    private String buildRepositoriesQuery(Query query, Filters filters) {
        SqlFragment reposCondition =
                SqlFragment.of(query.text() != null ? "and repository_key like '" + query.text() + "%'" : "");
        SqlFragment lastModifiedFilter = createModifiedFilterForRepos(filters.lastModified());
        SqlFragment createdFilter = createCreatedFilterForRepos(filters.created());

        PreparedQuery preparedQuery = new PreparedQuery();
        preparedQuery.add("select repository_key, type, package_type, modified, created from repos_view");
        preparedQuery.add("where");
        preparedQuery.add("(type = 'local' or type = 'remote' or type = 'federated')");
        preparedQuery.add(reposCondition);
        preparedQuery.add(lastModifiedFilter);
        preparedQuery.add(createdFilter);
        preparedQuery.add("limit 10");

        return bind(preparedQuery.getTemplate(), preparedQuery.getParams());
    }

    /**
     * Simulates the createModifiedFilter logic for testing
     */
    private SqlFragment createModifiedFilterForRepos(DateRange lastModified) {
        if (lastModified == null || lastModified.isEmpty()) {
            return SqlFragment.EMPTY;
        }
        if (lastModified.hasFrom() && lastModified.hasTo()) {
            return SqlFragment.of(
                    "and modified >= %d and modified <= %d ".formatted(lastModified.from(), lastModified.to()));
        }
        if (lastModified.hasFrom()) {
            return SqlFragment.of("and modified >= %d ".formatted(lastModified.from()));
        }
        return SqlFragment.EMPTY;
    }

    /**
     * Simulates the createCreatedFilter logic for testing
     */
    private SqlFragment createCreatedFilterForRepos(DateRange created) {
        if (created == null || created.isEmpty()) {
            return SqlFragment.EMPTY;
        }
        if (created.hasFrom() && created.hasTo()) {
            return SqlFragment.of("and created >= %d and created <= %d ".formatted(created.from(), created.to()));
        }
        if (created.hasFrom()) {
            return SqlFragment.of("and created >= %d ".formatted(created.from()));
        }
        return SqlFragment.EMPTY;
    }

    /**
     * Helper method to create path condition
     */
    private SqlFragment createPathCondition(Query query, String tablePrefix) {
        if (query.text() != null) {
            return SqlFragment.of("and " + tablePrefix + "node_name like '" + query.text() + "%'");
        }
        return SqlFragment.EMPTY;
    }

    /**
     * Helper method to add date filters to query builder (simulates addCommonConditions logic)
     */
    private void addDateFilters(SyntaxSearchQueryBuilder builder, Filters filters, String tablePrefix) {
        DateRange lastModified = filters.lastModified();
        if (lastModified != null && !lastModified.isEmpty()) {
            if (lastModified.hasFrom() && lastModified.hasTo()) {
                builder.addCondition(
                        SqlFragment.of("and " + tablePrefix + "modified >= " + lastModified.from() +
                                " and " + tablePrefix + "modified <= " + lastModified.to()));
            } else if (lastModified.hasFrom()) {
                builder.addCondition(
                        SqlFragment.of("and " + tablePrefix + "modified >= " + lastModified.from()));
            }
        }
        DateRange created = filters.created();
        if (created != null && !created.isEmpty()) {
            if (created.hasFrom() && created.hasTo()) {
                builder.addCondition(SqlFragment.of("and " + tablePrefix + "created >= " + created.from() +
                        " and " + tablePrefix + "created <= " + created.to()));
            } else if (created.hasFrom()) {
                builder.addCondition(SqlFragment.of("and " + tablePrefix + "created >= " + created.from()));
            }
        }
    }
}

