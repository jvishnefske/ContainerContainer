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

package org.artifactory.schedule;

import org.artifactory.backup.BackupJob;
import org.artifactory.repo.cleanup.IntegrationCleanupJob;
import org.artifactory.sh.cleanup.job.BinaryStoreGarbageCollectorJob;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for {@link TaskUtils}.
 *
 * @author Yossi Shaul
 */
@Test
public class TaskUtilsTest extends ArtifactoryHomeBoundTest {

    public void taskManualJobDetailsPopulation() {
        InternalTask t = (InternalTask) TaskUtils.createManualTask(BackupJob.class, 0);
        assertTrue(t.getTaskId().startsWith("artifactory.BackupJob"));
        assertEquals(t.getLastStarted(), 0);
        assertEquals(t.getType(), BackupJob.class);
        assertTrue(t.isManuallyActivated());
        assertFalse(t.isRunning());
        JobCommand jobCommand = BackupJob.class.getAnnotation(JobCommand.class);
        assertEquals(t.isSingleton(), jobCommand.singleton());
        assertEquals(t.getDescription(), jobCommand.description() + " (manual trigger)");
    }

    public void taskRepeatingJobDetailsPopulation() {
        InternalTask t = (InternalTask) TaskUtils.createRepeatingTask(IntegrationCleanupJob.class, 3, 4);
        t.addAttribute("custom", "attribute");
        assertTrue(t.getTaskId().startsWith("artifactory.IntegrationCleanupJob"));
        assertEquals(t.getLastStarted(), 0);
        assertEquals(t.getType(), IntegrationCleanupJob.class);
        assertFalse(t.isManuallyActivated());
        assertFalse(t.isRunning());
        JobCommand jobCommand = IntegrationCleanupJob.class.getAnnotation(JobCommand.class);
        assertEquals(t.isSingleton(), jobCommand.singleton());
        assertEquals(t.getDescription(), jobCommand.description());
    }

    public void taskCronJobDetailsPopulation() {
        InternalTask t = (InternalTask) TaskUtils.createCronTask(BinaryStoreGarbageCollectorJob.class, "0 0 /6 * * ?");
        assertTrue(t.getTaskId().startsWith("artifactory.BinaryStoreGarbageCollectorJob"));
        assertEquals(t.getLastStarted(), 0);
        assertEquals(t.getType(), BinaryStoreGarbageCollectorJob.class);
        assertFalse(t.isManuallyActivated());
        assertFalse(t.isRunning());
        JobCommand jobCommand = BinaryStoreGarbageCollectorJob.class.getAnnotation(JobCommand.class);
        assertEquals(t.isSingleton(), jobCommand.singleton());
        assertEquals(t.getDescription(), jobCommand.description());
    }

    public void taskDescriptionOverride() {
        Task t = TaskUtils.createManualTask(BinaryStoreGarbageCollectorJob.class, 2, "Another change");
        assertEquals(t.getDescription(), "Another change (manual trigger)");

        t = TaskUtils.createCronTask(BinaryStoreGarbageCollectorJob.class, "0 0 /6 * * ?",
                "Change the default");
        assertEquals(t.getDescription(), "Change the default");

        t = TaskUtils.createRepeatingTask(BinaryStoreGarbageCollectorJob.class, 1, 2,
                "My description");
        assertEquals(t.getDescription(), "My description");
    }

    @Test
    public void testExtractAttributeValues() {
        Map<String, Object> jobDataMap = Map.of("key1", "value1", "key2", "value2", "key3", "value3");
        Set<String> keysToExtractBy = Set.of("key1", "key3");
        List<Object> attributeValues =
                TaskUtils.extractAttributeValues(jobDataMap, keysToExtractBy.toArray(new String[] {}));
        assertEquals(attributeValues.size(), 2);
        assertTrue(attributeValues.contains("value1"));
        assertTrue(attributeValues.contains("value3"));
    }
}