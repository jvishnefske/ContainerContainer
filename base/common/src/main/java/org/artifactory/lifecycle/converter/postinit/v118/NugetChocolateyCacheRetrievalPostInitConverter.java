package org.artifactory.lifecycle.converter.postinit.v118;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.common.ConstantValues.nugetChocolateyMinCachePeriodEnabled;
import static org.artifactory.common.ConstantValues.nugetChocolateyMinCachePeriodSeconds;

/**
 * Enforces a minimum retrievalCachePeriodSecs of 4 hours (14400 seconds) on NuGet remote
 * repositories whose upstream URL points to chocolatey.org.
 * <p>
 * Repositories that already have a cache period >= 14400 are left untouched.
 *
 * @author Arko Jyoti De
 */
@Slf4j
public class NugetChocolateyCacheRetrievalPostInitConverter extends PostInitConverter {

    private static final String CHOCOLATEY_URL_MARKER = "chocolatey.org";

    public NugetChocolateyCacheRetrievalPostInitConverter(ArtifactoryServiceVersion from,
            ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        if (!nugetChocolateyMinCachePeriodEnabled.getBoolean()) {
            log.info("NugetChocolateyCacheRetrievalPostInitConverter is disabled by feature flag");
            return;
        }
        try {
            log.info("Starting NugetChocolateyCacheRetrievalPostInitConverter");
            long startTime = System.currentTimeMillis();
            RepositoryConfigService repositoryConfigService =
                    ContextHelper.get().beanForType(RepositoryConfigService.class);
            List<RemoteRepoConfig> modifiedConfigs = getChocolateyReposNeedingUpdate(repositoryConfigService);
            if (!modifiedConfigs.isEmpty()) {
                SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
                securityService.doAsSystem(
                        () -> repositoryConfigService.bulkUpsertRepoConfigs(modifiedConfigs, true));
                log.info("Updated retrievalCachePeriodSecs to {} for {} NuGet Chocolatey remote repo(s)",
                        nugetChocolateyMinCachePeriodSeconds.getLong(), modifiedConfigs.size());
            }
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Finished NugetChocolateyCacheRetrievalPostInitConverter in {} ms", elapsed);
        } catch (Exception e) {
            log.error("Failed to convert NuGet Chocolatey remote repos: {}", e.getMessage(), e);
        }
    }

    private List<RemoteRepoConfig> getChocolateyReposNeedingUpdate(RepositoryConfigService repositoryConfigService) {
        List<RemoteRepoConfig> modifiedConfigs = new ArrayList<>();
        repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE).stream()
                .map(RemoteRepoConfig.class::cast)
                .filter(config -> config.getPackageType() == RepoType.NuGet)
                .filter(this::isChocolateyUpstream)
                .filter(this::isCachePeriodBelowMinimum)
                .forEach(config -> {
                    long previous = config.getRepoTypeConfig().getRetrievalCachePeriodSecs();
                    config.getRepoTypeConfig().setRetrievalCachePeriodSecs(
                            nugetChocolateyMinCachePeriodSeconds.getLong());
                    log.info("Repo '{}': retrievalCachePeriodSecs {} -> {}",
                            config.getKey(), previous, nugetChocolateyMinCachePeriodSeconds.getLong());
                    modifiedConfigs.add(config);
                });
        return modifiedConfigs;
    }

    private boolean isChocolateyUpstream(RemoteRepoConfig config) {
        String url = config.getRepoTypeConfig().getUrl();
        return StringUtils.isNotBlank(url) && StringUtils.containsIgnoreCase(url, CHOCOLATEY_URL_MARKER);
    }

    private boolean isCachePeriodBelowMinimum(RemoteRepoConfig config) {
        return config.getRepoTypeConfig().getRetrievalCachePeriodSecs() <
                nugetChocolateyMinCachePeriodSeconds.getLong();
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // no preconditions
    }
}
