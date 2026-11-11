package org.artifactory.storage.db.devopsagent.requestlog.service;

import jakarta.annotation.PreDestroy;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogIdentifiable;
import org.artifactory.storage.db.devopsagent.requestlog.model.Cursor;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BooleanSupplier;

/**
 * Direction-agnostic base for request-log service implementations.
 * Owns the write queue, flush lifecycle, parameter validation, cursor decode, and result shaping.
 * Concrete subclasses supply the row type {@code R}, the DAO insert call, and the feature flag.
 */
@Slf4j
public abstract class AbstractRequestLogServiceImpl<R extends RequestLogIdentifiable>
        implements RequestLogFlushable {

    static final int LIMIT_MAX = 2000;
    static final int OFFSET_MAX = 10_000;
    static final int SEARCH_MAX_LEN = 256;
    static final String FEATURE_DISABLED_MESSAGE = "request log API is disabled by configuration";
    static final String TIME_FROM_AFTER_TIME_TO_MESSAGE = "'time_from' must be <= 'time_to'";
    static final String MIN_DURATION_MS_NEGATIVE_MESSAGE = "'min_duration_ms' must be >= 0";
    static final String SEARCH_QUERY_TOO_LONG_MESSAGE = "'search' length exceeds 256";
    static final String LIMIT_OUT_OF_RANGE_MESSAGE = "'limit' must be between 1 and 2000";
    static final String OFFSET_EXCEEDS_MAX_MESSAGE =
            "'offset' exceeds maximum " + OFFSET_MAX + " — use 'cursor' parameter to page deeper";

    static final String OFFSET_OUT_OF_RANGE_MESSAGE = "'offset' must be between 0 and 10000";
    static final String MALFORMED_CURSOR_MESSAGE = "'cursor' is malformed";
    static final int TRACE_PER_DIRECTION_CAP = 10_000;
    static final String INTERNAL_ERROR_MESSAGE = "Internal error in request log API";
    private static final int DEFAULT_QUEUE_CAPACITY =
            Integer.parseInt(ConstantValues.devopsAgentRequestLogQueueCapacity.getDefValue());

    private final LinkedBlockingQueue<R> queue;

    /**
     * Kill-switch supplier — overridable in tests that don't bind an {@code ArtifactoryHome}.
     */
    @Setter
    private BooleanSupplier flagEnabledSupplier;

    protected AbstractRequestLogServiceImpl(int queueCapacity, BooleanSupplier defaultFlag) {
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.flagEnabledSupplier = defaultFlag;
    }

    protected static int resolveQueueCapacity() {
        if (!ArtifactoryHome.isBound()) {
            return DEFAULT_QUEUE_CAPACITY;
        }
        return ConstantValues.devopsAgentRequestLogQueueCapacity.getInt();
    }

    /** Persist a batch of rows to the DB; called from the flush loop. */
    protected abstract void insertBatch(List<R> rows) throws SQLException;

    /** Direction label used in log messages, e.g. {@code "inbound"} or {@code "outbound"}. */
    protected abstract String direction();

    @Override
    public boolean isFeatureEnabled() {
        return flagEnabledSupplier.getAsBoolean();
    }

    /** Throws {@link RequestLogException}(501) when the feature flag is off. */
    public void assertFeatureEnabled() {
        if (!flagEnabledSupplier.getAsBoolean()) {
            throw new RequestLogException(HttpStatus.SC_NOT_IMPLEMENTED, FEATURE_DISABLED_MESSAGE);
        }
    }

    protected void enqueue(R row) {
        if (!isFeatureEnabled()) {
            return;
        }
        if (!RequestLogValidation.isValidMethod(row.httpMethod())) {
            log.warn("agentic_api.request_logs.invalid_method method={}", row.httpMethod());
            return;
        }
        if (!RequestLogValidation.isValidStatus(row.statusCode())) {
            log.warn("agentic_api.request_logs.invalid_status code={}", row.statusCode());
            return;
        }
        if (row.statusCode() < 400) {
            return;
        }
        if (!queue.offer(row)) {
            log.warn("agentic_api.request_logs.queue_full direction={} dropped=1", direction());
        }
    }

    @Override
    public void flush() {
        flushBatch(ConstantValues.devopsAgentRequestLogBatchSize.getInt());
    }

    @PreDestroy
    void shutdown() {
        flush();
    }

    private void flushBatch(int batchSize) {
        List<R> batch = new ArrayList<>(batchSize);
        while (queue.drainTo(batch, batchSize) > 0) {
            try {
                insertBatch(batch);
            } catch (SQLException e) {
                log.error("agentic_api.request_logs.flush_error direction={} rows={}",
                        direction(), batch.size(), e);
            }
            batch = new ArrayList<>(batchSize);
        }
    }

    void validate(SearchCriteria criteria) {
        if (criteria.timeFrom() != null && criteria.timeTo() != null
                && criteria.timeFrom() > criteria.timeTo()) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, TIME_FROM_AFTER_TIME_TO_MESSAGE);
        }
        if (criteria.minDurationMs() < 0) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, MIN_DURATION_MS_NEGATIVE_MESSAGE);
        }
        if (criteria.searchQuery() != null && criteria.searchQuery().length() > SEARCH_MAX_LEN) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, SEARCH_QUERY_TOO_LONG_MESSAGE);
        }
        if (criteria.limit() < 1 || criteria.limit() > LIMIT_MAX) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, LIMIT_OUT_OF_RANGE_MESSAGE);
        }
        if (criteria.offset() > OFFSET_MAX) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, OFFSET_EXCEEDS_MAX_MESSAGE);
        }
        if (criteria.offset() < 0) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, OFFSET_OUT_OF_RANGE_MESSAGE);
        }
    }

    @FunctionalInterface
    interface SqlSearch<T> {
        List<T> get() throws SQLException;
    }

    protected SearchResult<R> doSearch(SearchCriteria criteria, SqlSearch<R> daoSearch, String endpoint) {
        assertFeatureEnabled();
        validate(criteria);
        try {
            return shapeResult(daoSearch.get(), criteria.limit());
        } catch (SQLException e) {
            log.error("agentic_api.request_logs.internal_error endpoint={}", endpoint, e);
            throw new RequestLogException(HttpStatus.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, e);
        }
    }

    protected static <T extends RequestLogIdentifiable> SearchResult<T> shapeResult(
            List<T> rows, int limit) {
        if (rows == null || rows.isEmpty()) {
            return new SearchResult<>(Collections.emptyList(), false, null);
        }
        boolean hasMore = rows.size() > limit;
        List<T> trimmed = hasMore ? rows.subList(0, limit) : rows;
        T last = trimmed.get(trimmed.size() - 1);
        Cursor next = hasMore ? new Cursor(last.requestTime(), last.id()) : null;
        return new SearchResult<>(trimmed, hasMore, next);
    }
}
