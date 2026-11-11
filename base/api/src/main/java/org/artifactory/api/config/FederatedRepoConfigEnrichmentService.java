package org.artifactory.api.config;

/**
 * Dedicated service for the one-shot enrichment (migration) of federated repository configs.
 */
public interface FederatedRepoConfigEnrichmentService {

    /**
     * Enriches "cold" federated repositories. A cold federated repo has its federation members only in
     * the legacy blob location ({@code repoTypeConfig}); this copies them into the new
     * {@code baseConfig.federationConfig} location using the production dual-write setters, persists via the fast
     * bulk-update path, and rebuilds the in-memory cache. Idempotent: already dual-written ("warm") federated repos
     * and non-federated repos are skipped.
     */
    void enrichColdFederatedReposBlobs();
}
