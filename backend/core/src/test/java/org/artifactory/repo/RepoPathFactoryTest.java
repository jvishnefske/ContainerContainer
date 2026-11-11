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

package org.artifactory.repo;

import org.artifactory.model.common.RepoPathImpl;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Yoav Landman
 */
@Test
public class RepoPathFactoryTest {

    public void testRepoPathFactory() {
        RepoPath repoPath = InternalRepoPathFactory.create("repox", "/a/b/c/");
        assertEquals(repoPath, InternalRepoPathFactory.create("repox", "/a/b/c/"));
        assertEquals(repoPath, InternalRepoPathFactory.create("repox", "a/b/c"));
        assertEquals(repoPath, InternalRepoPathFactory.create("repox", "a/b/c/"));
        assertEquals(repoPath, InternalRepoPathFactory.create("repox", "/a/b/c"));
    }

    public void pathWithLeadingSlash() {
        assertEquals(RepoPathFactory.create("/a/b/c"), new RepoPathImpl("a", "b/c"));
        assertEquals(RepoPathFactory.create("/a"), new RepoPathImpl("a", ""));
    }

    public void rootRepositoryPathSingleArgument() {
        RepoPath repoPath = RepoPathFactory.create("myrepo");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "");
        assertTrue(repoPath.isFolder());
        assertTrue(repoPath.isRoot());
    }

    public void rootRepositoryPath() {
        RepoPath repoPath = RepoPathFactory.create("myrepo", "");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "");
        assertTrue(repoPath.isFolder());
        assertTrue(repoPath.isRoot());
    }

    public void singleArgumentFactoryFolder() {
        RepoPath repoPath = RepoPathFactory.create("myrepo/a/b/c/");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "a/b/c");
        assertTrue(repoPath.isFolder());
    }

    public void createWithPathDot() {
        // When path is ".", should use only the name
        RepoPath repoPath = RepoPathFactory.create("myrepo", ".", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "file.txt"));
    }

    public void createWithPathDotAndFolderName() {
        // When path is ".", should use only the name (even if name ends with /)
        RepoPath repoPath = RepoPathFactory.create("myrepo", ".", "folder/");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "folder");
        assertTrue(repoPath.isFolder());
        assertEquals(repoPath, new RepoPathImpl("myrepo", "folder/"));
    }

    public void createWithNormalPath() {
        // Normal case: path + "/" + name
        RepoPath repoPath = RepoPathFactory.create("myrepo", "a/b", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "a/b/file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "a/b/file.txt"));
    }

    public void createWithEmptyPath() {
        // Empty path should combine with name
        RepoPath repoPath = RepoPathFactory.create("myrepo", "", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "file.txt"));
    }

    public void createWithBlankPath() {
        // Blank (null) path should use only the name, same as "." or empty string
        RepoPath repoPath = RepoPathFactory.create("myrepo", null, "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "file.txt"));
    }

    public void createWithPathEndingWithSlash() {
        // Path ending with slash should combine properly without adding another slash
        RepoPath repoPath = RepoPathFactory.create("myrepo", "a/b/", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "a/b/file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "a/b/file.txt"));
    }

    public void createWithFolderName() {
        // Name ending with "/" should be treated as folder
        RepoPath repoPath = RepoPathFactory.create("myrepo", "a/b", "folder/");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "a/b/folder");
        assertTrue(repoPath.isFolder());
        assertEquals(repoPath, new RepoPathImpl("myrepo", "a/b/folder/"));
    }

    public void createWithNestedPath() {
        // Nested path with multiple levels
        RepoPath repoPath = RepoPathFactory.create("myrepo", "level1/level2/level3", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "level1/level2/level3/file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "level1/level2/level3/file.txt"));
    }

    public void createWithPathDotAndNestedName() {
        // Path is "." but name contains path separator
        RepoPath repoPath = RepoPathFactory.create("myrepo", ".", "a/b/file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "a/b/file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "a/b/file.txt"));
    }

    public void createWithRootLevelFile() {
        // Root level file using "." path
        RepoPath repoPath = RepoPathFactory.create("myrepo", ".", "rootfile.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "rootfile.txt");
        assertFalse(repoPath.isRoot());
        assertEquals(repoPath, new RepoPathImpl("myrepo", "rootfile.txt"));
    }

    public void createWithSingleLevelPath() {
        // Single level path
        RepoPath repoPath = RepoPathFactory.create("myrepo", "folder", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "folder/file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "folder/file.txt"));
    }

    public void createWithSpecialCharactersInName() {
        // Name with special characters
        RepoPath repoPath = RepoPathFactory.create("myrepo", "folder", "file-name_v1.0.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "folder/file-name_v1.0.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "folder/file-name_v1.0.txt"));
    }

    public void createWithPathDotCaseSensitive() {
        // Path "." should be case-sensitive (uppercase should not match)
        RepoPath repoPath = RepoPathFactory.create("myrepo", "DOT", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "DOT/file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "DOT/file.txt"));
    }

    public void createWithPathJustSlash() {
        // Path that is just "/" should combine properly
        RepoPath repoPath = RepoPathFactory.create("myrepo", "/", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "file.txt"));
    }

    public void createWithPathEndingWithMultipleSlashes() {
        // Path ending with multiple slashes should still combine properly
        RepoPath repoPath = RepoPathFactory.create("myrepo", "a/b//", "file.txt");
        assertEquals(repoPath.getRepoKey(), "myrepo");
        assertEquals(repoPath.getPath(), "a/b/file.txt");
        assertEquals(repoPath, new RepoPathImpl("myrepo", "a/b/file.txt"));
    }
}