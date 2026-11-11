package com.jfrog.build.gateway;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.PropertiesAddon;
import org.artifactory.api.search.ItemSearchResults;
import org.artifactory.api.search.SearchService;
import org.artifactory.api.search.property.PropertySearchControls;
import org.artifactory.api.search.property.PropertySearchResult;
import org.artifactory.descriptor.property.Property;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class BuildPropertyGateway {

    private final AddonsManager addonsManager;
    private final SearchService searchService;

    public void addProperties(
            @NonNull String rawRepoPath,
            @NonNull Map<String, Collection<String>> properties) {
        PropertiesAddon propertiesAddon = addonsManager.addonByType(PropertiesAddon.class);
        RepoPath repoPath = RepoPathFactory.create(rawRepoPath);
        for (String key : properties.keySet()) {
            Collection<String> valuesForKey = properties.get(key);
            String[] values = new String[valuesForKey.size()];
            propertiesAddon.addProperty(repoPath, null, new Property(key), valuesForKey.toArray(values));
        }
    }

    public void renameBuildNameProperty(
            @NonNull String propertyKey,
            @NonNull String valueFrom,
            @NonNull String valueTo) {
        PropertiesAddon propertiesAddon = addonsManager.addonByType(PropertiesAddon.class);
        PropertySearchControls controls = buildPropertySearchControls(propertyKey, valueFrom);
        ItemSearchResults<PropertySearchResult> results = searchService.searchProperty(controls);
        List<PropertySearchResult> searchResults = results.getResults();
        if (searchResults.isEmpty()) {
            log.warn("Could not find artifacts with build name property value: {}", valueFrom);
            return;
        }
        Property property = new Property();
        property.setName(propertyKey);
        for (PropertySearchResult propertySearchResult : searchResults) {
            replacePropertyValue(propertyKey, valueFrom, valueTo, property, propertySearchResult, propertiesAddon);
        }
        log.info("Renamed artifact build name properties from {} to {}", valueFrom, valueTo);
    }

    private void replacePropertyValue(
            @NonNull String propertyKey,
            @NonNull String valueFrom,
            @NonNull String valueTo,
            @NonNull Property property,
            @NonNull PropertySearchResult propertySearchResult,
            @NonNull PropertiesAddon propertiesAddon) {
        RepoPath path = propertySearchResult.getItemInfo().getRepoPath();
        try {
            propertiesAddon.deleteProperty(path, propertyKey);
            propertiesAddon.addProperty(path, null, property, valueTo);
            log.debug("Renamed build name properties from {} to {} for artifact {}", valueFrom, valueTo, path.getId());
        } catch (Exception e) {
            log.error("Failed to rename build name properties of artifact {} from {} to {}",
                    path.getId(), valueFrom, valueTo);
        }
    }

    @NonNull
    private PropertySearchControls buildPropertySearchControls(
            @NonNull String propertyKey,
            @NonNull String valueFrom) {
        PropertySearchControls controls = new PropertySearchControls();
        controls.setLimitSearchResults(false);
        controls.put(propertyKey, valueFrom, false);
        return controls;
    }
}
