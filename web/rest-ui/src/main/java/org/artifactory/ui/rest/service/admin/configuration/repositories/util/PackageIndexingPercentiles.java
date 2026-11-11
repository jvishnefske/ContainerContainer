package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import lombok.Builder;

/**
 * Per-package-type indexing duration percentiles in milliseconds, computed per-node from local
 * samples. Returned as-is by {@link GetPackageIndexingPercentilesService} to power the
 * create/edit repository UI; for cluster-wide percentiles use the Coralogix dashboard.
 *
 * @author Linoy Levi
 */
@Builder
public record PackageIndexingPercentiles(
        String packageType,
        double p50Ms,
        double p95Ms,
        double p99Ms) {
}
