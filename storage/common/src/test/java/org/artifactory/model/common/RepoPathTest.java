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

package org.artifactory.model.common;

import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the RepoPath class.
 *
 * @author Yossi Shaul
 */
@Test
public class RepoPathTest {

    public void getParentRepoPathWithParent() {
        RepoPath child = new RepoPathImpl("repo", "a/b/c");
        RepoPath parent = child.getParent();
        Assert.assertEquals(parent, new RepoPathImpl("repo", "/a/b"));
    }

    public void getParentRepoPathLastParent() {
        RepoPath child = new RepoPathImpl("repo", "a/");
        RepoPath parent = child.getParent();
        Assert.assertEquals(parent, new RepoPathImpl("repo", ""));
    }

    public void getParentRepoPathForRoot() {
        RepoPath child = new RepoPathImpl("repo", "/");
        RepoPath parent = child.getParent();
        assertNull(parent);
    }

    public void getParentRepoPathWithNoParent() {
        RepoPath child = new RepoPathImpl("repo", "");
        RepoPath parent = child.getParent();
        assertNull(parent);
    }

    public void repoRootPath() {
        RepoPath repoPath = InternalRepoPathFactory.repoRootPath("repokey");
        assertEquals(repoPath.getRepoKey(), "repokey");
        assertEquals("", repoPath.getPath(), "Repository root path should be an empty string");
        assertTrue(repoPath.isRoot());
    }

    public void rootPath() {
        assertFalse(new RepoPathImpl("1", "2").isRoot());
        assertTrue(new RepoPathImpl("1", "").isRoot());
        assertTrue(new RepoPathImpl("1", "     ").isRoot());
        assertTrue(new RepoPathImpl("1", "     ").isRoot());
        assertTrue(new RepoPathImpl("1", null).isRoot());
        nullRepoKey();
    }

    private void nullRepoKey() {
        try {
            // Should throw exception repoKey can't be null
            new RepoPathImpl((String) null, "");
            fail();
        } catch (IllegalArgumentException e) {
            //We expected failure during RepoPath creation  (repo key can't be null);
        }
    }

    public void archiveResourcePath() {
        RepoPathImpl archivePath = new RepoPathImpl("key", "file.jar");
        RepoPath resourcePath = InternalRepoPathFactory.archiveResourceRepoPath(archivePath, "path/to/resource");
        Assert.assertEquals(resourcePath.getRepoKey(), archivePath.getRepoKey());
        assertEquals(resourcePath.getPath(), "file.jar!/path/to/resource");
    }

    public void archiveResourcePathLeadingSlash() {
        RepoPathImpl archivePath = new RepoPathImpl("key", "folder");
        RepoPath resourcePath = InternalRepoPathFactory.archiveResourceRepoPath(archivePath, "/path/to/resource");
        Assert.assertEquals(resourcePath.getRepoKey(), archivePath.getRepoKey());
        assertEquals(resourcePath.getPath(), "folder!/path/to/resource");
    }

    public void toPathWithRelativePath() {
        RepoPathImpl repoPath = new RepoPathImpl("key", "path/to/");
        assertEquals(repoPath.toPath(), "key/path/to/");
    }

    public void toPathRootRepoPath() {
        RepoPathImpl repoPath = new RepoPathImpl("key", "");
        assertEquals(repoPath.toPath(), "key/");
    }

    @DataProvider(name = "getCanonicalKeyCases")
    public Object[][] getCanonicalKeyCases() {
        return new Object[][] {
                {"repo", "a/b/c", "repo/a/b/c"},
                {"repo", "single", "repo/single"},
                {"repo", "a/b/c/", "repo/a/b/c"},                  // trailing slash trimmed by RepoPathImpl
                {"repo", "/a/b/c", "repo/a/b/c"},                  // leading slash trimmed
                {"repo", "", "repo/"},                             // repo root
                {"my-repo", "deep/nested/file.txt", "my-repo/deep/nested/file.txt"},
        };
    }

    @Test(dataProvider = "getCanonicalKeyCases")
    public void getCanonicalKey(String repoKey, String path, String expected) {
        assertEquals(new RepoPathImpl(repoKey, path).getCanonicalKey(), expected);
    }

    @DataProvider(name = "folderPathCases")
    public Object[][] folderPathCases() {
        return new Object[][] {
                // repoKey, path, expected toPath() (with slash), expected key (without slash)
                {"repo", "a/b", "repo/a/b/", "repo/a/b"},
                {"repo", "single", "repo/single/", "repo/single"},
                {"repo", "deep/nested/dir", "repo/deep/nested/dir/", "repo/deep/nested/dir"},
                {"my-repo", "a", "my-repo/a/", "my-repo/a"},
        };
    }

    @Test(dataProvider = "folderPathCases")
    public void getCanonicalKeyStripsFolderSlash(String repoKey, String path, String expectedToPath,
            String expectedKey) {
        RepoPath folder = new RepoPathImpl(repoKey, path, true);
        // The RepoPath genuinely represents a folder, so toPath() carries a trailing slash...
        assertTrue(folder.toPath().endsWith("/"), "folder toPath() has a trailing slash");
        assertEquals(folder.toPath(), expectedToPath);
        // ...but the canonical key must strip it (it mirrors equals(), which ignores the folder flag).
        assertFalse(folder.getCanonicalKey().endsWith("/"), "key must not have a trailing slash");
        assertEquals(folder.getCanonicalKey(), expectedKey);
    }

    public void getCanonicalKeyMatchesForEquivalentInstances() {
        RepoPath a = new RepoPathImpl("repo", "a/b/c");
        RepoPath b = new RepoPathImpl("repo", "/a/b/c/");   // same logical path, different construction
        assertEquals(a.getCanonicalKey(), b.getCanonicalKey());
        assertEquals(a, b);                                 // consistent with equals()
    }

    public void getCanonicalKeyUsesSlashNotIdSeparator() {
        RepoPath path = new RepoPathImpl("repo", "a/b");
        assertEquals(path.getCanonicalKey(), "repo/a/b");
        assertEquals(path.getId(), "repo:a/b");             // getId() uses ':'; getCanonicalKey() uses '/'
    }

    public void getCanonicalKeyDistinctPathsDoNotCollide() {
        // Uniqueness relies on repo keys being slash-free (repository naming rules): the first '/' is always the
        // repoKey/path boundary, so different repo keys or paths can never produce the same key.
        assertNotEquals(new RepoPathImpl("a", "b/c").getCanonicalKey(),
                new RepoPathImpl("a", "bc").getCanonicalKey());
        assertNotEquals(new RepoPathImpl("repo1", "a/b").getCanonicalKey(),
                new RepoPathImpl("repo2", "a/b").getCanonicalKey());
    }
}
