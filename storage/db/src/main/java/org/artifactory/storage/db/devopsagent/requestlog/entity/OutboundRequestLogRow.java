package org.artifactory.storage.db.devopsagent.requestlog.entity;

import lombok.Builder;

@Builder
public record OutboundRequestLogRow(
        long id,
        long requestTime,
        String traceId,
        String repoKey,
        String httpMethod,
        String requestUrl,
        int statusCode,
        long durationMs
) implements RequestLogIdentifiable {
}
