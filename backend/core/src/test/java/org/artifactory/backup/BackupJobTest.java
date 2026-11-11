package org.artifactory.backup;

import com.google.common.collect.ImmutableMap;
import org.artifactory.schedule.JobName;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Map;

import static org.artifactory.schedule.Task.JOB_NAME;
import static org.mockito.Mockito.spy;

/**
 * @author Omri Ziv
 */
public class BackupJobTest extends ArtifactoryHomeBoundTest {

    @Test
    public void testLockName() {
        BackupJob backupJob = spy(new BackupJob());
        Task task = TaskUtils.createRepeatingTask(BackupJob.class, 10L, 10L);
        Map<String, Object> map = ImmutableMap.of(BackupJob.BACKUP_KEY, "backup-key", JOB_NAME, JobName.BACKUP);
        map.forEach(task::addAttribute);

        ReflectionTestUtils.setField(backupJob, "activeTask", task);

        Assert.assertEquals(backupJob.getCommandLockKey(), JobName.BACKUP.asText() + "_backup-key");
    }

}
