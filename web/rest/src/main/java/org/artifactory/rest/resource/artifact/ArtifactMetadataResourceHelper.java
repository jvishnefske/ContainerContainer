/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.artifactory.rest.resource.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.PropertiesAddon;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.property.Property;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.StatsInfo;
import org.artifactory.md.Properties;
import org.artifactory.model.fs.StatsImpl;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.storage.fs.service.StatsService;
import org.jfrog.common.StringList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jfrog.common.JsonUtils.jsonMerge;

/**
 * @author dudim
 */
@Slf4j
@Component
public class ArtifactMetadataResourceHelper {

    private final AddonsManager addonsManager;
    private final StatsService statsService;
    private final AuthorizationService authorizationService;
    private final SecurityService securityService;
    private final RepositoryService repositoryService;

    @Autowired
    public ArtifactMetadataResourceHelper(AddonsManager addonsManager,
            @Qualifier("statsServiceImpl") StatsService statsService, AuthorizationService authorizationService,
            SecurityService securityService, RepositoryService repositoryService) {
        this.addonsManager = addonsManager;
        this.statsService = statsService;
        this.authorizationService = authorizationService;
        this.securityService = securityService;
        this.repositoryService = repositoryService;
    }

    void validateUserHasAnnotateAuthorization(RepoPath repoPath) {
        if (!authorizationService.canAnnotate(repoPath)) {
            throw new AuthorizationRestException("Request for '" + repoPath + "' is forbidden for user: '" +
                    securityService.currentUsername() + "', You must have annotate permission on this path");
        }
    }

    boolean handleStatistics(JsonNode requestedStats, RepoPath repoPath) {
        StatsInfo packageStats = statsService.getStats(repoPath);
        if (packageStats == null) {
            packageStats = getDefaultStatForPackage(repoPath);
        }
        StatsInfo modifiedStats = jsonMerge(packageStats, requestedStats.toString(), StatsImpl.class, null);
        return statsService.setStats(repoPath, modifiedStats);
    }

    private StatsImpl getDefaultStatForPackage(RepoPath repoPath) {
        StatsImpl statsImpl = new StatsImpl();
        statsImpl.setRepoPath(repoPath.getPath());
        return statsImpl;
    }

    boolean handlePatchProperties(@Nonnull JsonNode requestedProps, @Nonnull RepoPath repoPath,
            String recursiveProperties) {
        ItemInfo itemInfo = repositoryService.getItemInfo(repoPath);
        PropertiesImpl properties = (PropertiesImpl) propertiesAddon().getProperties(itemInfo);

        Map<String, List<String>> propertiesMap = convertToMap(properties.entries());
        Map<String, JsonNode> requestedPropsMap = convertJsonNodeToMap(requestedProps);

        List<String> toBeDeleted = getToBeDeleted(requestedPropsMap);
        Map<Property, List<String>> toBeAdded = getToBeAdded(propertiesMap, requestedPropsMap);
        Map<Property, List<String>> toBeModified = getToBeModified(propertiesMap, requestedPropsMap);

        Response response = propertiesAddon().patchProperties(repoPath, recursiveProperties, toBeDeleted, toBeAdded,
                toBeModified);
        return response.getStatus() == Response.Status.NO_CONTENT.getStatusCode();
    }

    @Nonnull
    private Map<String, JsonNode> convertJsonNodeToMap(@Nonnull JsonNode propsNode) {
        Map<String, JsonNode> resultMap = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = propsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            resultMap.put(entry.getKey(), entry.getValue());
        }
        return resultMap;
    }

    @Nonnull
    private Map<String, List<String>> convertToMap(@Nonnull Set<Map.Entry<String, String>> properties) {
        Map<String, List<String>> propertyMap = new HashMap<>();
        properties.forEach(entry -> {
            propertyMap.putIfAbsent(entry.getKey(), Lists.newArrayList());
            propertyMap.get(entry.getKey()).add(entry.getValue());
        });
        return propertyMap;
    }

    @Nonnull
    private List<String> getToBeDeleted(@Nonnull Map<String, JsonNode> requestedPropsMap) {
        return requestedPropsMap.keySet().stream()
                .filter(key -> requestedPropsMap.get(key) instanceof NullNode)
                .toList();
    }

    @Nonnull
    private Map<Property, List<String>> getToBeAdded(@Nonnull Map<String, List<String>> propertiesMap,
            @Nonnull Map<String, JsonNode> requestedPropsMap) {
        return requestedPropsMap.keySet().stream()
                .filter(key -> !propertiesMap.containsKey(key) && !(requestedPropsMap.get(key) instanceof NullNode))
                .collect(Collectors.toMap(
                        Property::new, key -> buildValue(requestedPropsMap.get(key))));
    }

    @Nonnull
    private Map<Property, List<String>> getToBeModified(@Nonnull Map<String, List<String>> propertiesMap,
            @Nonnull Map<String, JsonNode> requestedPropsMap) {
        return requestedPropsMap.keySet().stream()
                .filter(key -> propertiesMap.containsKey(key) &&
                        (!(requestedPropsMap.get(key) instanceof NullNode)))
                .collect(Collectors.toMap(Property::new, key -> buildValue(requestedPropsMap.get(key))));
    }

    @Nonnull
    private List<String> buildValue(@Nonnull Object value) {
        if (value instanceof TextNode textNode) {
            return List.of(textNode.asText());
        }
        if (value instanceof ArrayNode arrayNode) {
            return convertArrayNodeToList(arrayNode);
        }
        throw new IllegalArgumentException("Failed to parse json object while performing patch properties request.");
    }

    @Nonnull
    public List<String> convertArrayNodeToList(@Nonnull ArrayNode arrayNode) {
        List<String> resultList = new ArrayList<>();
        for (JsonNode element : arrayNode) {
            if (element.isTextual()) {
                resultList.add(element.asText());
            }
        }
        return resultList;
    }

    StringList convertPropertiesToStringList(Properties properties) {
        String packagePropertiesKeys = Arrays.toString(properties.keys().toArray());
        packagePropertiesKeys = packagePropertiesKeys.substring(1, packagePropertiesKeys.length() - 1);
        return new StringList(packagePropertiesKeys);
    }

    PropertiesAddon propertiesAddon() {
        return addonsManager.addonByType(PropertiesAddon.class);
    }
}
