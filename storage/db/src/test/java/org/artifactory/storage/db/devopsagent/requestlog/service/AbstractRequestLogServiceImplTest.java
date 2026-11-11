package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogIdentifiable;
import org.artifactory.storage.db.devopsagent.requestlog.model.Cursor;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;
import org.artifactory.test.ArtifactoryHomeStub;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the decision points in {@link AbstractRequestLogServiceImpl} not already covered
 * by {@link AbstractRequestLogServiceImplFeatureFlagTest}.
 * <p>
 * Covers: {@code validate} (6 throw conditions + happy path), {@code enqueue} queue-full path,
 * {@code shapeResult} (3 cases), {@code doSearch} SQLException wrapping, {@code shutdown} flush,
 * and {@code flush} batch draining.
 */
public class AbstractRequestLogServiceImplTest {

    private static final int DEFAULT_QUEUE_CAPACITY = 10;

    private ArtifactoryHomeStub homeStub;
    private TestService service;

    @BeforeMethod
    public void setUp() {
        homeStub = new ArtifactoryHomeStub(getClass().getName());
        homeStub.bind();
        service = new TestService(DEFAULT_QUEUE_CAPACITY);
    }

    @AfterMethod
    public void tearDown() {
        ArtifactoryHomeStub.unbind();
    }

    // -------------------------------------------------------------------------
    // validate — 6 throw conditions + happy path
    // -------------------------------------------------------------------------

    @Test
    public void validate_throwsWhenFromAfterTo() {
        SearchCriteria criteria = baseCriteria().timeFrom(10L).timeTo(5L).build();
        RequestLogException ex = expectThrows(RequestLogException.class, () -> service.validate(criteria));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.TIME_FROM_AFTER_TIME_TO_MESSAGE),
                "Message should contain TIME_FROM_AFTER_TIME_TO_MESSAGE");
    }

    @Test
    public void validate_throwsWhenMinDurationNegative() {
        SearchCriteria criteria = baseCriteria().minDurationMs(-1L).build();
        RequestLogException ex = expectThrows(RequestLogException.class, () -> service.validate(criteria));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.MIN_DURATION_MS_NEGATIVE_MESSAGE),
                "Message should contain MIN_DURATION_MS_NEGATIVE_MESSAGE");
    }

    @Test
    public void validate_throwsWhenSearchQueryTooLong() {
        String longQuery = "x".repeat(257);
        SearchCriteria criteria = baseCriteria().searchQuery(longQuery).build();
        RequestLogException ex = expectThrows(RequestLogException.class, () -> service.validate(criteria));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.SEARCH_QUERY_TOO_LONG_MESSAGE),
                "Message should contain SEARCH_QUERY_TOO_LONG_MESSAGE");
    }

    @Test
    public void validate_throwsWhenLimitTooLow() {
        SearchCriteria criteria = baseCriteria().limit(0).build();
        RequestLogException ex = expectThrows(RequestLogException.class, () -> service.validate(criteria));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.LIMIT_OUT_OF_RANGE_MESSAGE),
                "Message should contain LIMIT_OUT_OF_RANGE_MESSAGE");
    }

    @Test
    public void validate_throwsWhenLimitTooHigh() {
        SearchCriteria criteria = baseCriteria().limit(2001).build();
        RequestLogException ex = expectThrows(RequestLogException.class, () -> service.validate(criteria));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.LIMIT_OUT_OF_RANGE_MESSAGE),
                "Message should contain LIMIT_OUT_OF_RANGE_MESSAGE");
    }

    @Test
    public void validate_throwsWhenOffsetExceedsMax() {
        SearchCriteria criteria = baseCriteria().offset(10001).build();
        RequestLogException ex = expectThrows(RequestLogException.class, () -> service.validate(criteria));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.OFFSET_EXCEEDS_MAX_MESSAGE),
                "Message should contain OFFSET_EXCEEDS_MAX_MESSAGE");
    }

    @Test
    public void validate_throwsWhenOffsetNegative() {
        SearchCriteria criteria = baseCriteria().offset(-1).build();
        RequestLogException ex = expectThrows(RequestLogException.class, () -> service.validate(criteria));
        assertEquals(ex.getStatusCode(), 400);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.OFFSET_OUT_OF_RANGE_MESSAGE),
                "Message should contain OFFSET_OUT_OF_RANGE_MESSAGE");
    }

    @Test
    public void validate_happyPath_noException() {
        SearchCriteria criteria = baseCriteria().build();
        // Should not throw
        service.validate(criteria);
    }

    // -------------------------------------------------------------------------
    // enqueue — queue-full path
    // -------------------------------------------------------------------------

    @Test
    public void enqueue_dropsWhenQueueFull() {
        // Create a service with capacity=1 so the second enqueue triggers queue-full
        TestService smallService = new TestService(1);
        RequestLogIdentifiable row1 = rowWithStatus(500);
        RequestLogIdentifiable row2 = rowWithStatus(500);

        // Fill the queue
        smallService.enqueue(row1);
        // Second enqueue must silently drop (not throw)
        smallService.enqueue(row2);

        // No flush happened — batches must be empty
        assertTrue(smallService.batches.isEmpty(),
                "No batches should have been inserted without an explicit flush");
    }

    // -------------------------------------------------------------------------
    // enqueue — validation
    // -------------------------------------------------------------------------

    @Test
    public void enqueue_dropsRowWithNullHttpMethod() {
        service.enqueue(rowWithMethod(null));
        service.flush();
        assertTrue(service.batches.isEmpty(), "null httpMethod must be dropped before queue");
    }

    @Test
    public void enqueue_dropsRowWithInvalidHttpMethod() {
        for (String method : new String[]{"PROPFIND", "MKCOL", "", "INVALID"}) {
            service.enqueue(rowWithMethod(method));
        }
        service.flush();
        assertTrue(service.batches.isEmpty(), "non-whitelisted httpMethods must be dropped");
    }

    @Test
    public void enqueue_acceptsAllNineWhitelistedMethods() {
        String[] validMethods = {"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "CONNECT"};
        // Use status >= 400 so rows pass the existing status filter and reach the queue
        for (String m : validMethods) {
            TestService svc = new TestService(10);
            svc.enqueue(rowWithMethodAndStatus(m, 500));
            svc.flush();
            assertFalse(svc.batches.isEmpty(), "valid method " + m + " must reach DB");
        }
    }

    @Test
    public void enqueue_acceptsLowercaseMethod_afterNormalization() {
        // Producers normalize to upper-case, but the validator itself also handles lower-case
        TestService svc = new TestService(10);
        svc.enqueue(rowWithMethodAndStatus("get", 500));
        svc.flush();
        assertFalse(svc.batches.isEmpty(), "lower-case 'get' must be accepted by case-insensitive validation");
    }

    @Test
    public void enqueue_dropsRowWithStatusCodeBelow100() {
        for (int code : new int[]{0, -1, 99}) {
            service.enqueue(rowWithMethodAndStatus("GET", code));
        }
        service.flush();
        assertTrue(service.batches.isEmpty(), "statusCode < 100 must be dropped");
    }

    @Test
    public void enqueue_dropsRowWithStatusCodeAbove599() {
        for (int code : new int[]{600, 999, 9999}) {
            service.enqueue(rowWithMethodAndStatus("GET", code));
        }
        service.flush();
        assertTrue(service.batches.isEmpty(), "statusCode > 599 must be dropped");
    }

    @Test
    public void enqueue_acceptsBoundaryStatuses() {
        // 100 and 599 must pass validation (but 100-399 are dropped by the existing < 400 filter,
        // so use 400 and 599 to confirm both pass validation AND reach the queue)
        TestService svc = new TestService(10);
        svc.enqueue(rowWithMethodAndStatus("GET", 400));
        svc.enqueue(rowWithMethodAndStatus("GET", 599));
        svc.flush();
        assertFalse(svc.batches.isEmpty(), "status 400 and 599 must reach DB");
        assertEquals(svc.batches.stream().mapToInt(List::size).sum(), 2);
    }

    // -------------------------------------------------------------------------
    // shapeResult — 3 cases
    // -------------------------------------------------------------------------

    @Test
    public void shapeResult_emptyList_returnsEmptyResult() {
        SearchResult<RequestLogIdentifiable> result =
                AbstractRequestLogServiceImpl.shapeResult(Collections.emptyList(), 10);
        assertTrue(result.rows().isEmpty());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    public void shapeResult_nullList_returnsEmptyResult() {
        SearchResult<RequestLogIdentifiable> result =
                AbstractRequestLogServiceImpl.shapeResult(null, 10);
        assertTrue(result.rows().isEmpty());
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    public void shapeResult_exactFit_noMorePages() {
        List<RequestLogIdentifiable> rows = List.of(
                rowWithIdAndTime(1L, 100L),
                rowWithIdAndTime(2L, 200L),
                rowWithIdAndTime(3L, 300L)
        );
        SearchResult<RequestLogIdentifiable> result =
                AbstractRequestLogServiceImpl.shapeResult(rows, 3);
        assertEquals(result.rows().size(), 3);
        assertFalse(result.hasMore());
        assertNull(result.nextCursor());
    }

    @Test
    public void shapeResult_hasMore_trimsToCursorAndSetsNextCursor() {
        RequestLogIdentifiable third = rowWithIdAndTime(3L, 300L);
        List<RequestLogIdentifiable> rows = List.of(
                rowWithIdAndTime(1L, 100L),
                rowWithIdAndTime(2L, 200L),
                third,
                rowWithIdAndTime(4L, 400L)   // extra row signals more pages
        );
        SearchResult<RequestLogIdentifiable> result =
                AbstractRequestLogServiceImpl.shapeResult(rows, 3);

        assertEquals(result.rows().size(), 3);
        assertTrue(result.hasMore());
        assertNotNull(result.nextCursor());

        Cursor cursor = result.nextCursor();
        assertEquals(cursor.id(), third.id());
        assertEquals(cursor.requestTime(), third.requestTime());
    }

    // -------------------------------------------------------------------------
    // doSearch — SQLException wrapping
    // -------------------------------------------------------------------------

    @Test
    public void doSearch_wrapsSQLExceptionAs500() {
        SearchCriteria criteria = baseCriteria().build();
        RequestLogException ex = expectThrows(RequestLogException.class,
                () -> service.doSearch(criteria, () -> {
                    throw new SQLException("db error");
                }, "test_endpoint"));

        assertEquals(ex.getStatusCode(), 500);
        assertTrue(ex.getMessage().contains(AbstractRequestLogServiceImpl.INTERNAL_ERROR_MESSAGE),
                "Message should contain INTERNAL_ERROR_MESSAGE");
        assertTrue(ex.getCause() instanceof SQLException,
                "Cause should be the original SQLException");
        assertEquals(ex.getCause().getMessage(), "db error");
    }

    // -------------------------------------------------------------------------
    // shutdown — calls flush
    // -------------------------------------------------------------------------

    @Test
    public void shutdown_flushesQueue() {
        service.enqueue(rowWithStatus(500));
        service.shutdown();

        assertFalse(service.batches.isEmpty(), "shutdown() should have flushed the queue");
        assertEquals(service.batches.get(0).size(), 1);
    }

    // -------------------------------------------------------------------------
    // flush — drains batches via batchSize
    // -------------------------------------------------------------------------

    @Test
    public void flush_persistsBatchesViaBatchSize() {
        for (int i = 0; i < 5; i++) {
            service.enqueue(rowWithStatus(500));
        }
        service.flush();

        assertFalse(service.batches.isEmpty(), "flush() should have produced at least one batch");
        int total = service.batches.stream().mapToInt(List::size).sum();
        assertEquals(total, 5, "All 5 rows should have been persisted");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static SearchCriteria.SearchCriteriaBuilder baseCriteria() {
        return SearchCriteria.builder()
                .timeFrom(null)
                .timeTo(null)
                .minDurationMs(0L)
                .repoNames(Collections.emptyList())
                .searchQuery(null)
                .rawSearchText(null)
                .limit(100)
                .offset(0)
                .cursor(null);
    }

    private static RequestLogIdentifiable rowWithStatus(int statusCode) {
        return new RequestLogIdentifiable() {
            @Override
            public long id() {
                return 1L;
            }

            @Override
            public long requestTime() {
                return 123456789L;
            }

            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public String httpMethod() {
                return "GET";
            }
        };
    }

    private static RequestLogIdentifiable rowWithMethod(String httpMethod) {
        return new RequestLogIdentifiable() {
            @Override public long id() { return 1L; }
            @Override public long requestTime() { return 123456789L; }
            @Override public int statusCode() { return 500; }
            @Override public String httpMethod() { return httpMethod; }
        };
    }

    private static RequestLogIdentifiable rowWithMethodAndStatus(String httpMethod, int statusCode) {
        return new RequestLogIdentifiable() {
            @Override public long id() { return 1L; }
            @Override public long requestTime() { return 123456789L; }
            @Override public int statusCode() { return statusCode; }
            @Override public String httpMethod() { return httpMethod; }
        };
    }

    private static RequestLogIdentifiable rowWithIdAndTime(long id, long requestTime) {
        return new RequestLogIdentifiable() {
            @Override
            public long id() {
                return id;
            }

            @Override
            public long requestTime() {
                return requestTime;
            }

            @Override
            public int statusCode() {
                return 500;
            }

            @Override
            public String httpMethod() {
                return "GET";
            }
        };
    }

    // -------------------------------------------------------------------------
    // In-test concrete subclass — avoids Spring wiring
    // -------------------------------------------------------------------------

    private static class TestService extends AbstractRequestLogServiceImpl<RequestLogIdentifiable> {

        final List<List<RequestLogIdentifiable>> batches = new ArrayList<>();

        TestService(int queueCapacity) {
            super(queueCapacity, () -> true);  // feature always enabled
        }

        @Override
        protected void insertBatch(List<RequestLogIdentifiable> rows) throws SQLException {
            batches.add(new ArrayList<>(rows));
        }

        @Override
        protected String direction() {
            return "test";
        }

        // Expose package-private methods for direct testing
        @Override
        void validate(SearchCriteria criteria) {
            super.validate(criteria);
        }

        @Override
        protected void enqueue(RequestLogIdentifiable row) {
            super.enqueue(row);
        }
    }
}
