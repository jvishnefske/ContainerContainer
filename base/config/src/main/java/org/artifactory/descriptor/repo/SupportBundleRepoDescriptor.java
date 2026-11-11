package org.artifactory.descriptor.repo;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.LocalConfig;

import java.util.List;

/**
 * @author Tamir Hadad
 */
public class SupportBundleRepoDescriptor extends LocalRepoDescriptor {

    public static final String SUPPORT_BUNDLE_REPO_NAME = "jfrog-support-bundle";

    public SupportBundleRepoDescriptor(String repoKey, RepoLayout simpleLayout) {
        setKey(repoKey);
        setType(RepoType.Support);
        setRepoLayout(simpleLayout);
    }

    public SupportBundleRepoDescriptor(LocalConfig repositoryConfig, List<RepoLayout> repoLayouts,
            List<PropertySet> propertySets, List<KeyPairDescriptor> keyPairs) {
        super(repositoryConfig, repoLayouts, propertySets, keyPairs);
    }

    public static SupportBundleRepoDescriptor fromRepositoryConfig(CentralConfigDescriptor centralConfigDescriptor,
            LocalConfig sourceRepoConfig) {
        return new SupportBundleRepoDescriptor(sourceRepoConfig, centralConfigDescriptor.getRepoLayouts(),
                centralConfigDescriptor.getPropertySets(),
                centralConfigDescriptor.getKeyPairs());
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public boolean isCache() {
        return false;
    }
}
