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

package org.artifactory.storage.db.fs.itest;

import jakarta.annotation.Nonnull;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.tree.ItemNode;
import org.artifactory.storage.fs.tree.ItemNodeFilter;
import org.artifactory.storage.fs.tree.ItemTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link org.artifactory.storage.fs.tree.ItemTree}.
 *
 * @author Yossi Shaul
 */
@Test
public class ItemTreeTest extends DbBaseTest {

    @Autowired
    FileService fileService;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes-for-service.sql");
    }

    public void rootItemTree() {
        RepoPathImpl repo1 = new RepoPathImpl("repo1", "");
        ItemTree itemTree = new ItemTree(repo1);
        ItemNode rootNode = itemTree.getRootNode();
        assertNotNull(rootNode);
        assertEquals(rootNode.getRepoPath(), repo1);
        assertTrue(rootNode.hasChildren());
        assertEquals(rootNode.getChildren().size(), 2);
    }

    public void descendantItemTree() {
        RepoPathImpl repo1 = new RepoPathImpl("repo2", "org/jfrog");
        ItemTree local = new ItemTree(repo1, new ItemNodeFilter() {
            @Override
            public boolean accepts(@Nonnull ItemInfo itemInfo) {
                return !itemInfo.getName().equals("test.jar");
            }
        });
        ItemNode rootNode = local.buildTree();
        assertNotNull(rootNode);
        assertEquals(rootNode.getDescendants().size(), 2);
        assertFalse(rootNode.isUsedGetChildren());
        assertEquals(rootNode.getCachedChildrenNodes().size(), 2);
    }

    public void descendantItemTreeWithUnderscore() {
        RepoPathImpl repo1 = new RepoPathImpl("repo4", "org/a_b_c");
        ItemTree itemTree = new ItemTree(repo1);
        ItemNode rootNode = itemTree.buildTree();
        assertNotNull(rootNode);
        assertEquals(rootNode.getDescendants().size(), 4);
    }

    public void descendantItemTreeWithPercentMark() {
        RepoPathImpl repo1 = new RepoPathImpl("repo6", "org/a%c");
        ItemTree itemTree = new ItemTree(repo1);
        ItemNode rootNode = itemTree.buildTree();
        assertNotNull(rootNode);
        assertEquals(rootNode.getDescendants().size(), 4);
    }

    public void itemTreeWithoutPath() {
        RepoPathImpl repo1 = new RepoPathImpl("repo1", "");
        ItemTree itemTree = new ItemTree(repo1);
        ItemNode rootNode = itemTree.buildTree();
        assertNotNull(rootNode);
        assertEquals(rootNode.getChildren().size(), 2);
    }

    public void preBuildTree() {
        RepoPathImpl repo1 = new RepoPathImpl("repo1", "");
        ItemTree itemTree = new ItemTree(repo1);
        ItemNode rootNode = itemTree.buildTree();
        assertNotNull(rootNode);
        assertEquals(rootNode.getRepoPath(), repo1);
    }

    public void treeWithFilter() {
        RepoPathImpl repo1 = new RepoPathImpl("repo1", "");
        ItemTree itemTree = new ItemTree(repo1, new ItemNodeFilter() {
            @Override
            public boolean accepts(@Nonnull ItemInfo itemInfo) {
                return false;   // filter everything
            }
        });
        ItemNode rootNode = itemTree.buildTree();
        assertNotNull(rootNode);    // filter doesn't affect root node
        assertEquals(rootNode.getRepoPath(), repo1);
        assertTrue(rootNode.getChildren().isEmpty());
    }
}
