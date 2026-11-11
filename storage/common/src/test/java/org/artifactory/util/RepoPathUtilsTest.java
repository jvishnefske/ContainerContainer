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

package org.artifactory.util;

import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.trash.TrashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Tests the {@link org.artifactory.util.RepoPathUtils}.
 * This test is here because it has runtime dependency on {@link org.artifactory.model.common.RepoPathImpl}.
 *
 * @author Yossi Shaul
 */
class RepoPathUtilsTest {

    @Test
    void getAncestorParent() {
        RepoPath path = RepoPathFactory.create("test", "1/2");
        assertEquals(RepoPathUtils.getAncestor(path, 1), path.getParent());
    }

    @Test
    void getAncestorGrandParent() {
        RepoPath path = RepoPathFactory.create("test", "1/2");
        assertEquals(RepoPathUtils.getAncestor(path, 2), path.getParent().getParent());
    }

    @Test
    void getAncestorOfRoot() {
        RepoPath path = RepoPathFactory.create("test", "");
        assertNull(RepoPathUtils.getAncestor(path, 1));
    }

    @ParameterizedTest
    @MethodSource("pathsToTest")
    void isAncestorOf(RepoPath repoPath, RepoPath otherRepoPath, boolean ancestor) {
        assertEquals(RepoPathUtils.isAncestorOf(repoPath, otherRepoPath), ancestor);
    }

    @Test
    void getAncestorBeyondRoot() {
        RepoPath path = RepoPathFactory.create("test", "1/2");
        assertNull(RepoPathUtils.getAncestor(path, 3));
    }

    @Test
    void isTrashOfTrash() {
        RepoPath path = RepoPathFactory.create(TrashService.TRASH_KEY, "1/2");
        assertTrue(RepoPathUtils.isTrash(path));
        assertTrue(RepoPathUtils.isTrash(path.getRepoKey()));
        assertTrue(RepoPathUtils.isTrash(RepoPathUtils.repoRootPath(TrashService.TRASH_KEY)));
        assertTrue(RepoPathUtils.isTrash(TrashService.TRASH_KEY));
    }

    @Test
    void isTrashRootRepo() {
        RepoPath path = RepoPathUtils.repoRootPath("repo");
        assertFalse(RepoPathUtils.isTrash(path));
        assertFalse(RepoPathUtils.isTrash(path.getRepoKey()));

    }

    @Test
    void isTrashNullRepoPath() {
        assertFalse(RepoPathUtils.isTrash((RepoPath) null));
    }

    @Test
    void fileRepoPathToFolderRepoPath() {
        RepoPath folder = RepoPathUtils.toFolderRepoPath(new RepoPathImpl("a", "b"));
        assertTrue(folder.isFolder());
        assertEquals("a/b/", folder.toPath());
    }

    @Test
    void folderRepoPathToFolderRepoPath() {
        RepoPath folderRepoPath = new RepoPathImpl("a", "b/");
        assertSame(folderRepoPath, RepoPathUtils.toFolderRepoPath(folderRepoPath));
    }


    @Test
    void folderRepoPathToFileRepoPath() {
        RepoPath file = RepoPathUtils.toFileRepoPath(new RepoPathImpl("a", "b", true));
        assertTrue(file.isFile());
        assertEquals("a/b", file.toPath());
        file = RepoPathUtils.toFileRepoPath(new RepoPathImpl("a", "b/"));
        assertTrue(file.isFile());
        assertEquals("a/b", file.toPath());
    }


    private static Object[][] pathsToTest() {
        return new Object[][] {
                {new RepoPathImpl("myKey", "x/y/z/"), new RepoPathImpl("anotherKey", "x/y/z/file.jar"), false},
                {new RepoPathImpl("myKey", "x/y/z"), new RepoPathImpl("anotherKey", "x/y/z/myfile.jar"), false},
                {new RepoPathImpl("myKey", "x/y/z/"), new RepoPathImpl("myKey", "x/y/z/file.jar"), true},
                {new RepoPathImpl("myKey", "x/y/z"), new RepoPathImpl("myKey", "x/y/z/this.txt"), true},
                {new RepoPathImpl("myKey", "x/y/"), new RepoPathImpl("myKey", "x/y/z/file.exe"), true},
                {new RepoPathImpl("myKey", "x/y/"), new RepoPathImpl("myKey", "x/y/z/dir"), true},
                {new RepoPathImpl("myKey", "x/y/"), new RepoPathImpl("myKey", "x/y/z/dir/"), true},
                {new RepoPathImpl("myKey", "x/"), new RepoPathImpl("myKey", "x/y/z/file.jar"), true},
                {new RepoPathImpl("myKey", "x"), new RepoPathImpl("myKey", "x/y/z/file.jar"), true},
                {new RepoPathImpl("myKey", ""), new RepoPathImpl("myKey", "x/y/file.jar"), true},
                {new RepoPathImpl("myKey", null), new RepoPathImpl("myKey", "x/y/file.jar"), true},
                {new RepoPathImpl("myKey", "x/y/z/"), new RepoPathImpl("myKey", "x/y/file.jar"), false},
                {new RepoPathImpl("myKey", "x/y/z"), new RepoPathImpl("myKey", "x/y/file.jar"), false},
                {new RepoPathImpl("myKey", "x/z/"), new RepoPathImpl("myKey", "x/y/z/file.jar"), false},
                {new RepoPathImpl("myKey", "y/z/"), new RepoPathImpl("myKey", "x/y/z/file.jar"), false},
                {new RepoPathImpl("myKey", "x/y/z.jar"), new RepoPathImpl("myKey", "x/y/z/file.jar"), false},
                {new RepoPathImpl("myKey", "file.jar"), new RepoPathImpl("myKey", "x/y/z/file.jar"), false},
                {new RepoPathImpl("myKey", "z.txt"), new RepoPathImpl("myKey", "x/y/z/file.jar"), false},
                {new RepoPathImpl("myKey", "x/y/file.jar"), new RepoPathImpl("myKey", "x/y/z/"), false},
                {new RepoPathImpl("myKey", "x/y/file.jar"), new RepoPathImpl("myKey", "x/y/z"), false},
                {new RepoPathImpl("myKey", "x/y/file.jar"), new RepoPathImpl("myKey", "a/y"), false},
                {new RepoPathImpl("myKey", "x/y/file.jar"), new RepoPathImpl("myKey", "a/y.xyz"), false},
                {new RepoPathImpl("myKey", "x/y/file.jar"), new RepoPathImpl("myKey", ""), false},
                {new RepoPathImpl("myKey", "x/y/file.jar"), new RepoPathImpl("myKey", null), false},
                {new RepoPathImpl("myKey", "x/y/z"), new RepoPathImpl("myKey", "x/y/z"), false},
                {new RepoPathImpl("myKey", "x/y/z/"), new RepoPathImpl("myKey", "x/y/z"), false},
                {new RepoPathImpl("myKey", "x/y/z"), new RepoPathImpl("myKey", "x/y/z/"), false},
                {new RepoPathImpl("myKey", "x/y/z/"), new RepoPathImpl("myKey", "x/y/z/"), false},
        };
    }

    @ParameterizedTest
    @MethodSource("dataForIsChildTest")
    void testIsChild(RepoPath child, RepoPath parent, boolean expectedResult) {
        assertEquals(RepoPathUtils.isChild(child, parent), expectedResult);
    }

    private static Object[][] dataForIsChildTest() {
        return new Object[][] {
                {new RepoPathImpl("repoA", "x/y/z/"), new RepoPathImpl("repoA", "x/y/z/file.jar"), false},
                {new RepoPathImpl("repoA", "x/y/z/file.jar"), new RepoPathImpl("repoA", "x/y/z/"), true},
                {new RepoPathImpl("repoA", "x/y/z/"), new RepoPathImpl("repoA", "x/y/z/"), true},
                {new RepoPathImpl("repoA", "x/y/z"), new RepoPathImpl("repoA", "x/y/z"), true},
                {new RepoPathImpl("repoA", "x/y/z"), new RepoPathImpl("repoA", "x/y/a"), false},
                {new RepoPathImpl("repoA", "x/y/z/"), new RepoPathImpl("repoB", "x/y/z/file.jar"), false}
        };
    }

    @Test
    void testRemoveDashCache() {
        assertEquals("repo", RepoPathUtils.removeDashCache("repo-cache"));
    }

    @Test
    void removeDashCacheFromPath() {
        RepoPath repoPath = RepoPathFactory.create("maven-remote-cache", "repoPath");
        assertEquals("maven-remote/repoPath", RepoPathUtils.removeDashCacheFromPath(repoPath));
    }

    @Test
    void testAddDashCache() {
        String repoKeyWithDashCache = RepoPathUtils.addDashCache("repoKey");
        assertEquals("repoKey-cache", repoKeyWithDashCache);
    }

    @Test
    void testGetOriginRepoPathForTrash() {
        RepoPath trashRepoPath = RepoPathFactory.create(TrashService.TRASH_KEY);
        RepoPath originRepoPath = RepoPathUtils.getOriginRepoPathForTrash(trashRepoPath);
        assertNull(originRepoPath);

        RepoPath expectedRepoPath = RepoPathFactory.create("someOriginRepo");

        trashRepoPath = RepoPathFactory.create(TrashService.TRASH_KEY, "someOriginRepo");
        assertEquals(RepoPathUtils.getOriginRepoPathForTrash(trashRepoPath), expectedRepoPath);

        expectedRepoPath = InternalRepoPathFactory.create(TrashService.TRASH_KEY, "someFolder", true);
        assertTrue(RepoPathUtils.getOriginRepoPathForTrash(expectedRepoPath).isFolder());

        expectedRepoPath = RepoPathFactory.create("someOriginRepo", "someFolder/someFile");
        trashRepoPath = RepoPathFactory.create(TrashService.TRASH_KEY, "someOriginRepo/someFolder/someFile");
        assertEquals(RepoPathUtils.getOriginRepoPathForTrash(trashRepoPath), expectedRepoPath);
    }

    @Test
    void testGetOriginRepoPathForTrashException() {
        RepoPath trashRepoPath = RepoPathFactory.create("someNonTrashRepo");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RepoPathUtils.getOriginRepoPathForTrash(trashRepoPath),
                "Expected doThing() to throw, but it didn't");

        assertEquals("Specified path is not a Trashcan path", exception.getMessage());

    }

    @ParameterizedTest
    @MethodSource("addTrailingSlashIfNeededProvider")
    void getTargetArtifact(String url, String path, String expect) {
        assertEquals(expect, RepoPathUtils.addTrailingSlashIfNeeded(path, url));
    }

    private static Stream<Arguments> addTrailingSlashIfNeededProvider() {
        return Stream.of(
                Arguments.of("http://someurl", "somepath", "http://someurl/somepath"),
                Arguments.of("http://someurl/", "somepath", "http://someurl/somepath"));
    }
}
