package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.common.repo.PackageType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Type;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.regex.Pattern;

import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchQueryFilesTest.bind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class SyntaxSearchQueryBuilderTest {

    private static final int FOLDER_NODE_TYPE = 0;
    private static final int FILE_NODE_TYPE = 1;

    @Test
    void constructor_WithNullFilters_ShouldNotNeedJoin() {
        // Act
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);
        String sql = getSql(builder, 10);

        // Assert - should use simple table without alias
        assertTrue(sql.contains("from nodes_view"));
        assertFalse(sql.contains("nodes_view n"));
        assertFalse(sql.contains("JOIN"));
    }

    @Test
    void constructor_WithEmptyFilters_ShouldNotNeedJoin() {
        // Arrange
        Filters filters = new Filters(null, null, null, null, null);

        // Act
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("from nodes_view"));
        assertFalse(sql.contains("JOIN"));
    }

    @Test
    void constructor_WithTypesFilterOnly_ShouldNotNeedJoin() {
        // Arrange - types filter doesn't affect JOIN, only repositoryTypes/packageTypes do
        Filters filters = new Filters(Set.of(Type.FILE), null, null, null, null);

        // Act
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);
        String sql = getSql(builder, 10);

        // Assert - no JOIN because repositoryTypes and packageTypes are null
        assertTrue(sql.contains("from nodes_view"));
        assertFalse(sql.contains("nodes_view n"));
        assertFalse(sql.contains("JOIN"));
    }

    @Test
    void constructor_WithRepositoryTypeFilter_ShouldNeedJoin() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);

        // Act
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);
        String sql = getSql(builder, 10);

        // Assert - should use aliased table with JOIN
        assertTrue(sql.contains("nodes_view n"));
        assertTrue(sql.contains("JOIN repository_config rc"));
    }

    @Test
    void constructor_WithPackageTypeFilter_ShouldNeedJoin() {
        // Arrange
        Filters filters = new Filters(null, null, Set.of(PackageType.MAVEN), null, null);

        // Act
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("nodes_view n"));
        assertTrue(sql.contains("JOIN repository_config rc"));
    }

    @Test
    void addCondition_WithNullCondition_ShouldNotAddCondition() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        builder.addCondition(SqlFragment.EMPTY);
        String sql = getSql(builder, 10);

        // Assert - only base conditions present
        assertTrue(sql.contains("repo <> 'auto-trashcan'"));
        assertTrue(sql.contains("node_type = 1"));
    }

    @Test
    void addCondition_WithEmptyCondition_ShouldNotAddCondition() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        builder.addCondition(SqlFragment.EMPTY);
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("repo <> 'auto-trashcan'"));
        assertTrue(sql.contains("node_type = 1"));
    }

    @Test
    void addCondition_WithValidCondition_ShouldAddCondition() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        builder.addCondition(SqlFragment.of("and repo='maven'"));
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("and repo='maven'"));
    }

    @Test
    void addCondition_ShouldSupportMethodChaining() {
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        builder.addCondition(SqlFragment.of("and repo='maven'"));
        builder.addCondition(SqlFragment.of("and node_path='com/example'"));
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("and repo='maven'"));
        assertTrue(sql.contains("and node_path='com/example'"));
    }

    @Test
    void addRepositoryFilters_WithNullFilters_ShouldNotAddConditions() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        builder.addRepositoryFilters();
        String sql = getSql(builder, 10);

        // Assert - no repository type or package type conditions
        assertFalse(sql.contains("rc.type"));
        assertFalse(sql.contains("rc.package_type"));
    }

    @Test
    void addRepositoryFilters_WithRepositoryTypeFilter_ShouldAddTypeCondition() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);

        // Act
        builder.addRepositoryFilters();
        String sql = getSql(builder, 10);

        // Assert
        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON
                     n.repo = CASE
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 1
                    and type IN ('local')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(sql));
    }

    @Test
    void addRepositoryFilters_WithMultipleRepositoryTypes_ShouldAddInCondition() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL, RepositoryType.REMOTE), null, null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);

        // Act
        builder.addRepositoryFilters();
        String sql = getSql(builder, 10);

        assertTrue(sql.contains("type IN"));
        assertTrue(sql.contains("'local'"));
        assertTrue(sql.contains("'remote'"));
        assertTrue(sql.contains("JOIN repository_config rc"));
    }

    @Test
    void addRepositoryFilters_WithPackageTypeFilter_ShouldAddPackageTypeCondition() {
        // Arrange
        Filters filters = new Filters(null, null, Set.of(PackageType.MAVEN), null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);

        // Act
        builder.addRepositoryFilters();
        String sql = getSql(builder, 10);

        // Assert
        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON
                     n.repo = CASE
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 1
                    and package_type IN ('maven')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(sql));
    }

    @Test
    void addRepositoryFilters_WithBothFilters_ShouldAddBothConditions() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), Set.of(PackageType.MAVEN), null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);

        // Act
        builder.addRepositoryFilters();
        String sql = getSql(builder, 10);
        // Assert
        String expectedSql = """
                select n.node_id, n.repo, n.node_path, n.node_name, n.bin_length, n.modified, n.created
                from nodes_view n
                 JOIN repository_config rc ON
                     n.repo = CASE
                         WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'
                         ELSE rc.repository_key
                     END
                where
                    n.repo <> 'auto-trashcan'
                    and n.node_type = 1
                    and type IN ('local')
                    and package_type IN ('maven')
                order by n.modified desc, n.node_id desc
                limit 10;
                """;
        assertEquals(normalizeSql(expectedSql), normalizeSql(sql));
    }

    @Test
    void build_WithFolderNodeType_ShouldIncludeDepthCondition() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FOLDER_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("node_type = 0"));
        assertTrue(sql.contains("depth > 0"));
    }

    @Test
    void build_WithFileNodeType_ShouldNotIncludeDepthCondition() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("node_type = 1"));
        assertFalse(sql.contains("depth > 0"));
    }

    @Test
    void build_ShouldIncludeSelectColumns() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("node_id"));
        assertTrue(sql.contains("repo"));
        assertTrue(sql.contains("node_path"));
        assertTrue(sql.contains("node_name"));
        assertTrue(sql.contains("bin_length"));
        assertTrue(sql.contains("modified"));
    }

    @Test
    void build_WithJoin_ShouldUsePrefixedColumns() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert - columns should be prefixed with n.
        assertTrue(sql.contains("n.node_id"));
        assertTrue(sql.contains("n.repo"));
        assertTrue(sql.contains("n.node_path"));
    }

    @Test
    void build_ShouldIncludeOrderByClause() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("order by modified desc, node_id desc"));
    }

    @Test
    void build_WithJoin_ShouldUsePrefixedOrderBy() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("order by n.modified desc, n.node_id desc"));
    }

    @Test
    void build_ShouldIncludeLimitClause() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 25);

        // Assert
        assertTrue(sql.contains("limit 25"));
    }

    @Test
    void build_ShouldExcludeAutoTrashcan() {
        // Arrange
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(Filters.orEmpty(null), FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("repo <> 'auto-trashcan'"));
    }

    @Test
    void build_WithJoinAndFolderType_ShouldUsePrefixedDepthCondition() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.LOCAL), null, null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FOLDER_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert
        assertTrue(sql.contains("n.depth > 0"));
        assertTrue(sql.contains("n.node_type = 0"));
    }

    @Test
    void build_JoinClause_ShouldHandleRemoteCacheRepos() {
        // Arrange
        Filters filters = new Filters(null, Set.of(RepositoryType.REMOTE), null, null, null);
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE);

        // Act
        String sql = getSql(builder, 10);

        // Assert - JOIN should handle remote repos by appending -cache
        assertTrue(sql.contains("WHEN rc.type = 'remote' THEN rc.repository_key || '-cache'"));
    }

    public static String getSql(SyntaxSearchQueryBuilder builder, int limit) {
        PreparedQuery query = builder.build(limit);
        return bind(query.getTemplate(), query.getParams());
    }

    private String normalizeSql(String sql) {
        if (sql == null) {
            return null;
        }
        return Pattern.compile("\\s+").matcher(sql.trim()).replaceAll(" ");
    }
}

