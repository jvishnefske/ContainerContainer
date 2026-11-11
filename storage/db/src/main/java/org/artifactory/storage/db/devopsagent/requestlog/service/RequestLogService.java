package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;
import org.artifactory.storage.db.devopsagent.requestlog.model.TraceResult;

/**
 * Service for the agentic HTTP request-log API. Owns parameter validation, search-query
 * tokenization, keyset-cursor encoding/decoding, the feature-flag gate, and the
 * per-direction trace-row cap.
 * <p>
 * Throws {@link RequestLogException} for every failure mode; the REST resource layer translates
 * the exception into the host's {@code RestException}+{@code ErrorResponse} shape.
 */
public interface RequestLogService extends RequestLogFlushable {

    /** Search inbound HTTP requests. */
    SearchResult<RequestLogRow> searchInbound(SearchCriteria criteria);

    /**
     * Return up to 10,000 inbound rows sharing the given trace id within the
     * time window (default = last 24h when both bounds are null).
     */
    TraceResult findTrace(String traceId, Long timeFrom, Long timeTo);

    /** Enqueue one inbound row for batch persistence. No-op when feature is disabled or queue is full. */
    void enqueueInbound(RequestLogRow row);
}
