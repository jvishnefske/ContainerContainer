package org.artifactory.repo.system.suppliers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.ReleaseBundleRepoConfig;

/**
 * @author Tomer Slutsky
 */
public class ReleaseBundleRepo extends SystemRepoBase {

    @Override
    public LocalRepoConfig buildRepoConfig(CentralConfigDescriptor descriptor, String repoKey) {
        return ReleaseBundleRepoConfig.create(repoKey);
    }
}
