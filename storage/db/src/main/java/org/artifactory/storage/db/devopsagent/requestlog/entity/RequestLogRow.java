package org.artifactory.storage.db.devopsagent.requestlog.entity;

import lombok.Builder;

/**
 * Immutable inbound HTTP request-log row.
 * Maps to the {@code request_logs} table; populated by the producer and read by
 * {@link org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao}.
 */
@Builder
public record RequestLogRow(
        long id,
        long requestTime,
        String traceId,
        String username,
        String httpMethod,
        String requestPath,
        int statusCode,
        long durationMs) implements RequestLogIdentifiable {
}
