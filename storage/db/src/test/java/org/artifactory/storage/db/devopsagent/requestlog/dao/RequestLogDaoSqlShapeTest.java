package org.artifactory.storage.db.devopsagent.requestlog.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.devopsagent.requestlog.model.Cursor;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;


/**
 * Per-engine SQL-shape unit test.
 * Constructs the DAO with a mocked {@link ArtifactoryJdbcHelper}, calls
 * {@code buildSearchQuery(...)}, and asserts the emitted SQL contains the documented
 * PostgreSQL-dialect tokens in the correct order.
 */
public class RequestLogDaoSqlShapeTest {

    private RequestLogDao dao;

    @BeforeMethod
    public void setUp() {
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        dao = new RequestLogDao(mock(ArtifactoryJdbcHelper.class), dbProperties);
    }

    @Test
    public void searchSqlContainsTsquerySimpleParameterised() {
        SearchCriteria c = SearchCriteria.builder()
                .limit(10).offset(0)
                .repoNames(Collections.emptyList())
                .searchQuery("foo|bar")
                .minDurationMs(0L)
                .build();
        RequestLogDaoSupport.QueryParts parts = dao.buildSearchQuery(c);
        assertTrue(parts.sql().contains("search_vector @@ to_tsquery('simple', ?)"),
                "Search SQL must use parameterised to_tsquery('simple', ?): " + parts.sql());
        assertFalse(parts.sql().contains("foo|bar"),
                "User input must never be interpolated into the SQL string");
    }

    @Test
    public void searchSqlIncludesLimitOffsetWhenOffsetPositive() {
        SearchCriteria c = SearchCriteria.builder()
                .limit(50).offset(100)
                .repoNames(Collections.emptyList())
                .searchQuery("")
                .build();
        RequestLogDaoSupport.QueryParts parts = dao.buildSearchQuery(c);
        assertTrue(parts.sql().contains("LIMIT 51"), parts.sql());
        assertTrue(parts.sql().contains("OFFSET 100"), parts.sql());
        int limitIdx = parts.sql().indexOf("LIMIT 51");
        int offsetIdx = parts.sql().indexOf("OFFSET 100");
        assertTrue(limitIdx > 0 && offsetIdx > limitIdx, "LIMIT must appear before OFFSET");
    }

    @Test
    public void searchSqlOmitsOffsetWhenZero() {
        SearchCriteria c = SearchCriteria.builder()
                .limit(50).offset(0)
                .repoNames(Collections.emptyList())
                .searchQuery("")
                .build();
        RequestLogDaoSupport.QueryParts parts = dao.buildSearchQuery(c);
        assertFalse(parts.sql().contains("OFFSET "), "OFFSET must be omitted when offset == 0");
        assertTrue(parts.sql().contains("LIMIT 51"), parts.sql());
    }

    @Test
    public void searchSqlEmitsKeysetPredicateWhenCursorPresent() {
        SearchCriteria c = SearchCriteria.builder()
                .limit(10).offset(0)
                .cursor(new Cursor(1_715_630_000_000L, 42L))
                .repoNames(Collections.emptyList())
                .searchQuery("")
                .build();
        RequestLogDaoSupport.QueryParts parts = dao.buildSearchQuery(c);
        assertTrue(parts.sql().contains("(request_time < ? OR (request_time = ? AND id < ?))"),
                parts.sql());
        // Offset is ignored when cursor is supplied
        assertFalse(parts.sql().contains("OFFSET ?"));
    }

    @Test
    public void searchSqlSortAndTiebreakerArePresent() {
        SearchCriteria c = SearchCriteria.builder()
                .limit(10).offset(0)
                .repoNames(Collections.emptyList())
                .searchQuery("")
                .build();
        RequestLogDaoSupport.QueryParts parts = dao.buildSearchQuery(c);
        assertTrue(parts.sql().contains("ORDER BY request_time DESC, id DESC"),
                "Primary sort key + tiebreaker must be present and stable: " + parts.sql());
    }
}
