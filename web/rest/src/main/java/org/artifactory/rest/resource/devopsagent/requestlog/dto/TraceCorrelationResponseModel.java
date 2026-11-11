package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"traceId", "timeFrom", "timeTo", "inbound", "truncatedInbound", "outbound", "truncatedOutbound"})
public record TraceCorrelationResponseModel(
        String traceId,
        long timeFrom,
        long timeTo,
        List<RequestLogEntryModel> inbound,
        boolean truncatedInbound,
        List<OutboundRequestLogEntryModel> outbound,
        boolean truncatedOutbound) {
}
