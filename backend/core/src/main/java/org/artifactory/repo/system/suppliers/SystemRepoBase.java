package org.artifactory.repo.system.suppliers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;

import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_REPO_LAYOUT;

/**
 * This is an abstract class which contains the template with which a system repo descriptor can be built
 *
 * @author Tomer Slutsky
 */
public abstract class SystemRepoBase {

    public void populateReferenceEntities(CentralConfigDescriptor descriptor, LocalRepoConfig repoConfig) {
    }

    public final LocalRepoConfig createSystemRepoConfig(CentralConfigDescriptor descriptor, String repoKey) {
        LocalRepoConfig repoConfig = buildRepoConfig(descriptor, repoKey);
        populateReferenceEntities(descriptor, repoConfig);
        return repoConfig;
    }

    public LocalRepoConfig buildRepoConfig(CentralConfigDescriptor descriptor, String repoKey) {
        LocalRepoConfig repoConfig = LocalRepoConfig.create(repoKey, Generic);
        addDefaultRepoLayout(descriptor, repoConfig);
        return repoConfig;
    }

    protected void addDefaultRepoLayout(CentralConfigDescriptor descriptor, RepoConfig repoConfig) {
        if (descriptor != null && descriptor.getRepoLayout(DEFAULT_REPO_LAYOUT) != null) {
            repoConfig.getBaseConfig().setRepoLayoutRef(DEFAULT_REPO_LAYOUT);
        }
    }
}
