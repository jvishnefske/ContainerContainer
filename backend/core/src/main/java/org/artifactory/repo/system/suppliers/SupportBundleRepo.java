package org.artifactory.repo.system.suppliers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;

/**
 * @author Tomer Slutsky
 */
public class SupportBundleRepo extends SystemRepoBase {

    @Override
    public LocalRepoConfig buildRepoConfig(CentralConfigDescriptor descriptor, String repoKey) {
        LocalRepoConfig repoConfig = LocalRepoConfig.create(repoKey, RepoType.Support);
        addDefaultRepoLayout(descriptor, repoConfig);
        return repoConfig;
    }
}
