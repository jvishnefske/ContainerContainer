package org.artifactory.repo.system.suppliers;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.model.LocalRepoConfig;

import java.util.ArrayList;
import java.util.List;

import static org.artifactory.descriptor.property.PropertySet.ARTIFACTORY_RESERVED_PROP_SET;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_BLACKED_OUT;
import static org.artifactory.repo.config.RepoConfigDefaultValues.DEFAULT_INCLUDES_PATTERN;

/**
 * @author Tomer Slutsky
 */
public class EdgeRepo extends SystemRepoBase {

    @Override
    public LocalRepoConfig buildRepoConfig(CentralConfigDescriptor descriptor, String repoKey) {
        LocalRepoConfig repoConfig = super.buildRepoConfig(descriptor, repoKey);
        repoConfig.getBaseConfig().setDescription("A generic repository for uploads");
        repoConfig.getBaseConfig().setIncludesPattern(DEFAULT_INCLUDES_PATTERN);
        repoConfig.getRepoTypeConfig().setBlackedOut(DEFAULT_BLACKED_OUT);
        return repoConfig;
    }

    @Override
    public void populateReferenceEntities(CentralConfigDescriptor descriptor, LocalRepoConfig repoConfig) {
        repoConfig.getRepoTypeConfig().setPropertySetRefs(getPropertySetRefs(descriptor));
    }

    private List<String> getPropertySetRefs(CentralConfigDescriptor configDescriptor) {
        String defaultPropertySetRef = configDescriptor.getPropertySets().stream()
                .map(PropertySet::getName)
                .filter(propertySetName -> propertySetName.equals(ARTIFACTORY_RESERVED_PROP_SET))
                .findFirst()
                .orElse(null);
        if (defaultPropertySetRef == null) {
            return null;
        }
        List<String> propertySetRefs = new ArrayList<>();
        propertySetRefs.add(defaultPropertySetRef);
        return propertySetRefs;
    }
}
