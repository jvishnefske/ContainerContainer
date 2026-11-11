package org.artifactory.rest.resource.devopsagent.dto;

/**
 * Single agent-facing result row from {@code GET /api/devopsagent/errors}.
 * Field set is frozen by the API documentation v2 — adding or renaming a field is a contract break.
 */
public record ErrorCatalogEntry(
        long id,
        String signatureHash,
        String severity,
        String loggerClass,
        String exceptionClass,
        String templateMessage,
        long firstOccurrenceAt,
        long lastOccurrenceAt,
        long occurrenceCount) {
}
