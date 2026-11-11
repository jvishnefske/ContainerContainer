package org.artifactory.lifecycle.converter.postinit.v111;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * This converter will disable the list remote folder items property for all none smart remote repositories that have
 * it enabled.
 *
 * @author Amir Avidan
 */
@Slf4j
public class DisableListingRemoteFolderItemsPostInitConverter extends PostInitConverter {

    public DisableListingRemoteFolderItemsPostInitConverter(ArtifactoryServiceVersion from,
            ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        try {
            log.info("Starting DisableListingRemoteFolderItemsPostInitConverter");
            long startTime = System.currentTimeMillis();
            RepositoryConfigService repositoryConfigService =
                    ContextHelper.get().beanForType(RepositoryConfigService.class);
            List<RemoteRepoConfig> remoteRepoConfigListWithoutSmart = getRemoteRepoConfigs(repositoryConfigService);
            SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
            securityService.doAsSystem(
                    () -> repositoryConfigService.bulkUpsertRepoConfigs(remoteRepoConfigListWithoutSmart, true));
            long endTime = System.currentTimeMillis();
            log.info("Converted remote repos, Set list remote folder items property to false." +
                    " Total time: {} milliseconds", endTime - startTime);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private List<RemoteRepoConfig> getRemoteRepoConfigs(RepositoryConfigService repositoryConfigService) {
        List<RemoteRepoConfig> modifiedRepoConfigs = new ArrayList<>();
        repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE).stream()
                .map(RemoteRepoConfig.class::cast)
                .filter(repoConfig -> repoConfig.getRepoTypeConfig().isListRemoteFolderItems())
                .filter(repoConfig -> !repoConfig.getRepoTypeConfig().getContentSynchronisation().isEnabled())
                .forEach(repoConfig -> {
                    repoConfig.getRepoTypeConfig().setListRemoteFolderItems(false);
                    modifiedRepoConfigs.add(repoConfig);
                });
        return modifiedRepoConfigs;
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        //noop
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v7940.get())
                && target.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v7940.get());
    }
}
