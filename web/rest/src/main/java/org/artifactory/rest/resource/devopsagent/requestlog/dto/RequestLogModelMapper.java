package org.artifactory.rest.resource.devopsagent.requestlog.dto;

import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;

import java.util.List;

/** Stateless mapping between storage-db model rows and the REST DTOs. */
public final class RequestLogModelMapper {

    private RequestLogModelMapper() {
        // utility
    }

    public static RequestLogEntryModel toModel(RequestLogRow row) {
        return new RequestLogEntryModel(
                row.id(),
                row.requestTime(),
                row.traceId(),
                row.username(),
                row.httpMethod(),
                row.requestPath(),
                row.statusCode(),
                row.durationMs());
    }

    public static List<RequestLogEntryModel> toInboundModels(List<RequestLogRow> rows) {
        return rows.stream().map(RequestLogModelMapper::toModel).toList();
    }

    public static OutboundRequestLogEntryModel toOutboundModel(OutboundRequestLogRow row) {
        return new OutboundRequestLogEntryModel(
                row.id(),
                row.requestTime(),
                row.traceId(),
                row.repoKey(),
                row.httpMethod(),
                row.requestUrl(),
                row.statusCode(),
                row.durationMs());
    }

    public static List<OutboundRequestLogEntryModel> toOutboundModels(List<OutboundRequestLogRow> rows) {
        return rows.stream().map(RequestLogModelMapper::toOutboundModel).toList();
    }
}
