package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One outbound row in the agentic request-log API response. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"id", "requestTime", "traceId", "repoKey", "httpMethod",
        "requestUrl", "statusCode", "durationMs"})
public record OutboundRequestLogEntryModel(
        long id,
        long requestTime,
        String traceId,
        String repoKey,
        String httpMethod,
        String requestUrl,
        int statusCode,
        long durationMs) {
}
