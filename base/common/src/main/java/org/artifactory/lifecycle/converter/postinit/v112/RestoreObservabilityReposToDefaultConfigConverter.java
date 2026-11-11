package org.artifactory.lifecycle.converter.postinit.v112;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

import java.util.stream.Stream;

import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.BILLING;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.LOG_SHIPPING;
import static org.artifactory.repo.system.SystemRepos.SystemRepoConfig.USAGE;
import static org.artifactory.version.ArtifactoryServiceVersionProvider.v7990;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
public class RestoreObservabilityReposToDefaultConfigConverter extends PostInitConverter {

    public RestoreObservabilityReposToDefaultConfigConverter(ArtifactoryServiceVersion from,
            ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Started Observability repositories config restore");
        var repositoryConfigService = ContextHelper.get().beanForType(RepositoryConfigService.class);
        var systemReposDefaultConfigs = Stream.of(LOG_SHIPPING, USAGE, BILLING)
                .map(SystemRepos.SystemRepoConfig::getRepoKey)
                .map(repositoryConfigService::getRepoConfigByKey)
                .filter(LocalRepoConfig.class::isInstance)
                .map(LocalRepoConfig.class::cast)
                .peek(c -> c.getSecurityConfig().setXrayIndex(false))
                .toList();
        var securityService = ContextHelper.get().beanForType(SecurityService.class);
        securityService.doAsSystem(
                () -> repositoryConfigService.bulkUpsertRepoConfigs(systemReposDefaultConfigs, true)
        );
        log.info("Completed Observability repositories config restore");
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(v7990.get()) && target.getVersion().afterOrEqual(v7990.get());
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // noop
    }
}
