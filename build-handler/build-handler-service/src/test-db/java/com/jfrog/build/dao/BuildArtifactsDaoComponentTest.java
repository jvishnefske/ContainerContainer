package com.jfrog.build.dao;

import com.jfrog.build.entity.BuildArtifact;
import com.jfrog.build.test.dao.TestBuildArtifactsDao;
import com.jfrog.build.test.dao.TestBuildsDao;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.jfrog.build.acl.model.BuildChecksumType.MD5;
import static com.jfrog.build.acl.model.BuildChecksumType.SHA1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class BuildArtifactsDaoComponentTest extends DatabaseTestBase {

    private static final String SHA1_SUFFIX = "cab88fc2a043c2479a6de676a2f8179e9ea2167";
    private static final String MD5_SUFFIX = "02a360ecad98a34b59863c1e65bcf71";

    private static final String MD5_A = "a" + MD5_SUFFIX;
    private static final String MD5_B = "b" + MD5_SUFFIX;
    private static final String MD5_C = "c" + MD5_SUFFIX;
    private static final String MD5_X = "x" + MD5_SUFFIX;

    private static final String SHA1_A = "a" + SHA1_SUFFIX;
    private static final String SHA1_B = "b" + SHA1_SUFFIX;
    private static final String SHA1_C = "c" + SHA1_SUFFIX;
    private static final String SHA1_X = "x" + SHA1_SUFFIX;

    @Autowired
    private BuildArtifactsDao buildArtifactsDao;

    @Autowired
    private BuildModulesDao buildModulesDao;

    @Autowired
    private TestBuildsDao testBuildsDao;

    @Autowired
    private TestBuildArtifactsDao testBuildArtifactsDao;

    @Test
    void createBuildArtifacts_EmptyInput() throws SQLException {
        buildArtifactsDao.createBuildArtifacts(List.of());
        List<BuildArtifact> artifacts = testBuildArtifactsDao.findBuildArtifactsForModule(102L);
        assertEquals(0, artifacts.size());
    }

    @Test
    void createBuildArtifacts_NullChecksumsArtifacts() throws SQLException {
        long build14 = testBuildsDao.createAndInsertModules14();
        ArrayList<BuildArtifact> bas = new ArrayList<>(2);
        bas.add(new BuildArtifact(4001L, 401L, "b2-art1", "dll", SHA1_X, null, null));
        bas.add(new BuildArtifact(4021L, 401L, "b2-art1", "dll", null, null, null));
        bas.add(new BuildArtifact(4022L, 402L, "b2-m2-art1", "jar", null, MD5_X, null));
        bas.add(new BuildArtifact(4023L, 402L, "b2-m2-art1", null, null, null, null));
        buildArtifactsDao.createBuildArtifacts(bas);
        List<Long> moduleIds = buildModulesDao.findModuleIdsForBuild(build14);
        List<BuildArtifact> artifacts = testBuildArtifactsDao.findBuildArtifactsForModules(moduleIds);
        assertEquals(4, artifacts.size());
    }

    @Test
    void deleteBuildArtifacts() throws SQLException {
        createSimpleArtifact();
        createMultipleArtifacts();

        assertFoundArtifact1001(testBuildArtifactsDao.findBuildArtifactsForChecksum(SHA1, SHA1_X));
        assertFoundArtifact1001(testBuildArtifactsDao.findBuildArtifactsForChecksum(MD5, MD5_X));

        List<BuildArtifact> forSha1 = testBuildArtifactsDao.findBuildArtifactsForChecksum(SHA1, SHA1_B);
        List<BuildArtifact> forMd5 = testBuildArtifactsDao.findBuildArtifactsForChecksum(MD5, MD5_B);

        assertEquals(2, forSha1.size());
        assertEquals(2, forMd5.size());

        assertBuildArtifactsFromBuild12(forSha1);
        assertBuildArtifactsFromBuild12(forMd5);

        assertEquals(1, buildArtifactsDao.deleteBuildArtifacts(buildModulesDao.findModuleIdsForBuild(11L)));
        assertEquals(4, buildArtifactsDao.deleteBuildArtifacts(buildModulesDao.findModuleIdsForBuild(12L)));
    }

    private void createSimpleArtifact() throws SQLException {
        testBuildsDao.createAndInsertModules11();
        BuildArtifact ba = new BuildArtifact(1001L, 101L, "art1", "jar", SHA1_X, MD5_X, null);
        buildArtifactsDao.createBuildArtifacts(List.of(ba));
        List<BuildArtifact> artifacts = testBuildArtifactsDao.findBuildArtifactsForModule(101L);
        assertFoundArtifact1001(artifacts);
    }

    private void createMultipleArtifacts() throws SQLException {
        long build12 = testBuildsDao.createAndInsertModules12();
        ArrayList<BuildArtifact> bas = new ArrayList<>(2);
        bas.add(new BuildArtifact(2001L, 201L, "b2-art1", "dll", SHA1_A, MD5_A, null));
        bas.add(new BuildArtifact(2002L, 201L, "b2-art2", "zip", SHA1_B, MD5_B, null));
        bas.add(new BuildArtifact(2021L, 202L, "b2-m2-art1", "jar", SHA1_B, MD5_B, null));
        bas.add(new BuildArtifact(2022L, 202L, "b2-m2-art2", "pom", SHA1_C, MD5_C, null));
        buildArtifactsDao.createBuildArtifacts(bas);
        List<Long> moduleIds = buildModulesDao.findModuleIdsForBuild(build12);
        List<BuildArtifact> artifacts = testBuildArtifactsDao.findBuildArtifactsForModules(moduleIds);
        assertEquals(4, artifacts.size());
        assertBuildArtifactsFromBuild12(artifacts);
    }

    private void assertFoundArtifact1001(@NonNull List<BuildArtifact> artifacts) {
        assertEquals(1, artifacts.size());
        BuildArtifact buildArtifact = artifacts.getFirst();
        assertEquals(1001L, buildArtifact.getArtifactId());
        assertEquals("art1", buildArtifact.getArtifactName());
        assertEquals("jar", buildArtifact.getArtifactType());
        assertEquals(SHA1_X, buildArtifact.getSha1());
        assertNull(buildArtifact.getSha2());
        assertEquals(MD5_X, buildArtifact.getMd5());
    }

    private void assertBuildArtifactsFromBuild12(@NonNull List<BuildArtifact> artifacts) {
        for (BuildArtifact artifact : artifacts) {
            switch ((int) artifact.getArtifactId()) {
                case 2001:
                    assertEquals("b2-art1", artifact.getArtifactName());
                    assertEquals("dll", artifact.getArtifactType());
                    assertEquals(SHA1_A, artifact.getSha1());
                    assertEquals(MD5_A, artifact.getMd5());
                    break;
                case 2002:
                    assertEquals("b2-art2", artifact.getArtifactName());
                    assertEquals("zip", artifact.getArtifactType());
                    assertEquals(SHA1_B, artifact.getSha1());
                    assertEquals(MD5_B, artifact.getMd5());
                    break;
                case 2021:
                    assertEquals("b2-m2-art1", artifact.getArtifactName());
                    assertEquals("jar", artifact.getArtifactType());
                    assertEquals(SHA1_B, artifact.getSha1());
                    assertEquals(MD5_B, artifact.getMd5());
                    break;
                case 2022:
                    assertEquals("b2-m2-art2", artifact.getArtifactName());
                    assertEquals("pom", artifact.getArtifactType());
                    assertEquals(SHA1_C, artifact.getSha1());
                    assertEquals(MD5_C, artifact.getMd5());
                    break;
                default:
                    fail("Build artifact " + artifact + " should not appear in this list!");
            }
        }
    }
}
