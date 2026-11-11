package org.artifactory.rest.services.devopsagent;

/**
 * Raw query-string carrier from {@code ErrorCatalogResource} to
 * {@code ErrorCatalogRestService#search}. The service is responsible for parsing, validating,
 * and clamping each field; the resource layer only collects {@code @QueryParam} values into this
 * shape so the service signature stays readable.
 */
public record ErrorCatalogSearchRequest(
        String idsCsv,
        String severitiesCsv,
        String loggerClassesCsv,
        String exceptionClassesCsv,
        Long occurrenceCountFrom,
        Long occurrenceCountTo,
        Long firstOccurrenceFrom,
        Long firstOccurrenceTo,
        Long lastOccurrenceFrom,
        Long lastOccurrenceTo,
        String search,
        boolean matchAll,
        int limit,
        int offset) {
}
