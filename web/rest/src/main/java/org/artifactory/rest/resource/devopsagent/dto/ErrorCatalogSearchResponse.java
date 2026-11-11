package org.artifactory.rest.resource.devopsagent.dto;

import java.util.List;

/**
 * Envelope for {@code GET /api/devopsagent/errors}. Up to {@code limit} entries plus a
 * hint-only {@code hasMore} flag computed by asking the DAO for {@code limit + 1} rows.
 */
public record ErrorCatalogSearchResponse(
        List<ErrorCatalogEntry> logs,
        boolean hasMore) {
}
