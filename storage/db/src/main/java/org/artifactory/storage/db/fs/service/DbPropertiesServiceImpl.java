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

package org.artifactory.storage.db.fs.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.xml.bind.DatatypeConverter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.properties.PropertiesDiff;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.properties.PropertiesImpl.PropertyChange;
import org.artifactory.properties.PropertiesImpl.PropertyChangeResult;
import org.artifactory.properties.PropertyWithId;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.storage.EmojiNotAllowedInDbException;
import org.artifactory.storage.GCCandidate;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.dao.PropertiesDao;
import org.artifactory.storage.db.fs.entity.NodeProperty;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.service.ItemMetaInfo;
import org.artifactory.storage.fs.service.NodeMetaInfoService;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.util.InternalStringUtils;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.jfrog.rtfs.dto.properties.NodePropertyData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Yossi Shaul
 */
@Slf4j
@Service
public class DbPropertiesServiceImpl implements PropertiesService {

    @Autowired
    private DbService dbService;

    @Autowired
    private PropertiesDao propertiesDao;

    @Autowired
    private NodesDao nodesDao;

    @Autowired
    private FileService fileService;

    @Autowired
    private NodeMetaInfoService nodeMetaInfoService;

    private boolean replacePropertiesOnSet = false;
    private boolean logSetPropertiesPerformance = false;

    @PostConstruct
    private void init() {
        replacePropertiesOnSet = ConstantValues.nodePropertiesReplaceAll.getBoolean();
        logSetPropertiesPerformance = ConstantValues.nodePropertiesLogPerformance.getBoolean();
    }

    @Nonnull
    @Override
    public Properties getProperties(ItemInfo itemInfo) {
        long id = itemInfo.getId();
        if (id < 0) {
            return getProperties(itemInfo.getRepoPath());
        } else {
            return getPropertiesById(id);
        }
    }

    @Nonnull
    @Override
    public Map<Long, Properties> getProperties(@Nonnull List<Long> nodeIds) {
        return getPropertiesByIds(nodeIds);
    }

    @Override
    @Nonnull
    public Properties getProperties(RepoPath repoPath) {
        long nodeId = fileService.getNodeId(repoPath);
        return getPropertiesById(nodeId);
    }

    @Nonnull
    @Override
    public Map<String, Properties> getPropertiesForFiles(@Nonnull RepoPath repoPath, @Nonnull List<String> fileNames) {
        try {
            return nodesDao.getPropertiesForFiles(repoPath, fileNames).entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                        PropertiesImpl properties = new PropertiesImpl();
                        entry.getValue().forEach(
                                nodeProperty -> properties.put(nodeProperty.getPropKey(), nodeProperty.getPropValue())
                        );
                        return properties;
                    }));
        } catch (Exception e) {
            throw new StorageException("Failed to get file properties for " + repoPath, e);
        }
    }

    @Nonnull
    private Properties getPropertiesById(long nodeId) {
        if (nodeId > 0) {
            return loadProperties(nodeId);
        } else {
            return new PropertiesImpl();
        }
    }


    @NonNull
    private Map<Long, Properties> getPropertiesByIds(@NonNull List<Long> nodeIds) {
        return loadProperties(nodeIds);
    }

    @Override
    public Map<Long, Properties> getAllProperties(String repoKey, String propKey, List<String> propValues) {
        Map<Long, Properties> nodesProps = Maps.newHashMap();
        Multimap<Long, NodeProperty> queryResults;
        try {
            queryResults = propertiesDao.getNodesProperties(repoKey, propKey, propValues);
        } catch (SQLException e) {
            throw new StorageException("Failed to load properties", e);
        }
        for (Map.Entry<Long, Collection<NodeProperty>> entry : queryResults.asMap().entrySet()) {
            Properties props = new PropertiesImpl();
            entry.getValue().forEach(singleProp -> props.put(singleProp.getPropKey(), singleProp.getPropValue()));
            nodesProps.put(entry.getKey(), props);
        }
        return nodesProps;
    }

    public Map<String, List<String>> getAllPropsLongerThan(int size) {
        Map<String, List<String>> nodesProps = Maps.newHashMap();
        List<NodeProperty> queryResults;
        try {
            queryResults = propertiesDao.getPostgresPropValuesLongerThanAllowed();
        } catch (SQLException e) {
            throw new StorageException("Failed to load properties", e);
        }
        for (NodeProperty property : queryResults) {
            String propKey = property.getPropKey();
            String propValue = property.getPropValue();
            List<String> existingValues = nodesProps.get(propKey);
            if (existingValues == null) {
                ArrayList<String> propValues = new ArrayList<>();
                propValues.add(propValue);
                nodesProps.put(propKey, propValues);
            } else {
                existingValues.add(propValue);
            }
        }
        return nodesProps;
    }

    @Override
    public int trimPropertyValuesToAllowedLength() {
        int affectedRows;
        try {
            affectedRows = propertiesDao.trimPostgresPropValuesToMaxAllowedLength();
        } catch (SQLException e) {
            throw new StorageException("Failed trimming property values.", e);
        }
        log.info("{} properties were trimmed", affectedRows);
        return affectedRows;
    }

    @Override
    public Map<NodePath, SetMultimap<String, NodePropertyData>> getStreamedNodePropertyData(List<NodePath> paths,
            String repoKey) {
        try {
            return propertiesDao.getStreamedNodePropertyData(paths, repoKey);
        } catch (SQLException e) {
            throw new StorageException("Failed to stream properties for " + paths, e);
        }
    }

    @Nonnull
    private Properties loadProperties(long nodeId) {
        try {
            List<NodeProperty> nodeProperties = propertiesDao.getNodeProperties(nodeId);
            return new PropertiesImpl(nodeId, nodeProperties);
        } catch (SQLException e) {
            throw new StorageException("Failed to load properties for " + nodeId, e);
        }
    }

    @NonNull
    private Map<Long, Properties> loadProperties(@NonNull List<Long> nodeIds) {
        try {
            Map<Long, Properties> result = new HashMap<>();
            Map<Long, Collection<NodeProperty>> nodePropertiesMap = propertiesDao.getNodesProperties(nodeIds);
            nodeIds.forEach(id -> {
                Collection<NodeProperty> nodeProps = nodePropertiesMap.get(id);
                if (nodeProps == null) {
                    result.put(id, new PropertiesImpl(id, new ArrayList<>()));
                } else {
                    result.put(id, new PropertiesImpl(id, new ArrayList<>(nodeProps)));
                }
            });
            return result;
        } catch (SQLException e) {
            throw new StorageException("Failed to load properties for " + nodeIds, e);
        }
    }

    @Override
    public boolean hasProperties(RepoPath repoPath) {
        long nodeId = fileService.getNodeId(repoPath);
        try {
            return nodeId > 0 && propertiesDao.hasNodeProperties(nodeId);
        } catch (SQLException e) {
            throw new StorageException("Failed to check properties for " + repoPath, e);
        }
    }

    /**
     * Sets properties
     *
     * @param nodeId     Id of the node to set properties on
     * @param properties The properties to set
     */
    private boolean setProperties(long nodeId, Properties properties, RepoPath repoPath) {
        try {
            if (properties == null) {
                log.warn("Failed to set properties: properties cannot be null");
                return false;
            }
            boolean modified;
            PropertiesImpl propsImpl = (PropertiesImpl) properties;
            long start = System.currentTimeMillis();
            if (shouldUpdateProps(nodeId, propsImpl)) {
                modified = updateProperties(nodeId, propsImpl, repoPath.getRepoKey());
            } else {
                modified = replaceProperties(nodeId, propsImpl, repoPath);
            }
            if (logSetPropertiesPerformance) {
                long elapsed = System.currentTimeMillis() - start;
                log.info("Set properties of node {} with {} properties took {} ms", nodeId, properties.size(), elapsed);
            }
            return modified;
        } catch (SQLException e) {
            if (InternalStringUtils.containsEmoji(e.getMessage())) {
                throw new EmojiNotAllowedInDbException("Emojis are not allowed for the current db. " +
                        "Please change db collation, or address the properties: " + properties.asMap(), e);
            }
            throw new StorageException("Failed to set properties on node: " + nodeId, e);
        }
    }

    private boolean updateProperties(long nodeId, PropertiesImpl properties, String repoKey) throws SQLException {
        log.trace("Updating properties of node {} using tracked changes", nodeId);
        Collection<PropertyChange> propertyChanges = properties.getPropertyChanges();
        log.debug("Found {} tracked changes for node {}", propertyChanges.size(), nodeId);
        List<PropertyChangeResult> changeResults = Lists.newArrayList();
        for (PropertyChange change : propertyChanges) {
            PropertyWithId changeProperty = change.getProperty();
            NodeProperty resultProperty = null;
            boolean success;
            switch (change.getChangeType()) {
                case CREATE:
                    resultProperty = new NodeProperty(dbService.nextId(), nodeId,
                            changeProperty.getPropKey(), changeProperty.getPropValue());
                    success = propertiesDao.create(resultProperty) > 0;
                    break;
                case UPDATE:
                    resultProperty = new NodeProperty(changeProperty.getPropId(), nodeId,
                            changeProperty.getPropKey(), changeProperty.getPropValue());
                    success = propertiesDao.updateValue(resultProperty) > 0;
                    break;
                case DELETE:
                    NodeProperty propToDelete = new NodeProperty(changeProperty.getPropId(), nodeId,
                            changeProperty.getPropKey(), changeProperty.getPropValue());
                    success = propertiesDao.delete(propToDelete) > 0;
                    break;
                default:
                    throw new IllegalStateException("Unexpected change type: " + change.getChangeType());
            }
            if (log.isDebugEnabled()) {
                long propId = resultProperty == null ? changeProperty.getPropId() : resultProperty.getPropId();
                log.debug("Property change result of {} node id {} prop id {} prop key [{}]: success={}",
                        change.getChangeType(), nodeId, propId, changeProperty.getPropKey(), success);
            }
            changeResults.add(new PropertyChangeResult(change, success, resultProperty));
        }
        properties.resetChangeTracking(changeResults);

        updateNodeMetaInfoIfNeeded(nodeId, properties, repoKey, getChangedPropsKeySet(changeResults));

        return !changeResults.isEmpty();
    }

    @Override
    public void replaceProperties(@NonNull Map<Long, Properties> nodePropertiesToReplace) {
        try {
            log.trace("Replacing properties of multiple nodes");

            List<NodeProperty> nodeProperties = new ArrayList<>();
            nodePropertiesToReplace.forEach((nodeId, properties) -> {
                // first delete existing properties is exist
                deleteProperties(nodeId);
                // create record for each node property. one record for each key/value combination
                for (Map.Entry<String, String> propEntry : properties.entries()) {
                    NodeProperty property = new NodeProperty(
                            dbService.nextId(), nodeId, propEntry.getKey(), propEntry.getValue()
                    );
                    nodeProperties.add(property);
                }
            });

            propertiesDao.createBulk(nodeProperties);
        } catch (SQLException e) {
            throw new StorageException("Failed to set replace properties for multiple nodes ", e);
        }
    }


    private boolean replaceProperties(long nodeId, Properties properties, RepoPath repoPath) throws SQLException {
        log.trace("Replacing properties of node {}", nodeId);
        // first delete existing properties is exist
        int deleteCount = deleteProperties(nodeId);

        // create record for each node property. one record for each key/value combination
        List<NodeProperty> nodeProperties = new ArrayList<>();
        for (Map.Entry<String, String> propEntry : properties.entries()) {
            NodeProperty property = new NodeProperty(dbService.nextId(), nodeId, propEntry.getKey(),
                    propEntry.getValue());
            nodeProperties.add(property);
        }
        propertiesDao.createBulk(nodeProperties);
        // modified if we deleted something or added something

        updateNodeMetaInfoIfNeeded(nodeId, properties, repoPath.getRepoKey(),
                getChangedPropsKeySet(properties, repoPath));

        return deleteCount > 0 || properties.size() > 0;
    }

    /**
     * Sets properties
     *
     * @param repoPath   RepoPath of the node to set properties on
     * @param properties The properties to set
     */
    @Override
    public boolean setProperties(RepoPath repoPath, Properties properties) {
        long nodeId = fileService.getNodeId(repoPath);
        if (nodeId > 0) {
            return setProperties(nodeId, properties, repoPath);
        }
        return false;
    }

    @Override
    public int deleteProperties(long nodeId) {
        try {
            return propertiesDao.deleteNodeProperties(nodeId);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete properties for node: " + nodeId, e);
        }
    }

    @Override
    public List<GCCandidate> getGCCandidatesFromTrash(String validFrom) {
        try {
            return propertiesDao.getGCCandidatesFromTrash(validFrom);
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve trashcan delete candidates: ", e);
        }
    }

    @Override
    public Map<Long, Set<String>> getProperties(List<Long> nodeIdList, String propKey) {
        try {
            return propertiesDao.getNodesProperties(nodeIdList, propKey);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to retrieve property:" + propKey + " from list of nodeIds with size: " + nodeIdList.size(),
                    e);
        }
    }

    @Override
    public void createProperties(@Nonnull Map<Long, Properties> propertiesMap) {
        try {
            List<NodeProperty> propertiesList = new ArrayList<>();
            propertiesMap.forEach((nodeId, properties) -> {
                for (Map.Entry<String, String> propEntry : properties.entries()) {
                    NodeProperty property = new NodeProperty(
                            dbService.nextId(), nodeId, propEntry.getKey(), propEntry.getValue()
                    );
                    propertiesList.add(property);
                }
            });
            propertiesDao.createBulk(propertiesList);
        } catch (SQLException e) {
            throw new StorageException("Error while creating properties", e);
        }
    }

    private String calcMd5ForProperties(Properties properties) {
        MessageDigest md;

        try {
            final List<String> list = properties.entries().stream().map(a -> a.getKey() + a.getValue()).
                    collect(Collectors.toList());
            java.util.Collections.sort(list);
            StringBuilder builder = new StringBuilder();
            list.forEach(builder::append);
            md = MessageDigest.getInstance("MD5");
            md.update(builder.toString().getBytes());
            byte[] digest = md.digest();
            return DatatypeConverter
                    .printHexBinary(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to calculate MD5 for properties", e);
        }
    }

    private void createOrUpdateNodeMetaInfo(long nodeId, Properties properties) {
        if (properties.isEmpty()) {
            nodeMetaInfoService.deleteMetaInfo(nodeId);
        } else {
            String md5 = calcMd5ForProperties(properties);
            nodeMetaInfoService.createOrUpdateNodeMetaInfo(nodeId,
                    new ItemMetaInfo(System.currentTimeMillis(), getSecurityService().currentUsername(), md5));
        }
    }

    private boolean shouldUpdateProps(long nodeId, PropertiesImpl propsImpl) {
        return !replacePropertiesOnSet && propsImpl.isChangeTrackingEnabled() &&
                propsImpl.getNodeId() == nodeId;
    }

    private SecurityService getSecurityService() {
        return ContextHelper.get().beanForType(SecurityService.class);
    }

    private CacheExpiry getCacheExpiry() {
        return ContextHelper.get().beanForType(CacheExpiry.class);
    }

    private void updateNodeMetaInfoIfNeeded(long nodeId, Properties properties, String repoKey,
            Set<String> changedPropKeys) {
        boolean shouldUpdateNodeMetaInfo = changedPropKeys.stream()
                .anyMatch(propKey -> !getCacheExpiry().shouldExcludePropertyFromMirroring(repoKey, propKey));
        if (shouldUpdateNodeMetaInfo) {
            createOrUpdateNodeMetaInfo(nodeId, properties);
        }
    }

    @Nonnull
    private static Set<String> getChangedPropsKeySet(Properties properties, RepoPath repoPath) {
        PropertiesDiff propertiesDiff =
                ContextHelper.get().beanForType(org.artifactory.api.properties.PropertiesService.class)
                        .calcPropertiesDiff(properties, repoPath);
        Set<String> propKeys = new HashSet<>(propertiesDiff.getDeleted().keySet());
        propKeys.addAll(properties.keySet());
        return propKeys;
    }

    @Nonnull
    private static Set<String> getChangedPropsKeySet(List<PropertyChangeResult> changeResults) {
        return changeResults.stream()
                .map(change -> change.getChange().getProperty().getPropKey())
                .collect(Collectors.toSet());
    }
}
