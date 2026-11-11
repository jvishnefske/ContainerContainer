package org.artifactory.lifecycle.converter.postinit.v114;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.client.util.PathUtils;

import java.util.List;

/**
 * @author Tamar Rosen
 */
@Slf4j
public class FederatedRemoteMembersConverter extends PostInitConverter {

    public FederatedRemoteMembersConverter(ArtifactoryServiceVersion until) {
        super(until, until);
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return source.getVersion().before(ArtifactoryServiceVersionProvider.v71050.get())
                && target.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v71050.get());
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Starting to convert remote federated members by trimming trailing slashes from the URL...");
        RepositoryConfigService repoConfigService = ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<FederatedRepoConfig> federatedRepoConfigs =
                repoConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED).stream()
                        .map(FederatedRepoConfig.class::cast).toList();
        federatedRepoConfigs
                .forEach(federatedRepoConfig -> federatedRepoConfig.getRepoTypeConfig().getFederatedMembers()
                        .forEach(member -> member.setUrl(PathUtils.trimTrailingSlashes(member.getUrlOrTemplate()))));

        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        securityService.doAsSystem(() -> repoConfigService.bulkUpsertRepoConfigs(federatedRepoConfigs, true));
        log.debug("Finished converting remote federated members by trimming trailing slashes from the URL.");
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // noop
    }
}
