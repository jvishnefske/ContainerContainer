package org.artifactory.rest.services.devopsagent;

/**
 * Raw query-string carrier from {@code ErrorCatalogResource} to
 * {@code ErrorCatalogRestService#fetchEvents}. The service is responsible for parsing
 * {@code templateIdsCsv}, validating {@code occurredFrom} against {@code occurredTo},
 * decoding {@code pageToken}, and clamping {@code limit}. The resource layer only collects
 * {@code @QueryParam} values into this shape so the service signature stays readable.
 */
public record ErrorCatalogEventsRequest(
        String templateIdsCsv,
        Long occurredFrom,
        Long occurredTo,
        String pageToken,
        int limit) {
}
