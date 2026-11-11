package org.artifactory.federation.grid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Single site entry in a Grid {@code approve-creation} response, plus the
 * wire model for transparent recipients carried on
 * {@code RepoConfigChange.gridTransparentSites}. Lives in OSS so the
 * config-change record can reference it without depending on Pro types.
 *
 * @param jpdId    JPD identifier of the site (snake_case {@code jpd_id} on the wire)
 * @param url      Artifactory base URL of the site
 * @param active   true → active federated member; false → transparent record (key reservation only)
 * @param isOrigin true → the site that originated this {@code approve-creation} request
 *                 (snake_case {@code is_origin} on the wire). Local member; must be excluded
 *                 from federation member lists to avoid the "same host" guard.
 *
 * @author Tomer Ben Kereth
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GridSitePlan(
        String jpdId,
        String url,
        boolean active,
        boolean isOrigin
) {
}
