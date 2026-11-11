package org.artifactory.repo.system.suppliers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.LocalRepoConfig;

import static org.artifactory.descriptor.repo.RepoType.PipeInfo;

/**
 * @author Tomer Slutsky
 */
public class PipeinfoRepo extends SystemRepoBase {

    @Override
    public LocalRepoConfig buildRepoConfig(CentralConfigDescriptor configDescriptor, String repoKey) {
        return LocalRepoConfig.create(repoKey, PipeInfo);
    }
}
