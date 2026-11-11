package org.artifactory.lifecycle.converter.postinit.v107;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.FederatedMemberUtils;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;

import java.util.List;

import static org.artifactory.util.FederatedMemberUtils.convertToNewLocalMember;

/**
 * @author Tamar Rosen
 */
@Slf4j
public class FederatedLocalMemberPostInitConverter extends PostInitConverter {

    public FederatedLocalMemberPostInitConverter(ArtifactoryServiceVersion from, ArtifactoryServiceVersion until) {
        super(from, until);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Starting to convert local members from a literal url to a template url... ");
        RepositoryConfigService repoConfigService = ContextHelper.get().beanForType(RepositoryConfigService.class);
        List<FederatedRepoConfig> federatedRepoConfigs =
                repoConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED).stream()
                        .map(FederatedRepoConfig.class::cast).toList();
        String baseUrl = ContextHelper.get().beanForType(CentralConfigService.class).getDescriptor()
                .getFederatedRepoUrlBaseOrDefault();
        federatedRepoConfigs
                .forEach(federatedRepoConfig -> federatedRepoConfig.getFederatedMembers().stream()
                        .filter(federatedMember -> FederatedMemberUtils.isOldFormatLocal(federatedMember,
                                federatedRepoConfig.getKey(),
                                baseUrl))
                        .forEach(federatedMember -> convertToNewLocalMember(federatedMember, baseUrl)));

        SecurityService securityService = ContextHelper.get().beanForType(SecurityService.class);
        securityService.doAsSystem(() -> repoConfigService.bulkUpsertRepoConfigs(federatedRepoConfigs, true));
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // noop
    }


    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        // Original range: upgrade to 7.52.0-m001
        if (source.getVersion().before(ArtifactoryServiceVersionProvider.v7520m001.get())
                && target.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v7520m001.get())) {
            return true;
        }
        // Affected versions for RTFS-2873
        return source.getVersion().afterOrEqual(ArtifactoryServiceVersionProvider.v71371.get())
                && source.getVersion().beforeOrEqual(ArtifactoryServiceVersionProvider.v71480.get());
    }
}
