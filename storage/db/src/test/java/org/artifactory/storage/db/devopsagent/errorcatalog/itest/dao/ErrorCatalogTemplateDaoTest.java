package org.artifactory.storage.db.devopsagent.errorcatalog.itest.dao;

import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.TemplateRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.AgenticSearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.KeysetCursor;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.TemplateUpsertRequest;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for {@link ErrorCatalogTemplateDao}. Targets the per-DB UPSERT branches,
 * FTS/LIKE search fallbacks, keyset pagination, CHECK enforcement, and the batch merge path —
 * one test per behaviour worth losing sleep over.
 *
 * <p>Trivial null/empty input guards on the DAO are intentionally not tested here: they are
 * single-line returns that any code review catches and cost more in surefire time than they save.
 */
public class ErrorCatalogTemplateDaoTest extends DbBaseTest {

    /**
     * Production stores SHA-256 hex digests (exactly 64 chars) in a CHAR(64) column.
     * Tests use realistic 64-char fixtures so CHAR padding semantics behave the same way
     * they do at runtime — short tokens would be space-padded by Oracle/Derby/MSSQL.
     */
    private static String hash(String tag) {
        return (tag + "a".repeat(64)).substring(0, 64);
    }

    private static CriteriaBuilder criteria() {
        return new CriteriaBuilder();
    }

    /**
     * Small mutable builder for {@link AgenticSearchCriteria}. Tests stay readable by only setting
     * the fields they care about; everything else stays at the "no constraint" default.
     */
    private static final class CriteriaBuilder {
        private List<Long> ids;
        private List<String> severities;
        private List<String> loggerClasses;
        private List<String> exceptionClasses;
        private Long occurrenceCountFrom;
        private Long occurrenceCountTo;
        private Long firstOccurrenceFrom;
        private Long firstOccurrenceTo;
        private Long lastOccurrenceFrom;
        private Long lastOccurrenceTo;
        private String tsqueryString;

        CriteriaBuilder ids(List<Long> v) {
            this.ids = v;
            return this;
        }

        CriteriaBuilder severities(List<String> v) {
            this.severities = v;
            return this;
        }

        CriteriaBuilder loggerClasses(List<String> v) {
            this.loggerClasses = v;
            return this;
        }

        CriteriaBuilder exceptionClasses(List<String> v) {
            this.exceptionClasses = v;
            return this;
        }

        CriteriaBuilder occurrenceCountFrom(Long v) {
            this.occurrenceCountFrom = v;
            return this;
        }

        CriteriaBuilder occurrenceCountTo(Long v) {
            this.occurrenceCountTo = v;
            return this;
        }

        CriteriaBuilder lastOccurrenceFrom(Long v) {
            this.lastOccurrenceFrom = v;
            return this;
        }

        CriteriaBuilder lastOccurrenceTo(Long v) {
            this.lastOccurrenceTo = v;
            return this;
        }

        CriteriaBuilder tsqueryString(String v) {
            this.tsqueryString = v;
            return this;
        }

        AgenticSearchCriteria build() {
            return new AgenticSearchCriteria(
                    ids, severities, loggerClasses, exceptionClasses,
                    occurrenceCountFrom, occurrenceCountTo,
                    firstOccurrenceFrom, firstOccurrenceTo,
                    lastOccurrenceFrom, lastOccurrenceTo,
                    tsqueryString);
        }
    }

    @Autowired
    private ErrorCatalogTemplateDao dao;

    @AfterMethod
    public void tearDown() {
        dao.deleteAll();
    }

    @Test
    public void testUpsertInsertsNewRow() {
        long now = System.currentTimeMillis();
        String h = hash("new");
        dao.upsertBatch(List.of(new TemplateUpsertRequest(h, "ERROR", "com.example.Foo",
                "java.lang.NullPointerException", "boom <n>", 1L, now, now)));
        Optional<TemplateRow> row = dao.findBySignatureHash(h);
        assertTrue(row.isPresent());
        assertEquals(row.get().occurrenceCount(), 1L);
        assertEquals(row.get().severity(), "ERROR");
        assertEquals(row.get().firstOccurrenceAt(), now);
        assertEquals(row.get().lastOccurrenceAt(), now);
    }

    @Test
    public void testUpsertIncrementsCountAndAdvancesLastOccurrence() {
        long t1 = 1_000_000L;
        long t2 = 2_000_000L;
        String h = hash("merge");
        dao.upsertBatch(List.of(new TemplateUpsertRequest(h, "WARN", "com.example.A", null,
                "msg", 1L, t1, t1)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(h, "WARN", "com.example.A", null,
                "msg", 5L, t2, t2)));

        TemplateRow row = dao.findBySignatureHash(h).orElseThrow();
        assertEquals(row.occurrenceCount(), 6L);
        assertEquals(row.firstOccurrenceAt(), t1, "first_occurrence_at must be preserved");
        assertEquals(row.lastOccurrenceAt(), t2, "last_occurrence_at must advance");
    }

    @Test
    public void testUpsertOlderTimestampDoesNotRegress() {
        long laterTs = 5_000_000L;
        long earlierTs = 1_000_000L;
        String h = hash("noregress");
        dao.upsertBatch(List.of(new TemplateUpsertRequest(h, "ERROR", "com.example.B", null,
                "msg", 1L, laterTs, laterTs)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(h, "ERROR", "com.example.B", null,
                "msg", 1L, earlierTs, earlierTs)));

        TemplateRow row = dao.findBySignatureHash(h).orElseThrow();
        assertEquals(row.lastOccurrenceAt(), laterTs);
        assertEquals(row.occurrenceCount(), 2L);
    }

    @Test
    public void testFindIdsBySignatureHashesBulk() {
        // Covers the IN-list bulk lookup AND, implicitly, that a missing hash is simply absent
        // from the returned map — exercises the "miss" path without a separate negative test.
        long now = System.currentTimeMillis();
        String hA = hash("a");
        String hB = hash("b");
        String hC = hash("c");
        String hMiss = hash("miss");
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hA, "WARN", "c.A", null, "m1", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hB, "WARN", "c.B", null, "m2", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hC, "ERROR", "c.C", null, "m3", 1L, now, now)));

        Map<String, Long> resolved = dao.findIdsBySignatureHashes(List.of(hA, hB, hC, hMiss));
        assertEquals(resolved.size(), 3);
        assertNotNull(resolved.get(hA));
        assertNotNull(resolved.get(hB));
        assertNotNull(resolved.get(hC));
        assertFalse(resolved.containsKey(hMiss));
    }

    @Test
    public void testFilterBySeverityReturnsOnlyErrors() {
        long now = System.currentTimeMillis();
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("w1"), "WARN", "c.A", null, "warn 1", 1L,
                now - 100, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("w2"), "WARN", "c.A", null, "warn 2", 1L,
                now - 50, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("e1"), "ERROR", "c.B", null, "err 1", 1L, now, now)));

        List<TemplateRow> errors = dao.findBySeverity("ERROR", null, 10);
        assertEquals(errors.size(), 1);
        assertEquals(errors.get(0).severity(), "ERROR");
        List<TemplateRow> warns = dao.findBySeverity("WARN", null, 10);
        assertEquals(warns.size(), 2);
    }

    @Test
    public void testFilterByLoggerClass() {
        long now = System.currentTimeMillis();
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("foo"), "ERROR", "com.x.Foo", null, "m", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("bar"), "ERROR", "com.x.Bar", null, "m", 1L, now, now)));

        List<TemplateRow> result = dao.findByLoggerClass("com.x.Foo", null, 10);
        assertEquals(result.size(), 1);
        assertEquals(result.get(0).loggerClass(), "com.x.Foo");
    }

    @Test
    public void testSearchByMessageMatchesAndMisses() {
        // Hardened for MySQL/MariaDB InnoDB FULLTEXT: a 4-row corpus with the searched token in
        // exactly one row sidesteps the 50% MyISAM rule (irrelevant on InnoDB but defensive),
        // and the searched token "repository" (10 chars) is well above innodb_ft_min_token_size
        // and is not in any default stopword list.
        long now = System.currentTimeMillis();
        String hRepo = hash("repo");
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hRepo, "WARN", "c.A", null,
                "repository validation failed", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("conn"), "ERROR", "c.A", null,
                "connection refused by upstream", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("auth"), "ERROR", "c.A", null,
                "authentication denied", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("disk"), "WARN", "c.A", null,
                "disk usage above threshold", 1L, now, now)));

        List<TemplateRow> hits = dao.searchByMessage(
                new SearchCriteria("repository", null, null), null, 10);
        assertEquals(hits.size(), 1, "Expected exactly one row matching 'repository'");
        assertEquals(hits.get(0).signatureHash(), hRepo);

        List<TemplateRow> misses = dao.searchByMessage(
                new SearchCriteria("nonexistenttokenxyz", null, null), null, 10);
        assertTrue(misses.isEmpty(), "Search for a token not present must return no rows");
    }

    @Test
    public void testKeysetPaginationStable() {
        // Insert 5 templates with identical last_occurrence_at to force the id-tiebreaker path.
        long ts = 9_000_000L;
        for (int i = 0; i < 5; i++) {
            dao.upsertBatch(List.of(
                    new TemplateUpsertRequest(hash("kp" + i), "WARN", "c.A", null, "p" + i, 1L, ts, ts)));
        }
        List<TemplateRow> firstPage = dao.findBySeverity("WARN", null, 2);
        assertEquals(firstPage.size(), 2);

        TemplateRow last = firstPage.get(firstPage.size() - 1);
        List<TemplateRow> secondPage = dao.findBySeverity("WARN",
                KeysetCursor.of(last.lastOccurrenceAt(), last.id()), 2);
        assertEquals(secondPage.size(), 2);
        long firstPageMinId = firstPage.stream().mapToLong(TemplateRow::id).min().orElseThrow();
        for (TemplateRow r : secondPage) {
            assertTrue(r.id() < firstPageMinId, "Keyset pagination must not overlap");
        }
    }

    @Test
    public void testDeleteOlderThanCutoff() {
        long now = System.currentTimeMillis();
        long oldTs = now - 8 * 86_400_000L;
        String hOld = hash("old");
        String hNew = hash("new");
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hOld, "WARN", "c.A", null, "old", 1L, oldTs, oldTs)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hNew, "WARN", "c.A", null, "new", 1L, now, now)));

        int deleted = dao.deleteOlderThan(now - 7 * 86_400_000L);
        assertEquals(deleted, 1);
        assertFalse(dao.findBySignatureHash(hOld).isPresent());
        assertTrue(dao.findBySignatureHash(hNew).isPresent());
    }


    @Test
    public void testSearchTemplatesFiltersByIdInList() {
        long now = System.currentTimeMillis();
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("a"), "WARN", "c.A", null, "m1", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("b"), "WARN", "c.A", null, "m2", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("c"), "ERROR", "c.B", null, "m3", 1L, now, now)));
        long idA = dao.findBySignatureHash(hash("a")).orElseThrow().id();
        long idC = dao.findBySignatureHash(hash("c")).orElseThrow().id();

        List<TemplateRow> hits = dao.searchTemplates(criteria()
                .ids(List.of(idA, idC))
                .build(), 100, 0);
        assertEquals(hits.size(), 2);
        assertTrue(hits.stream().anyMatch(r -> r.id() == idA));
        assertTrue(hits.stream().anyMatch(r -> r.id() == idC));
    }

    @Test
    public void testSearchTemplatesFiltersBySeverityInList() {
        long now = System.currentTimeMillis();
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("w"), "WARN", "c.A", null, "m1", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("e"), "ERROR", "c.B", null, "m2", 1L, now, now)));

        List<TemplateRow> bothSev = dao.searchTemplates(criteria()
                .severities(List.of("WARN", "ERROR"))
                .build(), 100, 0);
        assertEquals(bothSev.size(), 2);

        List<TemplateRow> errsOnly = dao.searchTemplates(criteria()
                .severities(List.of("ERROR"))
                .build(), 100, 0);
        assertEquals(errsOnly.size(), 1);
        assertEquals(errsOnly.get(0).severity(), "ERROR");
    }

    @Test
    public void testSearchTemplatesFiltersByLoggerAndExceptionClass() {
        long now = System.currentTimeMillis();
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("la"), "ERROR", "c.Foo",
                "java.lang.NullPointerException", "m1", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("lb"), "ERROR", "c.Bar",
                "java.lang.NullPointerException", "m2", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("lc"), "ERROR", "c.Foo", "java.io.IOException",
                "m3", 1L, now, now)));

        List<TemplateRow> byLogger = dao.searchTemplates(criteria()
                .loggerClasses(List.of("c.Foo"))
                .build(), 100, 0);
        assertEquals(byLogger.size(), 2);
        for (TemplateRow row : byLogger) {
            assertEquals(row.loggerClass(), "c.Foo");
        }

        List<TemplateRow> byException = dao.searchTemplates(criteria()
                .exceptionClasses(List.of("java.lang.NullPointerException"))
                .build(), 100, 0);
        assertEquals(byException.size(), 2);
        for (TemplateRow row : byException) {
            assertEquals(row.exceptionClass(), "java.lang.NullPointerException");
        }
    }

    @Test
    public void testSearchTemplatesFiltersByOccurrenceCountRange() {
        long now = System.currentTimeMillis();
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("o1"), "WARN", "c.A", null, "m1", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("o5"), "WARN", "c.A", null, "m2", 5L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("o10"), "WARN", "c.A", null, "m3", 10L, now, now)));

        List<TemplateRow> midRange = dao.searchTemplates(criteria()
                .occurrenceCountFrom(2L)
                .occurrenceCountTo(7L)
                .build(), 100, 0);
        assertEquals(midRange.size(), 1);
        assertEquals(midRange.get(0).occurrenceCount(), 5L);

        List<TemplateRow> fromOnly = dao.searchTemplates(criteria()
                .occurrenceCountFrom(5L)
                .build(), 100, 0);
        assertEquals(fromOnly.size(), 2);
    }

    @Test
    public void testSearchTemplatesFiltersByLastOccurrenceRangeAndOrders() {
        long t1 = 1_000_000L;
        long t2 = 2_000_000L;
        long t3 = 3_000_000L;
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("t1"), "ERROR", "c.A", null, "m1", 1L, t1, t1)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("t2"), "ERROR", "c.A", null, "m2", 1L, t2, t2)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("t3"), "ERROR", "c.A", null, "m3", 1L, t3, t3)));

        List<TemplateRow> windowed = dao.searchTemplates(criteria()
                .lastOccurrenceFrom(t1)
                .lastOccurrenceTo(t2)
                .build(), 100, 0);
        assertEquals(windowed.size(), 2);
        // Order is last_occurrence_at DESC, id DESC — newest within the window first.
        assertEquals(windowed.get(0).lastOccurrenceAt(), t2);
        assertEquals(windowed.get(1).lastOccurrenceAt(), t1);
    }

    @Test
    public void testSearchTemplatesPaginationLimitOffsetSplitsResultSet() {
        long base = 1_000_000L;
        for (int i = 0; i < 7; i++) {
            dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("p" + i), "WARN", "c.A", null,
                    "m" + i, 1L, base + i, base + i)));
        }
        List<TemplateRow> page1 = dao.searchTemplates(criteria().build(), 5, 0);
        List<TemplateRow> page2 = dao.searchTemplates(criteria().build(), 5, 5);
        assertEquals(page1.size(), 5);
        assertEquals(page2.size(), 2);
        // Pages must not overlap.
        long firstPageMinTs = page1.stream().mapToLong(TemplateRow::lastOccurrenceAt).min().orElseThrow();
        for (TemplateRow row : page2) {
            assertTrue(row.lastOccurrenceAt() < firstPageMinTs,
                    "Page 2 rows must be strictly older than the oldest of page 1");
        }
    }

    @Test
    public void testSearchTemplatesDeterministicOrderingByIdTiebreaker() {
        // Two rows with identical last_occurrence_at — order must be stable across repeated calls
        // because id DESC is the tiebreaker.
        long ts = 5_000_000L;
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("d1"), "WARN", "c.A", null, "m1", 1L, ts, ts)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("d2"), "WARN", "c.A", null, "m2", 1L, ts, ts)));

        List<TemplateRow> first = dao.searchTemplates(criteria().build(), 100, 0);
        for (int i = 0; i < 4; i++) {
            List<TemplateRow> next = dao.searchTemplates(criteria().build(), 100, 0);
            assertEquals(next.size(), first.size());
            for (int j = 0; j < first.size(); j++) {
                assertEquals(next.get(j).id(), first.get(j).id(),
                        "Row " + j + " id must be byte-identical across calls (iteration " + i + ")");
            }
        }
        // The higher id appears first under DESC ordering.
        long firstId = first.get(0).id();
        long secondId = first.get(1).id();
        assertTrue(firstId > secondId, "DESC tiebreaker on id must put higher id first");
    }

    @Test
    public void testSearchTemplatesTsqueryAndOrModes() {
        // PG-only path: the agentic searchTemplates always uses to_tsquery('english', ?) — on other
        // engines that function does not exist, so the production code gates non-PG at HTTP 501.
        if (dbProperties.getDbType() != DbType.POSTGRESQL) {
            throw new SkipException("tsquery branch is PG-only — service layer rejects non-PG with HTTP 501");
        }
        long now = System.currentTimeMillis();
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("ts1"), "ERROR", "c.A", null,
                "checksum mismatch on repository upload", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("ts2"), "ERROR", "c.A", null,
                "repository sync failed", 1L, now, now)));
        dao.upsertBatch(List.of(new TemplateUpsertRequest(hash("ts3"), "ERROR", "c.A", null,
                "disk usage threshold", 1L, now, now)));

        // AND mode requires both tokens — only ts1 matches "checksum & repository".
        List<TemplateRow> andHits = dao.searchTemplates(criteria()
                .tsqueryString("checksum & repository")
                .build(), 100, 0);
        assertEquals(andHits.size(), 1);
        assertEquals(andHits.get(0).signatureHash(), hash("ts1"));

        // OR mode matches either — ts1 and ts2 share "repository".
        List<TemplateRow> orHits = dao.searchTemplates(criteria()
                .tsqueryString("checksum | repository")
                .build(), 100, 0);
        assertEquals(orHits.size(), 2);
    }

    @Test
    public void testUpsertBatchMergesDuplicatesWithinBatch() {
        // Same signature appearing twice in one batch must invoke the merge path on the second
        // iteration, not produce a duplicate row.
        long t = System.currentTimeMillis();
        String hMerged = hash("batchMerge");
        String hSolo = hash("batchSolo");
        dao.upsertBatch(List.of(
                new TemplateUpsertRequest(hMerged, "WARN", "c.A", null, "m", 1L, t, t),
                new TemplateUpsertRequest(hSolo, "ERROR", "c.B", null, "m", 1L, t, t),
                new TemplateUpsertRequest(hMerged, "WARN", "c.A", null, "m", 3L, t + 10, t + 10)));

        TemplateRow merged = dao.findBySignatureHash(hMerged).orElseThrow();
        assertEquals(merged.occurrenceCount(), 4L);
        assertEquals(merged.lastOccurrenceAt(), t + 10);
        assertTrue(dao.findBySignatureHash(hSolo).isPresent());
    }
}
