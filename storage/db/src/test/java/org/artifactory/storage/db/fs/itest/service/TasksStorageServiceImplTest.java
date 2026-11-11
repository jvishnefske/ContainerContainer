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

package org.artifactory.storage.db.fs.itest.service;

import com.google.common.collect.ImmutableList;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.fs.service.TasksStorageService;
import org.artifactory.storage.fs.service.XrayTask;
import org.artifactory.storage.task.TaskInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link TasksStorageService}
 *
 * @author Yossi Shaul
 */
@Test
public class TasksStorageServiceImplTest extends DbBaseTest {

    @Autowired
    private TasksStorageService tasksStorageService;

    @BeforeClass
    public void setup() {
        importSql("/sql/nodes-for-service.sql");
    }

    public void getIndexTasks() {
        List<RepoPath> tasks = tasksStorageService.getIndexTasks();
        assertNotNull(tasks);
        assertEquals(tasks.size(), 2);
        assertTrue(tasks.contains(InternalRepoPathFactory.createRepoPath("repo1:ant/ant/1.5/ant-1.5.jar")));
        assertTrue(tasks.contains(InternalRepoPathFactory.createRepoPath("reponone:ant/ant/1.5/ant-1.5.jar")));
    }

    public void hasIndexTask() {
        assertTrue(tasksStorageService
                .hasIndexTask(InternalRepoPathFactory.createRepoPath("repo1:ant/ant/1.5/ant-1.5.jar")));
    }

    @Test(dependsOnMethods = "getIndexTasks")
    public void addIndexTask() {
        RepoPath repoPath = InternalRepoPathFactory.createRepoPath("repo2:test");
        assertFalse(tasksStorageService.hasIndexTask(repoPath));
        tasksStorageService.addIndexTask(repoPath);
        assertTrue(tasksStorageService.hasIndexTask(repoPath));
    }

    @Test(dependsOnMethods = "addIndexTask")
    public void removeIndexTask() {
        assertTrue(tasksStorageService.removeIndexTask(InternalRepoPathFactory.createRepoPath("repo2:test")));
        assertFalse(tasksStorageService.hasIndexTask(InternalRepoPathFactory.createRepoPath("repo2:test")));
    }

    @Test(dependsOnMethods = "hasIndexTask")
    public void removeIndexTaskByRepoPath() {
        assertTrue(
                tasksStorageService
                        .removeIndexTask(InternalRepoPathFactory.createRepoPath("repo1:ant/ant/1.5/ant-1.5.jar")));
    }

    public void removeIndexTaskByRepoPathNotExist() {
        assertFalse(tasksStorageService.removeIndexTask(InternalRepoPathFactory.createRepoPath("nosuch:path.txt")));
    }

    public void getXrayEventOrderedTasks() {
        List<XrayTask> events = tasksStorageService.getXrayEventTasks();
        assertNotNull(events);
        assertEquals(events.size(), 2);
        assertEquals(events.get(0).getTaskContext(), "xray-event-1");
        assertEquals(events.get(1).getTaskContext(), "xray-event-2");
    }

    public void hasXrayEventTask() {
        assertTrue(tasksStorageService.hasXrayEventTask("xray-event-1"));
        assertFalse(tasksStorageService.hasXrayEventTask("xray-event-999"));
    }

    @Test(dependsOnMethods = "getXrayEventOrderedTasks")
    public void addXrayEventTask() {
        tasksStorageService.addXrayEventTask("xray-event-3");
        assertTrue(tasksStorageService.hasXrayEventTask("xray-event-3"));
    }

    @Test(dependsOnMethods = "getXrayEventOrderedTasks")
    public void addXrayEventTaskSkipEventAggregationTrue() {
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.xrayIndexerSkipEventAggregation, "true");
        tasksStorageService.addXrayEventTask("xray-cool-event-1");
        assertFalse(tasksStorageService.hasXrayEventTask("xray-cool-event-1"));
    }

    @Test(dependsOnMethods = "getXrayEventOrderedTasks")
    public void addXrayEventTaskSkipEventAggregationFalse() {
        artifactoryHomeBoundTest.setStringSystemProperty(ConstantValues.xrayIndexerSkipEventAggregation, "false");
        tasksStorageService.addXrayEventTask("xray-cool-event-1");
        assertTrue(tasksStorageService.hasXrayEventTask("xray-cool-event-1"));
    }

    @Test(dependsOnMethods = "addXrayEventTask")
    public void removeXrayEventTask() {
        assertTrue(tasksStorageService.removeXrayEventTask("xray-event-3"));
        assertFalse(tasksStorageService.hasXrayEventTask("xray-event-3"));
    }

    public void removeXrayEventByKeyNotExist() {
        assertFalse(tasksStorageService.removeXrayEventTask("nosuchevent"));
    }

    public void addTasks() {
        List<TaskInfo> tasks = ImmutableList.of(
                new TaskInfo("DEB:X", "a/b/c", 99L),
                new TaskInfo("DEB:X", "a/x/c", 99L),
                new TaskInfo("DEB:X", "z/x/c", 99L),
                new TaskInfo("DEB:Y", "a/b/c", 99L)
        );
        tasksStorageService.addTasks(tasks);

        // all 'DEB:X' with prefix of 'a'
        List<TaskInfo> results = tasksStorageService.getTaskByTypeAndContextPrefix("DEB:X", "a");
        assertEquals(results.size(), 2);
        List<TaskInfo> expectedTasks = tasks.stream()
                .filter(task -> task.getTaskType().equals("DEB:X") && task.getTaskContext().startsWith("a"))
                .collect(Collectors.toList());
        assertTrue(results.containsAll(expectedTasks));

        // all 'DEB:X' with prefix of 'z'
        results = tasksStorageService.getTaskByTypeAndContextPrefix("DEB:X", "z");
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), (new TaskInfo("DEB:X", "z/x/c", 99L)));

        // all 'DEB:Y' with prefix of 'a'
        results = tasksStorageService.getTaskByTypeAndContextPrefix("DEB:Y", "a");
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), (new TaskInfo("DEB:Y", "a/b/c", 99L)));
    }

    public void addEmptyTasks() {
        tasksStorageService.addTasks(null);
        tasksStorageService.addTasks(new ArrayList<>());
    }

    public void getTasksByPrefix() {
        List<TaskInfo> tasks = tasksStorageService
                .getTaskByTypeAndContextPrefix("DEB", "deb-local::trusty");
        List<TaskInfo> expectedTasks = ImmutableList.of(
                new TaskInfo("DEB", "deb-local::trusty/main/i386::ADD::dddd88fc2a043c2479a6de676a2f7179e9eaddac",
                        1340285601421L),
                new TaskInfo("DEB", "deb-local::trusty/main/i386::ADD::aaaa88fc2a043c2479a6de676a2f7179e9dddd",
                        1340285601422L),
                new TaskInfo("DEB", "deb-local::trusty/main/i386::DELETE::dddd88fc2a043c2479a6de676a2f7179e9eaddac",
                        1340285601426L),
                new TaskInfo("DEB", "deb-local::trusty/main/i386::ADD::aaaa88fc2a043c2479a6de676a2f7179e9dddd",
                        1340285601427L)
        );
        assertEquals(tasks, expectedTasks);
    }

    public void removeTasks() {
        List<TaskInfo> tasks = ImmutableList.of(
                new TaskInfo("DEB", "deb-local::precise/main/i386::ADD::aaaa88fc2a043c2479a6de676a2f7179e9dddd",
                        1340285601423L),
                new TaskInfo("DEB", "deb-local::precise/main/i386::DELETE::aaaa88fc2a043c2479a6de676a2f7179e9dddd",
                        1340285601425L));
        List<TaskInfo> resultedTasks = tasksStorageService
                .getTaskByTypeAndContextPrefix("DEB", "deb-local::precise");
        assertEquals(resultedTasks.size(), 2);
        assertTrue(resultedTasks.containsAll(tasks));

        // trying to delete similar tasks but with different dates, nothing should be deleted
        List<TaskInfo> similarTasks = ImmutableList.of(
                new TaskInfo("DEB", "deb-local::precise/main/i386::ADD::aaaa88fc2a043c2479a6de676a2f7179e9dddd",
                        1340285601421L),
                new TaskInfo("DEB", "deb-local::precise/main/i386::DELETE::aaaa88fc2a043c2479a6de676a2f7179e9dddd",
                        1340285601421L));
        tasksStorageService.removeTasks(similarTasks);
        assertEquals(resultedTasks.size(), 2);
        assertTrue(resultedTasks.containsAll(tasks));

        tasksStorageService.removeTasks(tasks);
        resultedTasks = tasksStorageService
                .getTaskByTypeAndContextPrefix("DEB", "deb-local::precise");
        assertEquals(resultedTasks.size(), 0);

        List<TaskInfo> otherTasks = tasksStorageService
                .getTaskByTypeAndContextPrefix("DEB", "deb-local::trusty");
        assertEquals(otherTasks.size(), 4);
    }
}
