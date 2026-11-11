package com.jfrog.build.dao;

import com.jfrog.build.entity.BuildDependency;
import com.jfrog.build.test.dao.TestBuildDependenciesDao;
import com.jfrog.build.test.dao.TestBuildsDao;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;

import static com.jfrog.build.acl.model.BuildChecksumType.MD5;
import static com.jfrog.build.acl.model.BuildChecksumType.SHA1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class BuildDependenciesDaoComponentTest extends DatabaseTestBase {

    private static final String SHA1_SUFFIX = "dab88fc2a043c2479a6de676a2f8179e9ea2167";
    private static final String MD5_SUFFIX = "d2a360ecad98a34b59863c1e65bcf71";

    private static final String MD5_A = "a" + MD5_SUFFIX;
    private static final String MD5_B = "b" + MD5_SUFFIX;
    private static final String MD5_C = "c" + MD5_SUFFIX;
    private static final String MD5_X = "x" + MD5_SUFFIX;

    private static final String SHA1_A = "a" + SHA1_SUFFIX;
    private static final String SHA1_B = "b" + SHA1_SUFFIX;
    private static final String SHA1_C = "c" + SHA1_SUFFIX;
    private static final String SHA1_X = "x" + SHA1_SUFFIX;

    @Autowired
    private BuildDependenciesDao buildDependenciesDao;

    @Autowired
    private BuildModulesDao buildModulesDao;

    @Autowired
    private TestBuildsDao testBuildsDao;

    @Autowired
    private TestBuildDependenciesDao testBuildDependenciesDao;

    @Test
    void createBuildDependencies_EmptyInput() throws SQLException {
        buildDependenciesDao.createBuildDependencies(List.of());
        List<BuildDependency> dependencies = testBuildDependenciesDao.findDependenciesForModule(102L);
        assertEquals(0, dependencies.size());
    }

    @Test
    void createBuildDependencies_NullChecksumsDependencies() throws SQLException {
        long build14 = testBuildsDao.createAndInsertModules14();
        buildDependenciesDao.createBuildDependencies(List.of(
                new BuildDependency(4001L, 401L, "a:b:1.1", (String) null, "dll", null, MD5_X, null),
                new BuildDependency(4021L, 402L, "jfrog:bi:2.2.1", (String) null, null, SHA1_X, null, null),
                new BuildDependency(4023L, 402L, "jfrog:bi:2.2.1", (String) null, null, null, null, null),
                new BuildDependency(4022L, 402L, "jfrog:bi:2.2.1", (String) null, null, null, null, null)
        ));
        List<Long> moduleIds = buildModulesDao.findModuleIdsForBuild(build14);
        List<BuildDependency> dependencies = testBuildDependenciesDao.findDependenciesForModules(moduleIds);
        assertEquals(4, dependencies.size());
    }

    @Test
    void deleteBuildDependencies() throws SQLException {
        createSimpleDependency();
        createMultipleDependencies();

        assertFoundDependency1001(testBuildDependenciesDao.findDependenciesForChecksum(SHA1, SHA1_X));
        assertFoundDependency1001(testBuildDependenciesDao.findDependenciesForChecksum(MD5, MD5_X));

        List<BuildDependency> forSha1 = testBuildDependenciesDao.findDependenciesForChecksum(SHA1, "b" + SHA1_SUFFIX);
        List<BuildDependency> forMd5 = testBuildDependenciesDao.findDependenciesForChecksum(MD5, "b" + MD5_SUFFIX);

        assertEquals(2, forSha1.size());
        assertEquals(2, forMd5.size());

        assertBuildDependenciesFromBuild12(forSha1);
        assertBuildDependenciesFromBuild12(forMd5);

        assertEquals(1, buildDependenciesDao.deleteBuildDependencies(buildModulesDao.findModuleIdsForBuild(11L)));
        assertEquals(4, buildDependenciesDao.deleteBuildDependencies(buildModulesDao.findModuleIdsForBuild(12L)));
    }

    void createSimpleDependency() throws SQLException {
        testBuildsDao.createAndInsertModules11();
        buildDependenciesDao.createBuildDependencies(List.of(
                new BuildDependency(1001L, 101L, "group:dep1:1.0", "compile,test", "jar", SHA1_X, MD5_X, null)
        ));
        List<BuildDependency> dependencies = testBuildDependenciesDao.findDependenciesForModule(101L);
        assertFoundDependency1001(dependencies);
    }

    void createMultipleDependencies() throws SQLException {
        long build12 = testBuildsDao.createAndInsertModules12();
        buildDependenciesDao.createBuildDependencies(List.of(
                new BuildDependency(2001L, 201L, "apache:log4j:1.0", (String) null, "dll", SHA1_A, MD5_A, null),
                new BuildDependency(2002L, 201L, "jfrog:bi:2.2.0", (String) null, "zip", SHA1_B, MD5_B, null),
                new BuildDependency(2021L, 202L, "g:a:v", (String) null, "jar", SHA1_B, MD5_B, null),
                new BuildDependency(2022L, 202L, "d:b:6", (String) null, "pom", SHA1_C, MD5_C, null)
        ));
        List<Long> moduleIds = buildModulesDao.findModuleIdsForBuild(build12);
        List<BuildDependency> dependencies = testBuildDependenciesDao.findDependenciesForModules(moduleIds);
        assertEquals(4, dependencies.size());
        assertBuildDependenciesFromBuild12(dependencies);
    }

    private void assertBuildDependenciesFromBuild12(@NonNull List<BuildDependency> dependencies) {
        for (BuildDependency dependency : dependencies) {
            switch ((int) dependency.getDependencyId()) {
                case 2001:
                    assertEquals("apache:log4j:1.0", dependency.getDependencyNameId());
                    assertNull(dependency.getDependencyScopes());
                    assertEquals("dll", dependency.getDependencyType());
                    assertEquals(SHA1_A, dependency.getSha1());
                    assertEquals(MD5_A, dependency.getMd5());
                    assertNull(dependency.getSha2());
                    break;
                case 2002:
                    assertEquals("jfrog:bi:2.2.0", dependency.getDependencyNameId());
                    assertNull(dependency.getDependencyScopes());
                    assertEquals("zip", dependency.getDependencyType());
                    assertEquals(SHA1_B, dependency.getSha1());
                    assertEquals(MD5_B, dependency.getMd5());
                    assertNull(dependency.getSha2());
                    break;
                case 2021:
                    assertEquals("g:a:v", dependency.getDependencyNameId());
                    assertNull(dependency.getDependencyScopes());
                    assertEquals("jar", dependency.getDependencyType());
                    assertEquals(SHA1_B, dependency.getSha1());
                    assertEquals(MD5_B, dependency.getMd5());
                    assertNull(dependency.getSha2());
                    break;
                case 2022:
                    assertEquals("d:b:6", dependency.getDependencyNameId());
                    assertNull(dependency.getDependencyScopes());
                    assertEquals("pom", dependency.getDependencyType());
                    assertEquals(SHA1_C, dependency.getSha1());
                    assertEquals(MD5_C, dependency.getMd5());
                    assertNull(dependency.getSha2());
                    break;
                default:
                    fail("Build dependency " + dependency + " should not appear in this list!");
            }
        }
    }

    private void assertFoundDependency1001(@NonNull List<BuildDependency> dependencies) {
        assertEquals(1, dependencies.size());
        BuildDependency buildDependency = dependencies.getFirst();
        assertEquals(1001L, buildDependency.getDependencyId());
        assertEquals("group:dep1:1.0", buildDependency.getDependencyNameId());
        assertEquals("compile,test", buildDependency.getDependencyScopes());
        assertEquals("jar", buildDependency.getDependencyType());
        assertEquals(SHA1_X, buildDependency.getSha1());
        assertEquals(MD5_X, buildDependency.getMd5());
        assertNull(buildDependency.getSha2());
    }
}
