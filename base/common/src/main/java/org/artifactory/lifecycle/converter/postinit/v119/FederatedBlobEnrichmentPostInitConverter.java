package org.artifactory.lifecycle.converter.postinit.v119;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.FederatedRepoConfigEnrichmentService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

/**
 * Enriches "cold" federated repositories by copying their federation members from the legacy blob
 * location ({@code repoTypeConfig}) into the new {@code baseConfig.federationConfig} location.
 * <p>
 * Pairs with the {@code v284} {@code ScriptFileDbConverter} (which adds and backfills the {@code repo_type_v2} column):
 * the SQL converter handles the schema/column, while this post-init converter handles the blob transformation SQL
 * cannot perform (deserialize {@code config_blob}, copy members, reserialize).
 * <p>
 * Thin by design: delegates to {@link FederatedRepoConfigEnrichmentService#enrichColdFederatedReposBlobs()}, which
 * performs the dual-write, the fast bulk-update persistence and the in-memory cache rebuild.
 *
 */
@Slf4j
public class FederatedBlobEnrichmentPostInitConverter extends PostInitConverter {

    private final ArtifactoryServiceVersion targetVersion;

    /**
     * @param until the version at which this converter must run; {@code isInterested} fires on any upgrade whose
     *              target reaches this version, regardless of source version.
     */
    public FederatedBlobEnrichmentPostInitConverter(ArtifactoryServiceVersion until) {
        super(until, until);
        this.targetVersion = until;
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Starting cold federated repositories blob enrichment.");
        FederatedRepoConfigEnrichmentService enrichmentService =
                ContextHelper.get().beanForType(FederatedRepoConfigEnrichmentService.class);
        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        securityService.doAsSystem(enrichmentService::enrichColdFederatedReposBlobs);
        log.info("Finished cold federated repositories blob enrichment.");
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // noop
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().beforeOrEqual(targetVersion) && target.getVersion().afterOrEqual(targetVersion);
    }
}
