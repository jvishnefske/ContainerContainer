package org.artifactory.repo.system.suppliers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;

import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;

/**
 * @author Tomer Slutsky
 */
public class BuildInfoRepo extends SystemRepoBase {

    @Override
    public LocalRepoConfig buildRepoConfig(CentralConfigDescriptor descriptor, String repoKey) {
        LocalRepoConfig buildInfoRepoConfig = LocalRepoConfig.create(repoKey, RepoType.BuildInfo);
        addDefaultRepoLayout(descriptor, buildInfoRepoConfig);
        return buildInfoRepoConfig;
    }

    /**
     * This is used since there is a use case where the repoKey is made dynamically and not provided
     */
    public LocalRepoConfig buildRepoConfig(CentralConfigDescriptor descriptor) {
        return buildRepoConfig(descriptor, BUILDINFO_REPO_KEY);
    }
}
