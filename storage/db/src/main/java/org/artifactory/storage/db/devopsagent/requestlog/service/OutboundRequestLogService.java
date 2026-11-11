package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;

import java.util.List;

/**
 * Service for outbound HTTP request logs (remote-repo fetches).
 * Owns the write queue, flush lifecycle, and read path for outbound rows.
 */
public interface OutboundRequestLogService extends RequestLogFlushable {

    /** Enqueue one outbound row for batch persistence. No-op when feature is disabled or queue is full. */
    void enqueueOutbound(OutboundRequestLogRow row);

    /** Search outbound request logs. Throws {@link RequestLogException}(501) when feature is disabled. */
    SearchResult<OutboundRequestLogRow> searchOutbound(SearchCriteria criteria);

    /**
     * Return up to {@code TRACE_PER_DIRECTION_CAP + 1} outbound rows sharing the given trace id
     * within the given time window. The extra row is a sentinel: if the result size exceeds
     * {@code TRACE_PER_DIRECTION_CAP}, the caller must trim to the cap and set {@code truncated=true}.
     * Does not gate on the feature flag — called internally from the trace endpoint.
     */
    List<OutboundRequestLogRow> findOutboundByTrace(String traceId, long from, long to);
}
