package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Integration test for {@link NodesDao#findReferencedSha1s(List)}.
 * Runs against all supported databases (Derby, PostgreSQL, MySQL, MariaDB,
 * MSSQL, Oracle) via DbBaseTest infrastructure.
 * <p>
 * Test data from /sql/nodes.sql:
 * <ul>
 *   <li>dcab88fc... -> repo1 (file node, referenced)</li>
 *   <li>acab88fc... -> repo1 (file node, referenced)</li>
 *   <li>dddd89fc... -> repo1, repo3, auto-trashcan (referenced outside trashcan)</li>
 *   <li>dddd88fc... -> repo-copy (referenced)</li>
 *   <li>dddd88fc2a043c2479a6de676a2f8179e9eadddd -> repo1, repo-copy (referenced)</li>
 * </ul>
 *
 * @author pankaj
 */
@Test
public class NodesDaoFindReferencedSha1sTest extends DbBaseTest {

    // SHA1s from nodes.sql that exist in non-trashcan repos
    private static final String SHA1_IN_REPO1 =
            "dcab88fc2a043c2479a6de676a2f8179e9ea2167";
    private static final String SHA1_IN_REPO1_AND_REPO_COPY =
            "dddd88fc2a043c2479a6de676a2f8179e9eadddd";
    private static final String SHA1_IN_MULTIPLE_REPOS =
            "dddd89fc2a043c2479a6de676a2f7179e9eaddac";
    // SHA1 that does not exist anywhere
    private static final String SHA1_NOT_EXISTS =
            "0000000000000000000000000000000000000000";

    @Autowired
    private NodesDao nodesDao;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes.sql");
    }

    public void emptyInputReturnsEmptySet() {
        Set<String> result =
                nodesDao.findReferencedSha1s(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test(dependsOnMethods = "emptyInputReturnsEmptySet")
    public void findsReferencedSha1s() {
        Set<String> result = nodesDao.findReferencedSha1s(List.of(
                SHA1_IN_REPO1,
                SHA1_IN_REPO1_AND_REPO_COPY,
                SHA1_NOT_EXISTS));

        assertTrue(result.contains(SHA1_IN_REPO1));
        assertTrue(result.contains(SHA1_IN_REPO1_AND_REPO_COPY));
        assertFalse(result.contains(SHA1_NOT_EXISTS));
        assertEquals(result.size(), 2);
    }

    @Test(dependsOnMethods = "emptyInputReturnsEmptySet")
    public void sha1InMultipleReposReturnedOnce() {
        Set<String> result = nodesDao.findReferencedSha1s(
                List.of(SHA1_IN_MULTIPLE_REPOS));

        assertTrue(result.contains(SHA1_IN_MULTIPLE_REPOS));
        assertEquals(result.size(), 1);
    }

    @Test(dependsOnMethods = "emptyInputReturnsEmptySet")
    public void nonExistentSha1NotReturned() {
        Set<String> result = nodesDao.findReferencedSha1s(
                List.of(SHA1_NOT_EXISTS));

        assertTrue(result.isEmpty());
    }

    @Test(dependsOnMethods = "emptyInputReturnsEmptySet")
    public void allInputReferencedReturnsAll() {
        List<String> allReferenced = List.of(
                SHA1_IN_REPO1,
                SHA1_IN_REPO1_AND_REPO_COPY,
                SHA1_IN_MULTIPLE_REPOS);

        Set<String> result =
                nodesDao.findReferencedSha1s(allReferenced);

        assertEquals(result.size(), 3);
    }

    @Test(dependsOnMethods = "emptyInputReturnsEmptySet")
    public void duplicateInputDeduplicatedInResult() {
        Set<String> result = nodesDao.findReferencedSha1s(List.of(
                SHA1_IN_REPO1, SHA1_IN_REPO1, SHA1_IN_REPO1));

        assertEquals(result.size(), 1);
        assertTrue(result.contains(SHA1_IN_REPO1));
    }

    @Test(dependsOnMethods = "emptyInputReturnsEmptySet")
    public void mixedReferencedAndOrphanedSha1s() {
        Set<String> result = nodesDao.findReferencedSha1s(List.of(
                SHA1_IN_REPO1,
                SHA1_NOT_EXISTS,
                SHA1_IN_MULTIPLE_REPOS,
                "aaaa000000000000000000000000000000000000"));

        assertEquals(result.size(), 2);
        assertTrue(result.contains(SHA1_IN_REPO1));
        assertTrue(result.contains(SHA1_IN_MULTIPLE_REPOS));
        assertFalse(result.contains(SHA1_NOT_EXISTS));
    }
}
