package org.artifactory.storage.db.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlParseHelperTest {
    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldKeepEmptyQuery() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("");
        assertEquals("", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldReplaceNullQueryWithEmptyString() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces(null);
        assertEquals("", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldLeaveSimpleSqlAsIs() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("SELECT * from nodes");
        assertEquals("SELECT * from nodes", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldCompactWhitespaces() {
        // 1. Replace tabs and new lines with spaces
        // 2. Collapse sequences of spaces to a single space
        // 3. Remove leading and trailing spaces
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("  SELECT\t *\tfrom\n  nodes  \n");
        assertEquals("SELECT * from nodes", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldRemoveSingleLineComments() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("""
                SELECT * from nodes -- a comment to remove
                  WHERE node_id=?
                ; -- another comment
                """);
        assertEquals("SELECT * from nodes WHERE node_id=? ;", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldRemoveSingleLineCommentWithoutTrailingEOL() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("SELECT * from nodes -- comment");
        assertEquals("SELECT * from nodes", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldRemoveMultiLineComments() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("""
                /*
                A multi-line comment
                before the query
                */ SELECT * /* comment inside the query */ from nodes
                """);
        assertEquals("SELECT * from nodes", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldRemoveUnclosedMultiLineComments() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("""
                SELECT * from nodes /* unclosed
                multiline
                comment
                """);
        assertEquals("SELECT * from nodes", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldIgnoreCommentWithinLiteral() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("SELECT '--test', id from nodes");
        assertEquals("SELECT '--test', id from nodes", actual);
    }

    @Test
    void removeSqlCommentsAndNormalizeSpaces_shouldIgnoreApostropheWithinQuotes() {
        String actual = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("SELECT id from nodes WHERE id=\"doesn't\"");
        assertEquals("SELECT id from nodes WHERE id=\"doesn't\"", actual);
    }

    @Test
    void getSqlStatementType_shouldUppercaseTheStatementType() {
        String actual = SqlParseHelper.getSqlStatementType("select * from nodes");
        assertEquals("SELECT", actual);
    }

    @Test
    void extractTableNames_getsTableNameFromSimpleSelectStatement() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "SELECT * from nodes", "SELECT");
        assertEquals(Set.of("nodes"), actual);
    }

    @Test
    void extractTableNames_getsTableNameFromSelectWithWereClause() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "SELECT * from nodes WHERE node_id=?", "SELECT");
        assertEquals(Set.of("nodes"), actual);
    }

    @Test
    void extractTableNames_getsTableNameFromNestedSelectWithWereClause() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "SELECT node_id from (SELECT * FROM nodes WHERE node_id=?)", "SELECT");
        assertEquals(Set.of("nodes"), actual);
    }

    @Test
    void extractTableNames_getsMultipleTablesNamesFromSelectWithJoin() {
        String normalizedQuery = SqlParseHelper.removeSqlCommentsAndNormalizeSpaces("""
                SELECT
                    rb.*,
                    rbp.*
                FROM
                    release_bundle rb
                    LEFT JOIN (
                        SELECT
                            MAX(promotion_created) AS max_created, promotion_bundle_id
                        FROM
                            release_bundle_promotion
                        WHERE
                            promotion_status = 3
                        GROUP BY promotion_bundle_id
                    ) rbp_latest_version
                        ON rb.bundle_id = rbp_latest_version.promotion_bundle_id
                    LEFT JOIN release_bundle_promotion rbp
                        ON rb.bundle_id = rbp.promotion_bundle_id AND rbp.promotion_created = max_created
                ORDER BY bundle_created DESC
                """
        );
        Set<String> actual = SqlParseHelper.extractTableNames(normalizedQuery, "SELECT");
        assertEquals(Set.of("release_bundle", "release_bundle_promotion"), actual);
    }

    @Test
    void extractTableNames_returnsEmptySetForSelectWithoutTable() {
        Set<String> actual = SqlParseHelper.extractTableNames("SELECT 1;", "SELECT");
        assertTrue(actual.isEmpty());
    }

    @Test
    void extractTableNames_getsTableNameForUpdateStatement() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "UPDATE nodes SET node_name=?", "UPDATE");
        assertEquals(Set.of("nodes"), actual);
    }

    @Test
    void extractTableNames_getsTableNameForDeleteStatement() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "DELETE FROM nodes WHERE node_id = ?", "DELETE");
        assertEquals(Set.of("nodes"), actual);
    }

    @Test
    void extractTableNames_getsTableNameForTruncateTableStatement() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "TRUNCATE table node_events_errors;", "TRUNCATE");
        assertEquals(Set.of("node_events_errors"), actual);
    }
    //
    @Test
    void extractTableNames_getsTableNameForInsertStatement() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "INSERT INTO tasks (task_type,task_context,created,task_id) VALUES (?,?,?,?)",
                "INSERT");
        assertEquals(Set.of("tasks"), actual);
    }

    @Test
    void extractTableNames_getsEmptySetForUnknownStatement() {
        Set<String> actual = SqlParseHelper.extractTableNames(
                "SET configuration_parameter TO DEFAULT;",
                "SET");
        assertTrue(actual.isEmpty());
    }

    @Test
    void extractTableNameAfter_shouldReturnDefaultValueWhenAfterTextNotFound() {
        String actual = SqlParseHelper.extractTableNameAfter("SELECT 1", " FROM ", "(unknown)");
        assertEquals("(unknown)", actual);
    }
}