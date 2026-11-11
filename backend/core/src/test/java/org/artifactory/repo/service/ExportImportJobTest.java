package org.artifactory.repo.service;

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
@Test
public class ExportImportJobTest extends ArtifactoryHomeBoundTest {

    public void testExportLockName() {
        Map<String, Object> map = ImmutableMap.of(Task.REPO_KEY, "key", JOB_NAME, JobName.EXPORT);
        Task task = TaskUtils.createManualTask(ExportJob.class, 10L);
        map.forEach(task::addAttribute);
        ExportJob exportJob = spy(ExportJob.class);
        ReflectionTestUtils.setField(exportJob, "activeTask", task);
        Assert.assertEquals(exportJob.getCommandLockKey(), JobName.EXPORT.asText() + "_key");
    }
}
