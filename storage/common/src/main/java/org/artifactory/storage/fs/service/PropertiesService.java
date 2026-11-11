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

package org.artifactory.storage.fs.service;

import com.google.common.collect.SetMultimap;
import jakarta.annotation.Nonnull;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.GCCandidate;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.jfrog.rtfs.dto.properties.NodePropertyData;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A business service to interact with the node properties table.
 *
 * @author Yossi Shaul
 */
public interface PropertiesService {

    //TODO: [by YS] use properties info

    /**
     * @param itemInfo The item to fetch properties for
     * @return The properties of the specific item.
     */
    @Nonnull
    Properties getProperties(ItemInfo itemInfo);

    /**
     * @param nodesIds - ids of the nodes for witch to get the properties
     * @return Map of the properties for every node id.
     */
    @Nonnull
    Map<Long, Properties> getProperties(@Nonnull List<Long> nodesIds);

    /**
     * Get a slim version of node properties by nodeIds
     *
     * @param paths - The list of nodeIds
     * @return A map of nodeId to a collection of the ids slim node properties
     */
    Map<NodePath, SetMultimap<String, NodePropertyData>> getStreamedNodePropertyData(List<NodePath> paths,
            String repoKey);

    /**
     * @param repoPath The repo path to fetch properties for
     * @return The properties of the specific repo path.
     */
    @Nonnull
    Properties getProperties(RepoPath repoPath);

    /**
     * Check if a folder contains specified files and fetch their properties in a single query.
     * This optimizes file checking by combining the existence check with property fetching.
     *
     * @param repoPath  The path to check
     * @param fileNames The file names to look for
     * @return Map of file name to its Properties, empty if no files found
     */
    @Nonnull
    Map<String, Properties> getPropertiesForFiles(@Nonnull RepoPath repoPath, @Nonnull List<String> fileNames);

    /**
     * @return a mapping of nodeId -> {@link Properties} that are retrieved for all nodes under {@param repoKey}
     * that have property {@param propKey} with any values from {@param propValues}
     */
    Map<Long, Properties> getAllProperties(String repoKey, String propKey, List<String> propValues);

    /**
     * Get all properties that their value size is longer than the requested parameter
     *
     * @param size size to use when returning the long properties
     * @return all properties that their values is longer than the requested size
     */
    Map<String, List<String>> getAllPropsLongerThan(int size);

    /**
     * Trim property values that are longer than the max allowed length. This method affect PostgreSQL only.
     */
    int trimPropertyValuesToAllowedLength();

    boolean hasProperties(RepoPath repoPath);

    void replaceProperties(@Nonnull Map<Long, Properties> nodePropertiesToReplace);


    /**
     * Sets the given properties on the node id. Existing properties are overridden. An empty object will cause a
     * removal of all the properties on this node.
     *
     * @param repoPath   RepoPath of the node
     * @param properties The properties to set
     * @return true if the database has changed as part of this method call
     */
    boolean setProperties(RepoPath repoPath, Properties properties);

    int deleteProperties(long nodeId);

    List<GCCandidate> getGCCandidatesFromTrash(String validFrom);

    Map<Long, Set<String>> getProperties(List<Long> nodeIdList, String propKey);

    void createProperties(@Nonnull Map<Long, Properties> propertiesMap);
}
