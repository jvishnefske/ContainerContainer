package org.artifactory.storage.db.itest;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.artifactory.api.properties.PropertiesDiff;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.descriptor.property.Property;
import org.artifactory.descriptor.property.PropertySet;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Tamar Rosen
 */
public class DummyPropertiesService implements PropertiesService {
    @Override
    public boolean hasProperties(RepoPath repoPath) {
        return false;
    }

    @Nonnull
    @Override
    public Properties getProperties(RepoPath repoPath) {
        return null;
    }

    @Nonnull
    @Override
    public Properties getProperties(ItemInfo itemInfo) {
        return null;
    }

    @Nonnull
    @Override
    public Map<Long, Set<String>> getProperties(List<Long> nodeIdList, String propKey) {
        return null;
    }

    @Override
    public Map<RepoPath, Properties> getProperties(Set<RepoPath> repoPaths, String... mandatoryKeys) {
        return null;
    }

    @Override
    public Map<Long, Properties> getAllProperties(String repoKey, String propKey, List<String> propValues) {
        return null;
    }

    @Override
    public void addProperty(RepoPath repoPath, @Nullable PropertySet propertySet, Property property,
            boolean updateAccessLogger, String... values) {

    }

    @Override
    public void addProperty(RepoPath repoPath, @Nullable PropertySet propertySet, Property property, String... values) {

    }

    @Override
    public void addPropertyRecursively(RepoPath repoPath, @Nullable PropertySet propertySet, Property property,
            boolean updateAccessLogger, String... values) {

    }

    @Override
    public void addPropertyRecursively(RepoPath repoPath, @Nullable PropertySet propertySet, Property property,
            String... values) {

    }

    @Override
    public void addPropertyRecursivelyMultiple(RepoPath repoPath, @Nullable PropertySet propertySet,
            Map<Property, List<String>> propertyMapFromRequest,
            boolean updateAccessLogger) {

    }

    @Override
    public void addPropertySha256RecursivelyMultiple(RepoPath repoPath) {

    }

    @Override
    public void editProperty(RepoPath repoPath, @Nullable PropertySet propertySet, Property property,
            boolean updateAccessLogger, String... values) {

    }

    @Override
    public boolean setProperties(RepoPath repoPath, Properties properties, boolean isInternalProperties) {
        return false;
    }

    @Override
    public boolean addProperties(RepoPath repoPath, String propKey, Set<String> valuesToAdd,
            boolean isInternalProperties) {
        return false;
    }

    @Override
    public boolean removePropertyValues(RepoPath repoPath, String propKey, Set<String> valuesToRemove,
            boolean isInternalProperties) {
        return false;
    }

    @Override
    public PropertiesDiff calcPropertiesDiff(Properties updatedProperties, RepoPath repoPath) {
        return new PropertiesDiff(new PropertiesImpl(), new PropertiesImpl());
    }

    @Override
    public Properties updateProperties(RepoPath repoPath, Properties newProperties) {
        return null;
    }

    @Override
    public boolean deleteProperty(RepoPath repoPath, String property, boolean updateAccessLogger) {
        return false;
    }

    @Override
    public boolean deleteProperty(RepoPath repoPath, String property) {
        return false;
    }

    @Override
    public void deletePropertyRecursively(RepoPath repoPath, String property, boolean updateAccessLogger) {

    }

    @Override
    public void deletePropertyRecursively(RepoPath repoPath, String property) {

    }

    @Override
    public boolean removeProperties(RepoPath repoPath) {
        return false;
    }
}
