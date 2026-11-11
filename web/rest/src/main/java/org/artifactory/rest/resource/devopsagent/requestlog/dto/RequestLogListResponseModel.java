package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"logs", "hasMore", "nextCursor"})
public record RequestLogListResponseModel<T>(
        List<T> logs,
        boolean hasMore,
        String nextCursor) {
}
