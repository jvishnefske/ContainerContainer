package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One inbound row in the agentic request-log API response. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"id", "requestTime", "traceId", "username", "httpMethod", "requestPath",
        "statusCode", "durationMs"})
public record RequestLogEntryModel(
        long id,
        long requestTime,
        String traceId,
        String username,
        String httpMethod,
        String requestPath,
        int statusCode,
        long durationMs) {
}
