package org.artifactory.storage.db.devopsagent.requestlog.model;

import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;

import java.util.List;

/**
 * Result of a trace lookup — inbound and outbound rows sharing one {@code trace_id} within the
 * requested time window. Truncation flags are set when the DAO returned more than the per-direction
 * cap (10,000); the service trimmed to the cap and set the flag.
 */
public record TraceResult(
        List<RequestLogRow> inbound,
        boolean truncatedInbound,
        List<OutboundRequestLogRow> outbound,
        boolean truncatedOutbound) {
}
