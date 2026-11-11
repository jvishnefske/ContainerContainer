package org.artifactory.storage.db.monitor.itest;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.monitor.SystemStateRecord;
import org.artifactory.storage.db.monitor.SystemStatesDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for {@link SystemStatesDao}.
 * Covers upsert semantics, time-filtered reads, and both deletion variants.
 *
 * @author Eli Fishman
 */
@Test
public class SystemStatesDaoTest extends DbBaseTest {

    private static final String USE_CASE = "exceeding.rows.violation";
    private static final String OTHER_USE_CASE = "other.use.case";

    @Autowired
    private SystemStatesDao dao;

    @AfterMethod
    public void cleanup() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM system_states");
    }

    @Test
    public void upsert_insertsNewRow_andIsReturnedByFind() throws SQLException {
        long ts = 1_000_000L;
        dao.upsert(USE_CASE, "searchAql", ts, "node-1", "admin",
                "{\"rowsCount\":100}");

        List<SystemStateRecord> rows = dao.findByUseCaseSince(USE_CASE, 0L);
        assertEquals(rows.size(), 1);
        SystemStateRecord r = rows.get(0);
        assertEquals(r.useCaseName(), USE_CASE);
        assertEquals(r.id(), "searchAql");
        assertEquals(r.timestamp(), ts);
        assertEquals(r.nodeId(), "node-1");
        assertEquals(r.actor(), "admin");
        assertEquals(r.data(), "{\"rowsCount\":100}");
    }

    @Test
    public void upsert_overwritesExistingRow_whenSameKey() throws SQLException {
        dao.upsert(USE_CASE, "searchAql", 1_000_000L, "node-1", "admin", "{\"rowsCount\":100}");
        dao.upsert(USE_CASE, "searchAql", 2_000_000L, "node-2", "bot",   "{\"rowsCount\":999}");

        List<SystemStateRecord> rows = dao.findByUseCaseSince(USE_CASE, 0L);
        assertEquals(rows.size(), 1, "upsert must keep exactly one row per (useCaseName, id)");
        SystemStateRecord r = rows.get(0);
        assertEquals(r.timestamp(), 2_000_000L, "timestamp must reflect the latest upsert");
        assertEquals(r.nodeId(), "node-2");
        assertEquals(r.actor(), "bot");
        assertEquals(r.data(), "{\"rowsCount\":999}");
    }

    @Test
    public void upsert_keepsDistinctRows_forDifferentIds() throws SQLException {
        dao.upsert(USE_CASE, "searchAql",   1_000_000L, "node-1", "u1", "{}");
        dao.upsert(USE_CASE, "download npm", 2_000_000L, "node-1", "u2", "{}");

        List<SystemStateRecord> rows = dao.findByUseCaseSince(USE_CASE, 0L);
        assertEquals(rows.size(), 2);
    }

    @Test
    public void findByUseCaseSince_excludesRowsAtOrBeforeCutoff() throws SQLException {
        dao.upsert(USE_CASE, "old-route", 1_000_000L, "node-1", null, "{}");
        dao.upsert(USE_CASE, "new-route", 3_000_000L, "node-1", null, "{}");

        // strictly greater than cutoff: row at 1_000_000 is excluded, row at 3_000_000 included
        List<SystemStateRecord> rows = dao.findByUseCaseSince(USE_CASE, 1_000_000L);
        assertEquals(rows.size(), 1);
        assertEquals(rows.get(0).id(), "new-route");
    }

    @Test
    public void findByUseCaseSince_returnsOnlyRowsForRequestedUseCase() throws SQLException {
        dao.upsert(USE_CASE,       "route-a", 1_000_000L, "node-1", null, "{}");
        dao.upsert(OTHER_USE_CASE, "route-b", 2_000_000L, "node-1", null, "{}");

        List<SystemStateRecord> rows = dao.findByUseCaseSince(USE_CASE, 0L);
        assertEquals(rows.size(), 1);
        assertEquals(rows.get(0).id(), "route-a");
    }

    @Test
    public void findByUseCaseSince_returnsEmptyList_whenNoRowsMatch() throws SQLException {
        List<SystemStateRecord> rows = dao.findByUseCaseSince(USE_CASE, 0L);
        assertTrue(rows.isEmpty());
    }

    @Test
    public void deleteByUseCaseAndId_removesTargetRow_leavesOthers() throws SQLException {
        dao.upsert(USE_CASE, "route-a", 1_000_000L, "node-1", null, "{}");
        dao.upsert(USE_CASE, "route-b", 2_000_000L, "node-1", null, "{}");

        int deleted = dao.deleteByUseCaseAndId(USE_CASE, "route-a");
        assertEquals(deleted, 1);

        List<SystemStateRecord> rows = dao.findByUseCaseSince(USE_CASE, 0L);
        assertEquals(rows.size(), 1);
        assertEquals(rows.get(0).id(), "route-b");
    }

    @Test
    public void deleteByUseCaseAndId_returnsZero_whenRowDoesNotExist() throws SQLException {
        int deleted = dao.deleteByUseCaseAndId(USE_CASE, "nonexistent");
        assertEquals(deleted, 0);
    }

    @Test
    public void deleteByUseCaseOlderThan_removesExpiredRows_keepsRecentOnes() throws SQLException {
        dao.upsert(USE_CASE, "route-old",    1_000_000L, "node-1", null, "{}");
        dao.upsert(USE_CASE, "route-cutoff", 5_000_000L, "node-1", null, "{}");
        dao.upsert(USE_CASE, "route-new",   10_000_000L, "node-1", null, "{}");

        // cutoff at 5_000_000 — strictly less than: deletes route-old only
        int deleted = dao.deleteByUseCaseOlderThan(USE_CASE, 5_000_000L);
        assertEquals(deleted, 1);

        List<SystemStateRecord> remaining = dao.findByUseCaseSince(USE_CASE, 0L);
        assertEquals(remaining.size(), 2);
        assertTrue(remaining.stream().anyMatch(r -> r.id().equals("route-cutoff")));
        assertTrue(remaining.stream().anyMatch(r -> r.id().equals("route-new")));
    }

    @Test
    public void deleteByUseCaseOlderThan_onlyDeletesFromTargetUseCase() throws SQLException {
        dao.upsert(USE_CASE,       "route-a", 1_000_000L, "node-1", null, "{}");
        dao.upsert(OTHER_USE_CASE, "route-b", 1_000_000L, "node-1", null, "{}");

        int deleted = dao.deleteByUseCaseOlderThan(USE_CASE, Long.MAX_VALUE);
        assertEquals(deleted, 1, "Cleanup must not touch rows belonging to other use cases");

        List<SystemStateRecord> otherRows = dao.findByUseCaseSince(OTHER_USE_CASE, 0L);
        assertEquals(otherRows.size(), 1);
    }
}
