package org.artifactory.rest.resource.devopsagent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Envelope for {@code GET /api/devopsagent/errors/events}. Up to {@code limit} raw events
 * plus an opaque {@code nextPageToken}. The token is omitted entirely (not serialised as
 * {@code "nextPageToken":null}) on the last page — agents parse for field presence per
 * api-documentation v2 § 5.5.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorCatalogEventsResponse(
        List<ErrorCatalogEventEntry> events,
        String nextPageToken) {
}
