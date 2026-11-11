package org.artifactory.rest.resource.devopsagent.dto;

/**
 * Single agent-facing raw event row from {@code GET /api/devopsagent/errors/events}.
 * Field set is frozen by API documentation v2 — adding or renaming a field is a contract break.
 */
public record ErrorCatalogEventEntry(
        long id,
        long templateId,
        long occurredAt,
        String rawMessage) {
}
