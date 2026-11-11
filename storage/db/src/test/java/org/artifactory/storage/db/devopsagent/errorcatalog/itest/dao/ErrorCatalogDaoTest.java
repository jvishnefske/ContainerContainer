package org.artifactory.storage.db.devopsagent.errorcatalog.itest.dao;

import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.entity.RawEventRow;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.KeysetCursor;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.RawEventInsertRequest;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.TemplateUpsertRequest;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Integration tests for {@link ErrorCatalogDao}. Targets the per-DB SQL branches (batched
 * INSERT, keyset pagination, time-range scan, chunked DELETE). One test per behaviour we care
 * about — trivial null/empty input guards on the DAO are intentionally not exercised here.
 */
public class ErrorCatalogDaoTest extends DbBaseTest {

    @Autowired
    private ErrorCatalogDao rawDao;

    @Autowired
    private ErrorCatalogTemplateDao templatesDao;

    private long templateId;

    @AfterMethod
    public void tearDown() {
        rawDao.deleteAll();
        templatesDao.deleteAll();
    }

    private long ensureTemplate(String sig) {
        long now = System.currentTimeMillis();
        templatesDao.upsertBatch(List.of(new TemplateUpsertRequest(sig, "ERROR", "c.A", null, "m", 1L, now, now)));
        return templatesDao.findBySignatureHash(sig).orElseThrow().id();
    }

    @Test
    public void testBatchInsertReturnsCount() {
        templateId = ensureTemplate("h-batch");
        long now = System.currentTimeMillis();
        int inserted = rawDao.insertBatch(List.of(
                new RawEventInsertRequest(templateId, now, "msg 1"),
                new RawEventInsertRequest(templateId, now + 1, "msg 2"),
                new RawEventInsertRequest(templateId, now + 2, "msg 3")));
        assertEquals(inserted, 3);
        assertEquals(rawDao.count(), 3L);
    }

    @Test
    public void testFindByTemplateReturnsTimeDescending() {
        templateId = ensureTemplate("h-drill");
        long t0 = 1_000_000L;
        rawDao.insertBatch(List.of(
                new RawEventInsertRequest(templateId, t0, "first"),
                new RawEventInsertRequest(templateId, t0 + 100, "second"),
                new RawEventInsertRequest(templateId, t0 + 200, "third")));

        List<RawEventRow> rows = rawDao.findByTemplate(templateId, null, 10);
        assertEquals(rows.size(), 3);
        assertTrue(rows.get(0).occurredAt() >= rows.get(1).occurredAt());
        assertTrue(rows.get(1).occurredAt() >= rows.get(2).occurredAt());
    }

    @Test
    public void testKeysetPaginationOnRaw() {
        templateId = ensureTemplate("h-keyset");
        long t0 = 9_000_000L;
        for (int i = 0; i < 6; i++) {
            rawDao.insertBatch(List.of(new RawEventInsertRequest(templateId, t0 + i, "m" + i)));
        }
        List<RawEventRow> firstPage = rawDao.findByTemplate(templateId, null, 3);
        assertEquals(firstPage.size(), 3);
        RawEventRow last = firstPage.get(firstPage.size() - 1);
        List<RawEventRow> secondPage = rawDao.findByTemplate(templateId,
                KeysetCursor.of(last.occurredAt(), last.id()), 3);
        assertEquals(secondPage.size(), 3);
        long firstPageMinId = firstPage.stream().mapToLong(RawEventRow::id).min().orElseThrow();
        for (RawEventRow r : secondPage) {
            assertTrue(r.id() < firstPageMinId);
        }
    }

    @Test
    public void testTimeRangeScanIncludesInsideExcludesOutside() {
        templateId = ensureTemplate("h-range");
        long t0 = 10_000_000L;
        rawDao.insertBatch(List.of(
                new RawEventInsertRequest(templateId, t0 - 10, "outside-before"),
                new RawEventInsertRequest(templateId, t0 + 50, "inside"),
                new RawEventInsertRequest(templateId, t0 + 200, "outside-after")));
        List<RawEventRow> inWindow = rawDao.findByOccurredBetween(t0, t0 + 100, 10);
        assertEquals(inWindow.size(), 1);
        assertEquals(inWindow.get(0).rawMessage(), "inside");
    }

    @Test
    public void testChunkedDelete() {
        templateId = ensureTemplate("h-chunked");
        long t0 = 100_000L;
        for (int i = 0; i < 25; i++) {
            rawDao.insertBatch(List.of(new RawEventInsertRequest(templateId, t0 + i, "m" + i)));
        }
        int deleted = rawDao.deleteOlderThanChunked(t0 + 20, 10, 0L);
        assertTrue(deleted >= 20);
        long remaining = rawDao.count();
        assertTrue(remaining <= 5);
    }

    /**
     * Defends the v2 design against re-introduction of partition-lifecycle DAO methods. Research
     * v2 § Implementation fallout removed {@code detachAndDropPartitionPg}, {@code dropPartitionMysql},
     * {@code createDailyPartition*}, and {@code PARTITION_DATE_FMT}. A future merge silently
     * re-adding any of them would break the uniform chunked-DELETE retention assumption. Reflection
     * scan: any declared method whose name contains {@code partition} (case-insensitive) fails the test.
     */
    @Test
    public void testNoPartitionMethodsOnDao() {
        for (Method m : ErrorCatalogDao.class.getDeclaredMethods()) {
            assertFalse(m.getName().toLowerCase().contains("partition"),
                    "ErrorCatalogDao must not expose partition-lifecycle methods (v2 design): "
                            + m.getName());
        }
    }

    // ============================================================
    // findByTemplatesAndTimeRange — RTDEV-86991 raw events drill-down
    // ============================================================

    @Test
    public void testFindByTemplatesAndTimeRangeHappyPath() {
        templateId = ensureTemplate("h-multi-happy");
        long t0 = 50_000L;
        rawDao.insertBatch(List.of(
                new RawEventInsertRequest(templateId, t0, "first"),
                new RawEventInsertRequest(templateId, t0 + 100, "second"),
                new RawEventInsertRequest(templateId, t0 + 200, "third")));

        List<RawEventRow> rows = rawDao.findByTemplatesAndTimeRange(
                List.of(templateId), null, null, null, 10);

        assertEquals(rows.size(), 3);
        // Ordered occurred_at DESC.
        assertTrue(rows.get(0).occurredAt() >= rows.get(1).occurredAt());
        assertTrue(rows.get(1).occurredAt() >= rows.get(2).occurredAt());
    }

    @Test
    public void testFindByTemplatesMultiIdInterleavedByTime() {
        long t1 = ensureTemplate("h-multi-A");
        long t2 = ensureTemplate("h-multi-B");
        long t0 = 60_000L;
        rawDao.insertBatch(List.of(
                new RawEventInsertRequest(t1, t0, "t1-early"),
                new RawEventInsertRequest(t2, t0 + 50, "t2-mid"),
                new RawEventInsertRequest(t1, t0 + 100, "t1-late"),
                new RawEventInsertRequest(t2, t0 + 150, "t2-late")));

        List<RawEventRow> rows = rawDao.findByTemplatesAndTimeRange(
                List.of(t1, t2), null, null, null, 10);

        assertEquals(rows.size(), 4);
        // Verify both templates are represented and the order is descending by time.
        long seenT1 = rows.stream().filter(r -> r.templateId() == t1).count();
        long seenT2 = rows.stream().filter(r -> r.templateId() == t2).count();
        assertEquals(seenT1, 2L);
        assertEquals(seenT2, 2L);
        for (int i = 0; i < rows.size() - 1; i++) {
            assertTrue(rows.get(i).occurredAt() >= rows.get(i + 1).occurredAt(),
                    "Rows must be ordered occurred_at DESC; got: " + rows);
        }
    }

    @Test
    public void testFindByTemplatesTimeWindowInclusiveLowerExclusiveUpper() {
        templateId = ensureTemplate("h-multi-range");
        long t0 = 70_000L;
        rawDao.insertBatch(List.of(
                new RawEventInsertRequest(templateId, t0, "boundary-from"),
                new RawEventInsertRequest(templateId, t0 + 50, "inside"),
                new RawEventInsertRequest(templateId, t0 + 100, "boundary-to")));

        // fromMs inclusive (t0 row INCLUDED); toMsExclusive exclusive (t0+100 row EXCLUDED).
        List<RawEventRow> rows = rawDao.findByTemplatesAndTimeRange(
                List.of(templateId), t0, t0 + 100, null, 10);

        assertEquals(rows.size(), 2);
        long matchedBoundaryFrom = rows.stream().filter(r -> r.occurredAt() == t0).count();
        long matchedInside = rows.stream().filter(r -> r.occurredAt() == t0 + 50).count();
        long matchedBoundaryTo = rows.stream().filter(r -> r.occurredAt() == t0 + 100).count();
        assertEquals(matchedBoundaryFrom, 1L, "fromMs is INCLUSIVE — row at t0 must be present");
        assertEquals(matchedInside, 1L);
        assertEquals(matchedBoundaryTo, 0L, "toMsExclusive is EXCLUSIVE — row at t0+100 must be absent");
    }

    @Test
    public void testFindByTemplatesKeysetCursorSkipsPriorRowsAndTieBreaks() {
        templateId = ensureTemplate("h-multi-keyset");
        long t0 = 80_000_000L;
        // 6 rows at distinct occurred_at + 2 rows sharing one occurred_at (tie-break case).
        for (int i = 0; i < 6; i++) {
            rawDao.insertBatch(List.of(new RawEventInsertRequest(templateId, t0 + i, "row-" + i)));
        }
        // Two rows at the SAME occurred_at — tie-break uses id DESC. Inserted in two separate
        // batches so each row gets its own auto-generated id.
        rawDao.insertBatch(List.of(new RawEventInsertRequest(templateId, t0 + 6, "tie-a")));
        rawDao.insertBatch(List.of(new RawEventInsertRequest(templateId, t0 + 6, "tie-b")));

        List<RawEventRow> firstPage = rawDao.findByTemplatesAndTimeRange(
                List.of(templateId), null, null, null, 3);
        assertEquals(firstPage.size(), 3);
        RawEventRow last = firstPage.get(firstPage.size() - 1);

        List<RawEventRow> secondPage = rawDao.findByTemplatesAndTimeRange(
                List.of(templateId), null, null, KeysetCursor.of(last.occurredAt(), last.id()), 10);
        // No overlap with the first page — every id in the second page is strictly older.
        for (RawEventRow r : secondPage) {
            for (RawEventRow seen : firstPage) {
                assertTrue(r.id() != seen.id(),
                        "Second page must not repeat rows from the first page");
            }
        }
        // Combined first + second page covers all 8 inserted rows.
        assertEquals(firstPage.size() + secondPage.size(), 8);
    }

    @Test
    public void testFindByTemplatesLimitHonoredExactly() {
        templateId = ensureTemplate("h-multi-limit");
        long t0 = 90_000_000L;
        for (int i = 0; i < 10; i++) {
            rawDao.insertBatch(List.of(new RawEventInsertRequest(templateId, t0 + i, "r-" + i)));
        }
        List<RawEventRow> rows = rawDao.findByTemplatesAndTimeRange(
                List.of(templateId), null, null, null, 4);
        assertEquals(rows.size(), 4);
    }
}
