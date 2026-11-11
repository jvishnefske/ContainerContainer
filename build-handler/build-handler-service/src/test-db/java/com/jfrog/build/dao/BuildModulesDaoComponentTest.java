package com.jfrog.build.dao;

import com.google.common.collect.ImmutableSet;
import com.jfrog.build.entity.BuildModule;
import com.jfrog.build.entity.ModuleProperty;
import com.jfrog.build.test.dao.TestBuildModulesDao;
import com.jfrog.build.test.dao.TestBuildsDao;
import com.jfrog.build.test.database.base.DatabaseTestBase;
import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.List;

import static com.jfrog.build.dao.util.BuildsDaoUtils.getDbIndexedValueMaxSize;
import static com.jfrog.build.test.database.TestDatabaseProvider.getDatabaseType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BuildModulesDaoComponentTest extends DatabaseTestBase {

    @Autowired
    private BuildModulesDao buildModulesDao;

    @Autowired
    private TestBuildModulesDao testBuildModulesDao;

    @Autowired
    private TestBuildsDao testBuildsDao;

    @BeforeEach
    void setup() {
        importSql("/sql/dml/builds.sql");
    }

    @Test
    void findModulesForBuild() throws SQLException {
        for (long i = 1L; i <= 5L; i++) {
            assertAnyBuildModuleList(testBuildModulesDao.findModulesForBuild(i));
        }
    }

    @Test
    void deleteBuildModules() throws SQLException {
        simpleCreateBuildModule();
        createTwoBuildModulesWithProperties();
        doDelete(11L, 1, 1);
        doDelete(12L, 2, 9);
    }

    private void simpleCreateBuildModule() throws SQLException {
        testBuildsDao.createAndInsertModules11();
        long buildId = 11L;
        List<BuildModule> modulesForBuild = testBuildModulesDao.findModulesForBuild(buildId);
        assertEquals(1, modulesForBuild.size());
        BuildModule buildModule1 = modulesForBuild.getFirst();
        assertEquals(101L, buildModule1.getModuleId());
        assertEquals("b11:mod1", buildModule1.getModuleNameId());
        assertTrue(buildModule1.getProperties().isEmpty());
    }

    private void createTwoBuildModulesWithProperties() throws SQLException {
        int dbIndexedValueMaxSize = getDbIndexedValueMaxSize(getDatabaseType());
        long buildId = testBuildsDao.createAndInsertModules12();
        List<BuildModule> modulesForBuild = testBuildModulesDao.findModulesForBuild(buildId);
        assertEquals(2, modulesForBuild.size());
        for (BuildModule module : modulesForBuild) {
            switch ((int) module.getModuleId()) {
                case 201:
                    assertEquals(201L, module.getModuleId());
                    assertEquals("b12:the-mod1", module.getModuleNameId());
                    assertEquals(2, module.getProperties().size());
                    break;
                case 202:
                    assertEquals(202L, module.getModuleId());
                    assertEquals("b12:not-mod2", module.getModuleNameId());
                    assertEquals(5, module.getProperties().size());
                    module.getProperties().forEach(moduleProperty ->
                            assertTrue(moduleProperty.getPropValue().length() <= dbIndexedValueMaxSize));
                    break;
                default:
                    fail("Build module " + module + " unexpected!");
            }
        }
    }

    private void doDelete(long buildId, int modulesCount, int expectedDeletedRowsCount) throws SQLException {
        List<Long> modulesForBuild = buildModulesDao.findModuleIdsForBuild(buildId);
        assertNotNull(modulesForBuild);
        assertEquals(modulesCount, modulesForBuild.size());
        assertEquals(expectedDeletedRowsCount, buildModulesDao.deleteBuildModules(modulesForBuild, buildId));
        assertTrue(testBuildModulesDao.findModulesForBuild(buildId).isEmpty());
    }

    private void assertAnyBuildModuleList(@NonNull List<BuildModule> modules) {
        assertNotNull(modules);
        assertFalse(modules.isEmpty());
        modules.sort((o1, o2) -> {
            if (o1.getBuildId() != o2.getBuildId()) {
                return Long.compare(o1.getBuildId(), o2.getBuildId());
            } else {
                return Long.compare(o1.getModuleId(), o2.getModuleId());
            }
        });
        long buildId = modules.getFirst().getBuildId();
        switch ((int) buildId) {
            case 1:
                assertBuildModuleData(modules, "a", new int[] {3}, new String[] {"bad"});
                break;
            case 2:
                assertBuildModuleData(modules, "b", new int[] {2, 2}, new String[] {"not-too-bad", "quite-good"});
                break;
            case 3:
                assertBuildModuleData(modules, "a", new int[] {0, 0}, new String[0]);
                break;
            case 4:
                assertBuildModuleData(modules, "b", new int[] {0, 0}, new String[0]);
                break;
            case 5:
                assertBuildModuleData(modules, "a", new int[] {2, 2, 2}, new String[] {"good", "good", "good"});
                break;
            default:
                fail("Build with id " + buildId + " is not known!");
        }
    }

    private void assertBuildModuleData(
            @NonNull List<BuildModule> buildModules,
            @NonNull String shortBuildName,
            int[] nbModulePropsExpected,
            @NonNull String[] moduleStatusExpected) {
        assertEquals(buildModules.size(), nbModulePropsExpected.length);
        int i = 0;
        for (BuildModule module : buildModules) {
            String artName = "mod" + shortBuildName + (i + 1);
            String moduleName = "b" + shortBuildName + ":" + artName;
            assertEquals(module.getModuleNameId(), moduleName);
            ImmutableSet<ModuleProperty> modProps = module.getProperties();
            assertEquals(modProps.size(), nbModulePropsExpected[i]);
            if (modProps.size() >= 2) {
                boolean foundName = false;
                boolean foundStatus = false;
                for (ModuleProperty modProp : modProps) {
                    if ("art-name".equals(modProp.getPropKey())) {
                        assertFalse(foundName, "Got 2 times the name property " + modProp);
                        assertEquals(modProp.getPropValue(), artName);
                        foundName = true;
                    }
                    if (modProp.getPropKey().startsWith("status")) {
                        assertFalse(foundStatus, "Got 2 times the status property " + modProp);
                        assertEquals(modProp.getPropValue(), moduleStatusExpected[i],
                                "Property " + modProp + " was not expected value " + moduleStatusExpected[i]);
                        foundStatus = true;
                    }
                }
                assertTrue(foundName, "Did not find the art-name property");
                assertTrue(foundStatus, "Did not find the status property");
            }
            i++;
        }
    }
}
