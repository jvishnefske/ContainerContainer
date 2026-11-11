package org.artifactory.storage.db.tasks;

import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.binstore.providers.base.PersistedRemoteTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;

/**
 * @author Shahar Palmor
 */
public class BinariesTasksMigrationDaoTest extends DbBaseTest {

    @Autowired
    BinariesTasksMigrationDao binariesTasksMigrationDao;

    @Test
    public void testFlow() throws SQLException {
        binariesTasksMigrationDao.createTasks(createPersistedRemoteTasks());
        List<PersistedRemoteTask> tasks = binariesTasksMigrationDao.getTasks();
        assertEquals(tasks.size(), 500);
        binariesTasksMigrationDao.deleteTasksByIds(
                tasks.stream().map(PersistedRemoteTask::getId).collect(Collectors.toSet()));
        List<PersistedRemoteTask> tasksAfterDelete = binariesTasksMigrationDao.getTasks();
        assertEquals(tasksAfterDelete.size(), 100);
    }

    private List<PersistedRemoteTask> createPersistedRemoteTasks() {
        List<PersistedRemoteTask> tasks = new ArrayList<>();
        for(int i = 0; i < 600; i++) {
            tasks.add(new PersistedRemoteTask(i, "repo", "sha" + i));
        }
        return tasks;
    }
}