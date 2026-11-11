package org.artifactory.rest.services.devopsagent;

import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogEventsResponse;
import org.artifactory.rest.resource.devopsagent.dto.ErrorCatalogSearchResponse;

/**
 * Service backing the agentic read surface on {@code /api/devopsagent/errors}:
 * {@code GET /api/devopsagent/errors} (RTDEV-85639, templates) and
 * {@code GET /api/devopsagent/errors/events} (RTDEV-86991, raw events drill-down).
 *
 * <p>The implementation owns the feature-flag check (404), DB-type guard (501), parameter parsing
 * and validation (400 with stable single-quoted parameter-name message prefixes per
 * api-documentation v2 § 6.3), silent clamping of {@code limit} and {@code offset}, search
 * tokenisation, page-token (en|de)coding, DAO invocation, {@code hasMore} / {@code nextPageToken}
 * detection, and per-outcome counter emission.</p>
 */
public interface ErrorCatalogRestService {

    ErrorCatalogSearchResponse search(ErrorCatalogSearchRequest request);

    ErrorCatalogEventsResponse fetchEvents(ErrorCatalogEventsRequest request);
}
