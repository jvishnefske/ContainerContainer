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

package org.artifactory.storage.fs.tree;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RootNodesFilterResult;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.tree.file.NodeItemFilterHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A folder node is a virtual file system node with {@link org.artifactory.fs.FolderInfo} as its data.
 * The node is detached from the database and may not exist anymore.
 *
 * @author Yossi Shaul
 */
@Slf4j
public class FolderNode extends ItemNode {
    private final TreeBrowsingCriteria criteria;
    @Getter
    private List<ItemNode> cachedChildrenNodes;
    @Getter
    private boolean usedGetChildren = true;

    public FolderNode(FolderInfo itemInfo, TreeBrowsingCriteria criteria) {
        super(itemInfo);
        this.criteria = criteria;
    }

    @Override
    public List<ItemNode> getChildren() {
        return getChildrenItemNode(false, null);
    }

    @Override
    public List<ItemNode> getChildren(boolean updateRootNodesFilterFlag, RootNodesFilterResult rootNodesFilterResult) {
        return getChildrenItemNode(updateRootNodesFilterFlag, rootNodesFilterResult);
    }

    @Override
    public Stream<ItemInfo> getChildrenStream(boolean updateRootNodesFilterFlag,
            RootNodesFilterResult rootNodesFilterResult) {
        AtomicBoolean localAcceptanceFlag = new AtomicBoolean(true);
        AtomicLong acceptedNodesCount = new AtomicLong(0L);
        Stream<ItemInfo> children = getFileService().getChildrenStream(itemInfo.getRepoPath())
                .filter(child -> {
                    NodeItemFilterHolder nodeItemFilterHolder = accepts(child);
                    if (nodeItemFilterHolder.isAccepted()) {
                        acceptedNodesCount.getAndIncrement();
                        return true;
                    } else {
                        localAcceptanceFlag.set(updateAcceptedLocalFlag(localAcceptanceFlag.get(), accepts(child)));
                        return false;
                    }
                });
        updateBrowsableItemAcceptedHolderCanReadFlag(updateRootNodesFilterFlag, rootNodesFilterResult,
                acceptedNodesCount.get(), localAcceptanceFlag.get());
        return children;
    }

    private void updateBrowsableItemAcceptedHolderCanReadFlag(boolean updateRootNodesFilterFlag,
            RootNodesFilterResult rootNodesFilterResult,
            long acceptedNodesCount, boolean localAcceptanceFlag) {
        if (acceptedNodesCount == 0 && updateRootNodesFilterFlag && !localAcceptanceFlag) {
            rootNodesFilterResult.setAllItemNodesCanRead(false);
        }
    }

    /**
     * if nodes accepted by filters (include/exclude , canRead and etc) , node is added to list
     *
     * @param updateRootNodesFilterFlag - if true , filter acceptance flag will be monitored
     * @param rootNodesFilterResult     - hold canRead flag in case list is empty due to permission issue
     * @return list of child nodes
     */
    private List<ItemNode> getChildrenItemNode(boolean updateRootNodesFilterFlag,
            RootNodesFilterResult rootNodesFilterResult) {
        if (cachedChildrenNodes != null) {
            return cachedChildrenNodes;
        }
        List<ItemInfo> children = getFileService().loadChildren(itemInfo.getRepoPath());
        List<ItemNode> childrenNodes = Lists.newArrayListWithCapacity(children.size());
        sort(children);
        boolean localAcceptanceFlag = true;
        for (ItemInfo child : children) {
            NodeItemFilterHolder nodeItemFilterHolder = accepts(child);
            boolean isChildAccepted = nodeItemFilterHolder.isAccepted();
            if (isChildAccepted) {
                addChildToList(childrenNodes, child);
            } else {
                localAcceptanceFlag = updateAcceptedLocalFlag(localAcceptanceFlag, nodeItemFilterHolder);
            }
        }
        updateCachedChildrenNodes(childrenNodes);
        updateBrowsableItemAcceptedHolderCanReadFlag(updateRootNodesFilterFlag, rootNodesFilterResult,
                childrenNodes, localAcceptanceFlag);
        return childrenNodes;
    }

    @Override
    public List<ItemNode> getDescendants() {
        List<ItemInfo> descendants = getFileService().loadDescendants(itemInfo.getRepoPath());
        List<ItemNode> descendantNodes = Lists.newArrayListWithCapacity(descendants.size());
        sort(descendants);
        descendants.stream()
                .filter(descendant -> {
                    NodeItemFilterHolder nodeItemFilterHolder = accepts(descendant);
                    return nodeItemFilterHolder.isAccepted();
                }).forEach(descendant -> addChildToList(descendantNodes, descendant));
        updateChildren(descendantNodes);
        updateCachedChildrenNodes(descendantNodes);
        usedGetChildren = false;
        return descendantNodes;
    }

    private void updateChildren(List<ItemNode> descendants) {
        Map<String, List<ItemNode>> childrenPerFolder = descendants.stream()
                .filter(ItemNode::isFolder)
                .collect(Collectors.toMap(key -> key.getRepoPath().getPath(), value -> new ArrayList<>()));

        descendants.forEach(descendant -> {
            int lastIndexOf = descendant.getRepoPath().getPath().lastIndexOf('/');
            String parentPath = descendant.getRepoPath().getPath().substring(0, lastIndexOf);
            List<ItemNode> childList = childrenPerFolder.get(parentPath);
            if (childList != null) {
                childList.add(descendant);
            }
        });

        descendants.stream()
                .filter(ItemNode::isFolder)
                .map(FolderNode.class::cast)
                .forEach(folderNode -> {
                    folderNode.updateCachedChildrenNodes(
                            childrenPerFolder.get(folderNode.getRepoPath().getPath()));
                    folderNode.usedGetChildren = false;
                });
    }

    /**
     * update child cache and browsableItemAccept can read flag when list is empty duw to file access permission
     *
     * @param updateRootNodesFilterFlag - if true update the browsableItemAccept canRead flag
     * @param rootNodesFilterResult     - object hold the empty list canRead flag
     * @param childrenNodes             - list of child nodes
     * @param localAcceptanceFlag       - local can read flag , if false child node read permission isn't accepted
     */
    private void updateBrowsableItemAcceptedHolderCanReadFlag(boolean updateRootNodesFilterFlag,
            RootNodesFilterResult rootNodesFilterResult, List<ItemNode> childrenNodes,
            boolean localAcceptanceFlag) {
        if (childrenNodes.isEmpty() && updateRootNodesFilterFlag && !localAcceptanceFlag) {
            rootNodesFilterResult.setAllItemNodesCanRead(false);
        }
    }

    /**
     * update cache children nodes
     *
     * @param childrenNodes - list of children nodes
     */
    private void updateCachedChildrenNodes(List<ItemNode> childrenNodes) {
        if (criteria.isCacheChildren()) {
            cachedChildrenNodes = childrenNodes;
        }
    }

    /**
     * update the local can read flag , if flag is false meaning , at least one node has read permission issue
     *
     * @param localAcceptanceFlag accepted local flag
     * @param acceptedChild       - filter instance which hold the acceptance result
     * @return it false , meaning one of the note items didn't passed the canRead filter
     */
    private boolean updateAcceptedLocalFlag(boolean localAcceptanceFlag, NodeItemFilterHolder nodeItemFilterHolder) {
        ItemNodeFilter acceptedFilter = nodeItemFilterHolder.getItemNodeFilter();
        if (localAcceptanceFlag && acceptedFilter instanceof FilterAccepted) {
            localAcceptanceFlag = ((FilterAccepted) acceptedFilter).isNodeAcceptCanRead();
        }
        return localAcceptanceFlag;
    }

    /**
     * add child nodes to list if accepted
     *
     * @param childrenNodes - child node list
     * @param child         - node that has been accepted
     */
    private void addChildToList(List<ItemNode> childrenNodes, ItemInfo child) {
        if (child.isFolder()) {
            childrenNodes.add(new FolderNode((FolderInfo) child, criteria));
        } else {
            childrenNodes.add(new FileNode((FileInfo) child));
        }
    }

    @Override
    public List<ItemInfo> getChildrenInfo() {
        List<ItemNode> children = getChildren();
        return Lists.transform(children, new Function<ItemNode, ItemInfo>() {
            @Override
            public ItemInfo apply(ItemNode input) {
                return input.getItemInfo();
            }
        });
    }

    public NodeItemFilterHolder accepts(ItemInfo child) {
        NodeItemFilterHolder itemNodeFilterAccepted;
        if (criteria.getFilters() == null) {
            itemNodeFilterAccepted = new NodeItemFilterHolder(true);
            return itemNodeFilterAccepted;
        }
        for (ItemNodeFilter filter : criteria.getFilters()) {
            if (!filter.accepts(child)) {
                log.debug("Filter {} rejected {}", filter, child);
                itemNodeFilterAccepted = new NodeItemFilterHolder(false, filter);
                return itemNodeFilterAccepted;
            }
        }
        // all filters accepted the item
        itemNodeFilterAccepted = new NodeItemFilterHolder(true);
        return itemNodeFilterAccepted;
    }

    private void sort(List<ItemInfo> children) {
        if (criteria.getComparator() == null) {
            return;
        }
        Collections.sort(children, criteria.getComparator());
    }

    @Override
    public boolean hasChildren() {
        if (cachedChildrenNodes != null) {
            return !cachedChildrenNodes.isEmpty();
        } else {
            return getFileService().hasChildren(itemInfo.getRepoPath());
        }
    }

    @Override
    public FolderInfo getItemInfo() {
        return (FolderInfo) super.getItemInfo();
    }

    private FileService getFileService() {
        return ContextHelper.get().beanForType(FileService.class);
    }
}
