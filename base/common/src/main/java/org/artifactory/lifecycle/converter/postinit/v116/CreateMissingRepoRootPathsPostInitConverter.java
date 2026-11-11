package org.artifactory.lifecycle.converter.postinit.v116;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.InternalRepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterPreconditionException;
import org.artifactory.converter.postinit.PostInitConverter;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.artifactory.version.CompoundVersionDetails;

/**
 * Post-init converter that creates missing repository root folder nodes.
 * This ensures data consistency when repositories exist in repository_config but lack
 * corresponding root folder entries in the nodes table.
 * This converter runs when the {@link ConstantValues#repoRootAtomicCreation} feature is enabled.
 */
@Slf4j
public class CreateMissingRepoRootPathsPostInitConverter extends PostInitConverter {

    public CreateMissingRepoRootPathsPostInitConverter(ArtifactoryServiceVersion until) {
        super(until, until);
    }

    @Override
    public boolean isInterested(CompoundVersionDetails source, CompoundVersionDetails target) {
        return super.isInterested(source, target) && ConstantValues.repoRootAtomicCreation.getBoolean();
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
        log.info("Starting post-init conversion to create missing repository root folder nodes");
        InternalRepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(InternalRepositoryConfigService.class);
        int created = repositoryConfigService.createMissingRepoRootFolders();
        log.info("Post-init conversion completed. Created {} root folder nodes", created);
    }

    @Override
    public void assertConversionPrecondition(ArtifactoryHome home, CompoundVersionDetails fromVersion,
            CompoundVersionDetails toVersion) throws ConverterPreconditionException {
        // No preconditions required
    }
}
