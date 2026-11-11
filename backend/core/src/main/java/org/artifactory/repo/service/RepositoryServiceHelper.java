package org.artifactory.repo.service;

import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.tree.ItemNode;
import org.artifactory.storage.fs.tree.ItemTree;
import org.artifactory.storage.fs.tree.TreeBrowsingCriteria;
import org.artifactory.storage.fs.tree.TreeBrowsingCriteriaBuilder;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * @author Oleksandr Legenkiy
 */
@Component
public class RepositoryServiceHelper {

    /**
     * Returns a list of children {@link org.artifactory.fs.ItemInfo} of the given repo path that can be read by the
     * current user.
     * An empty list is returned if the path doesn't exist of is not pointing to a folder.
     *
     * @param repoPath The repo path to list children
     * @return Returns a list of children {@link org.artifactory.fs.ItemInfo} of the given repo path or empty list.
     */
    @Nonnull
    List<ItemInfo> getChildren(RepoPath repoPath) {
        TreeBrowsingCriteria criteria = new TreeBrowsingCriteriaBuilder()
                .sortAlphabetically().applySecurity().cacheChildren(false).build();
        ItemNode rootNode = new ItemTree(repoPath, criteria).getRootNode();
        if (rootNode != null) {
            return rootNode.getChildrenInfo();
        } else {
            return Collections.emptyList();
        }
    }
}
