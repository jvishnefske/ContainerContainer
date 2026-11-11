/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2023 JFrog Ltd.
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

package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PAUSING;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.SKIPPED;
import static org.testng.Assert.assertFalse;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Tests the {@link org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao}.
 *
 * @author Mor Merhav
 */
@Test
public class QuartzTaskHistoryDaoTest extends DbBaseTest {

    @Autowired
    private QuartzTaskHistoryDao quartzTaskHistoryDao;

    @AfterMethod
    public void cleanUp() throws SQLException {
        quartzTaskHistoryDao.deleteAll();
    }

    @Test
    public void insert() throws SQLException {
        QuartzTaskHistoryRecord taskHistoryRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalId1")
                .taskType("taskType1")
                .status(IN_PROGRESS)
                .statusMessage("statusMsg")
                .taskId("taskId")
                .serverId("serverId")
                .manualTrigger(true)
                .username("username")
                .build();
        int runId = quartzTaskHistoryDao.insert(taskHistoryRecord);
        QuartzTaskHistoryRecord record = quartzTaskHistoryDao.get(runId);
        assertNotNull(record);
        assertEquals("externalId1", record.externalId());
        assertEquals("taskType1", record.taskType());
        assertEquals(IN_PROGRESS, record.status());
        assertEquals("statusMsg", record.statusMessage());
        assertTrue(record.lastModifiedDate() > 0);
        assertTrue(record.startedDate() > 0);
        assertEquals(-1L, record.finishedDate());
        assertFalse(record.deleted());
        assertEquals("taskId", record.taskId());
        assertEquals("serverId", record.serverId());
        assertTrue(record.manualTrigger());
        assertEquals("username", record.username());
    }

    @Test(dataProvider = "partialUpdateProvider")
    public void partialUpdate(long lastModifiedDate, long startedDate, long finishedDate, Status status,
            String statusMsg) throws SQLException {
        QuartzTaskHistoryRecord taskHistoryRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalId1")
                .taskType("taskType1")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .statusMessage("beforeStatusMessage")
                .serverId("serverId")
                .manualTrigger(true)
                .username("username")
                .build();
        int runId = quartzTaskHistoryDao.insert(taskHistoryRecord);
        QuartzTaskHistoryRecord recordBefore = quartzTaskHistoryDao.get(runId);

        assertNotNull(recordBefore);
        long lastModifiedDateBefore = recordBefore.lastModifiedDate();

        Supplier<Long> timeSupplier = () -> lastModifiedDateBefore + 1;
        ReflectionTestUtils.setField(quartzTaskHistoryDao, "timeSupplier", timeSupplier);

        QuartzTaskHistoryRecord partialUpdateRecord = QuartzTaskHistoryRecord.builder()
                .runId(runId)
                .lastModifiedDate(lastModifiedDate)
                .startedDate(startedDate)
                .finishedDate(finishedDate)
                .status(status)
                .statusMessage(statusMsg)
                .build();
        assertTrue(quartzTaskHistoryDao.update(partialUpdateRecord));
        QuartzTaskHistoryRecord recordAfter = quartzTaskHistoryDao.get(runId);
        assertNotNull(recordAfter);
        long lastModifiedDateAfter = recordAfter.lastModifiedDate();
        assertTrue(recordAfter.startedDate() > 0);
        assertTrue(lastModifiedDateAfter > lastModifiedDateBefore);
        assertTrue(lastModifiedDateAfter > recordAfter.startedDate());

        if (partialUpdateRecord.finishedDate() != -1) {
            assertEquals(recordAfter.finishedDate(), partialUpdateRecord.finishedDate());
        } else {
            assertEquals(recordBefore.finishedDate(), recordAfter.finishedDate());
        }
        if (partialUpdateRecord.status() != null) {
            assertEquals(recordAfter.status(), partialUpdateRecord.status());
        } else {
            assertEquals(recordAfter.status(), recordBefore.status());
        }
        if (partialUpdateRecord.statusMessage() != null) {
            assertEquals(recordAfter.statusMessage(), partialUpdateRecord.statusMessage());
        } else {
            assertEquals(recordAfter.statusMessage(), recordBefore.statusMessage());
        }
    }

    @Test(dataProvider = "statusesProvider")
    public void updateStatus(Status status) throws SQLException {
        QuartzTaskHistoryRecord taskHistoryRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalId1")
                .taskType("taskType1")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .serverId("serverId")
                .manualTrigger(true)
                .username("username")
                .build();
        int runId = quartzTaskHistoryDao.insert(taskHistoryRecord);
        QuartzTaskHistoryRecord recordBefore = quartzTaskHistoryDao.get(runId);

        assertNotNull(recordBefore);
        long lastModifiedDateBefore = recordBefore.lastModifiedDate();
        Supplier<Long> timeSupplier = () -> lastModifiedDateBefore + 1;
        ReflectionTestUtils.setField(quartzTaskHistoryDao, "timeSupplier", timeSupplier);
        assertTrue(quartzTaskHistoryDao.updateStatus(runId, status));

        QuartzTaskHistoryRecord recordAfter = quartzTaskHistoryDao.get(runId);
        assertNotNull(recordAfter);
        long lastModifiedDateAfter = recordAfter.lastModifiedDate();
        assertTrue(lastModifiedDateAfter > lastModifiedDateBefore);
        assertTrue(lastModifiedDateAfter > recordAfter.startedDate());
        assertEquals(status, recordAfter.status());
        assertFalse(recordAfter.deleted());
        assertEquals("taskId", recordAfter.taskId());
        assertEquals("serverId", recordAfter.serverId());
        assertTrue(recordAfter.manualTrigger());
        assertEquals("username", recordAfter.username());
    }

    @Test
    public void updateStatusWithMessage() throws SQLException {
        QuartzTaskHistoryRecord taskHistoryRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalId1")
                .taskType("taskType1")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .serverId("serverId")
                .manualTrigger(false)
                .username("username")
                .build();
        int runId = quartzTaskHistoryDao.insert(taskHistoryRecord);
        QuartzTaskHistoryRecord recordBefore = quartzTaskHistoryDao.get(runId);
        assertNotNull(recordBefore);
        long lastModifiedDateBefore = recordBefore.lastModifiedDate();
        Supplier<Long> timeSupplier = () -> lastModifiedDateBefore + 1;
        ReflectionTestUtils.setField(quartzTaskHistoryDao, "timeSupplier", timeSupplier);
        assertTrue(quartzTaskHistoryDao.updateStatus(runId, COMPLETED, "newStatusMsg"));

        QuartzTaskHistoryRecord recordAfter = quartzTaskHistoryDao.get(runId);
        assertNotNull(recordAfter);
        long lastModifiedDateAfter = recordAfter.lastModifiedDate();
        assertEquals("newStatusMsg", recordAfter.statusMessage());
        assertEquals(COMPLETED, recordAfter.status());
        assertTrue(lastModifiedDateAfter > lastModifiedDateBefore);
        assertTrue(lastModifiedDateAfter > recordAfter.startedDate());
        assertFalse(recordAfter.deleted());
        assertEquals("taskId", recordAfter.taskId());
        assertEquals("serverId", recordAfter.serverId());
        assertFalse(recordAfter.manualTrigger());
        assertEquals("username", recordAfter.username());
    }

    @Test
    public void hardDelete() throws SQLException {
        QuartzTaskHistoryRecord taskHistoryRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalId1")
                .taskType("taskType1")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .serverId("serverId")
                .manualTrigger(true)
                .username("username")
                .build();
        int runId = quartzTaskHistoryDao.insert(taskHistoryRecord);
        assertNotNull(quartzTaskHistoryDao.get(runId));
        assertTrue(quartzTaskHistoryDao.hardDelete(runId));
        assertNull(quartzTaskHistoryDao.get(runId));
    }

    @Test
    public void softDelete() throws SQLException {
        QuartzTaskHistoryRecord taskHistoryRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalId1")
                .taskType("taskType1")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .serverId("serverId")
                .manualTrigger(true)
                .username("username")
                .build();
        int runId = quartzTaskHistoryDao.insert(taskHistoryRecord);
        QuartzTaskHistoryRecord recordBefore = quartzTaskHistoryDao.get(runId);
        assertNotNull(recordBefore);
        long lastModifiedDateBefore = recordBefore.lastModifiedDate();
        Supplier<Long> timeSupplier = () -> lastModifiedDateBefore + 1;
        ReflectionTestUtils.setField(quartzTaskHistoryDao, "timeSupplier", timeSupplier);
        assertTrue(quartzTaskHistoryDao.softDelete(runId));
        QuartzTaskHistoryRecord recordAfter = quartzTaskHistoryDao.get(runId);
        assertNotNull(recordAfter);
        assertTrue(recordAfter.deleted());
        assertTrue(recordAfter.lastModifiedDate() > lastModifiedDateBefore);
    }

    @Test
    public void getAllNonDeleted() throws SQLException {
        QuartzTaskHistoryRecord taskHistoryRecord1 =
                new QuartzTaskHistoryRecord("externalId1", "taskType1", IN_PROGRESS, "taskId1", "serverId", true,
                        "username");
        int runId = quartzTaskHistoryDao.insert(taskHistoryRecord1);
        QuartzTaskHistoryRecord taskHistoryRecord2 =
                new QuartzTaskHistoryRecord("externalId2", "taskType2", IN_PROGRESS, "taskId2", "serverId", true,
                        "username");
        quartzTaskHistoryDao.insert(taskHistoryRecord2);
        QuartzTaskHistoryRecord taskHistoryRecord3 =
                new QuartzTaskHistoryRecord("externalId3", "taskType3", IN_PROGRESS, "taskId3", "serverId", true,
                        "username");
        int runId3 = quartzTaskHistoryDao.insert(taskHistoryRecord3);
        QuartzTaskHistoryRecord taskHistoryRecord4 =
                new QuartzTaskHistoryRecord("externalId4", "taskType4", IN_PROGRESS, "taskId4", "serverId", true,
                        "username");
        quartzTaskHistoryDao.insert(taskHistoryRecord4);

        assertTrue(quartzTaskHistoryDao.softDelete(runId));
        assertTrue(quartzTaskHistoryDao.softDelete(runId3));

        Set<QuartzTaskHistoryRecord> allNonDeleted = quartzTaskHistoryDao.getAllNonDeleted();
        assertEquals(allNonDeleted.size(), 2);
        assertTrue(allNonDeleted.stream()
                .anyMatch(quartzTaskHistoryRecord -> quartzTaskHistoryRecord.externalId().equals("externalId2")));
        assertTrue(allNonDeleted.stream()
                .anyMatch(quartzTaskHistoryRecord -> quartzTaskHistoryRecord.externalId().equals("externalId4")));
    }

    @Test
    public void findByExternalIdAndStatusIsOneOf() throws SQLException {
        QuartzTaskHistoryRecord record = QuartzTaskHistoryRecord.builder()
                .externalId("externalID")
                .taskType("taskType")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .serverId("serverId")
                .username("username")
                .build();
        QuartzTaskHistoryRecord deletedRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalID")
                .taskType("taskType")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .serverId("serverId")
                .deleted(true)
                .username("username")
                .build();
        QuartzTaskHistoryRecord skippedRecord = QuartzTaskHistoryRecord.builder()
                .externalId("externalID")
                .taskType("taskType")
                .status(SKIPPED)
                .taskId("taskId")
                .serverId("serverId")
                .username("username")
                .build();
        QuartzTaskHistoryRecord otherRecord = QuartzTaskHistoryRecord.builder()
                .externalId("otherExternalID")
                .taskType("taskType")
                .status(IN_PROGRESS)
                .taskId("taskId")
                .serverId("serverId")
                .username("username")
                .build();

        quartzTaskHistoryDao.insert(record);
        quartzTaskHistoryDao.insert(deletedRecord);
        quartzTaskHistoryDao.insert(skippedRecord);
        quartzTaskHistoryDao.insert(otherRecord);

        QuartzTaskHistoryRecord foundRecord = quartzTaskHistoryDao.findByExternalIdAndStatusIsOneOf("externalID",
                Set.of(IN_PROGRESS, PAUSING));

        assertNotNull(foundRecord);
        assertEquals(foundRecord.externalId(), "externalID");
        assertEquals(foundRecord.status(), IN_PROGRESS);
    }

    @Test(dataProvider = "statusesProvider")
    public void updateStatusWhereStatus(Status status) throws SQLException {
        QuartzTaskHistoryRecord record1 = generateRecord().toBuilder()
                .status(generateRandomStatusExcept(status))
                .build();
        QuartzTaskHistoryRecord record2 = generateRecord().toBuilder()
                .status(record1.status())
                .build();

        int runId1 = quartzTaskHistoryDao.insert(record1);
        int runId2 = quartzTaskHistoryDao.insert(record2);

        int numUpdated = quartzTaskHistoryDao.updateToStatusWhereStatus(status, record1.status());
        assertEquals(2, numUpdated);

        QuartzTaskHistoryRecord updatedRecord1 = quartzTaskHistoryDao.get(runId1);
        QuartzTaskHistoryRecord updatedRecord2 = quartzTaskHistoryDao.get(runId2);

        assertNotNull(updatedRecord1);
        assertNotNull(updatedRecord2);

        assertEquals(status, updatedRecord1.status());
        assertEquals(status, updatedRecord2.status());
    }

    @Test(dataProvider = "statusesProvider")
    public void updateToStatusWhereInStatusesAndLastModifiedBefore(Status status) throws SQLException {
        updateToStatusWhereInStatusesAndLastModifiedBefore(status, Status.getRunningStatuses());
        quartzTaskHistoryDao.deleteAll();
        updateToStatusWhereInStatusesAndLastModifiedBefore(status, Status.getCompletedStatuses());
        quartzTaskHistoryDao.deleteAll();
        updateToStatusWhereInStatusesAndLastModifiedBefore(status, Status.getFinalStatuses());
    }

    @Test
    public void findByStatuses() throws SQLException {
        Status[] statuses = Status.values();
        for (Status status : statuses) {
            quartzTaskHistoryDao.insert(generateRecord().toBuilder()
                    .status(status)
                    .build());
        }

        Set<Status> runningStatuses = Status.getRunningStatuses();
        Set<QuartzTaskHistoryRecord> runningTasks = quartzTaskHistoryDao.findByStatuses(runningStatuses);
        assertEquals(runningStatuses.size(), runningTasks.size());

        Set<Status> completedStatuses = Status.getCompletedStatuses();
        Set<QuartzTaskHistoryRecord> completedTasks = quartzTaskHistoryDao.findByStatuses(completedStatuses);
        assertEquals(completedStatuses.size(), completedTasks.size());

        Set<Status> finalStatuses = Status.getFinalStatuses();
        Set<QuartzTaskHistoryRecord> finalizedTasks = quartzTaskHistoryDao.findByStatuses(finalStatuses);
        assertEquals(finalStatuses.size(), finalizedTasks.size());
    }

    @Test(dataProvider = "statusesProvider")
    public void updateStatusWhereRunIdIn(Status status) throws SQLException {
        QuartzTaskHistoryRecord record1 = generateRecord().toBuilder()
                .status(generateRandomStatusExcept(status))
                .build();
        QuartzTaskHistoryRecord record2 = generateRecord().toBuilder()
                .status(generateRandomStatusExcept(status))
                .build();

        int runId1 = quartzTaskHistoryDao.insert(record1);
        int runId2 = quartzTaskHistoryDao.insert(record2);

        int numUpdated = quartzTaskHistoryDao.updateStatusWhereRunIdIn(status, Set.of(runId1, runId2));
        assertEquals(2, numUpdated);

        QuartzTaskHistoryRecord updatedRecord1 = quartzTaskHistoryDao.get(runId1);
        assertNotNull(updatedRecord1);
        assertEquals(status, updatedRecord1.status());

        QuartzTaskHistoryRecord updatedRecord2 = quartzTaskHistoryDao.get(runId2);
        assertNotNull(updatedRecord2);
        assertEquals(status, updatedRecord2.status());
    }

    @DataProvider
    public Object[][] statusesProvider() {
        Status[] values = Status.values();
        Object[][] objects = new Object[values.length][1];

        for (int i = 0; i < values.length; i++) {
            objects[i][0] = values[i];
        }
        return objects;
    }

    @DataProvider
    public static Object[][] partialUpdateProvider() {
        long startedDate = Instant.now().plusSeconds(1000).toEpochMilli();
        long finishedDate = startedDate + 1;
        long lastModifiedDate = startedDate + 2; // always provide last modified date
        return new Object[][] {
                // last modified date, started date, finished date, status, status message
                {lastModifiedDate, -1, -1, null, null},
                {lastModifiedDate, startedDate, finishedDate, getRandomStatus(), "statusMsg"},
                {lastModifiedDate, startedDate, -1, null, null},
                {lastModifiedDate, startedDate, finishedDate, null, null},
                {lastModifiedDate, startedDate, finishedDate, getRandomStatus(), null},
                {lastModifiedDate, startedDate, -1, null, "statusMsg"},
                {lastModifiedDate, startedDate, -1, getRandomStatus(), "statusMsg"},
                {lastModifiedDate, startedDate, -1, getRandomStatus(), null},
                {lastModifiedDate, -1, finishedDate, null, null},
                {lastModifiedDate, -1, finishedDate, getRandomStatus(), null},
                {lastModifiedDate, -1, finishedDate, getRandomStatus(), "statusMsg"},
                {lastModifiedDate, -1, finishedDate, null, "statusMsg"},
                {lastModifiedDate, -1, -1, getRandomStatus(), null},
                {lastModifiedDate, -1, -1, getRandomStatus(), "statusMsg"},
                {lastModifiedDate, -1, -1, getRandomStatus(), null},
                {lastModifiedDate, -1, -1, null, "statusMsg"}
        };
    }

    private void updateToStatusWhereInStatusesAndLastModifiedBefore(Status status, Set<Status> runningStatuses)
            throws SQLException {
        Set<Integer> runIds = new HashSet<>();
        for (Status runningStatus : runningStatuses) {
            runIds.add(quartzTaskHistoryDao.insert(generateRecord()
                    .toBuilder()
                    .status(runningStatus)
                    .lastModifiedDate(5L)
                    .build()));
        }

        int numUpdated =
                quartzTaskHistoryDao.updateToStatusWhereInStatusesAndLastModifiedBefore(status, runningStatuses, 6L);

        assertEquals(runningStatuses.size(), numUpdated);
        for (Integer runId : runIds) {
            QuartzTaskHistoryRecord record = quartzTaskHistoryDao.get(runId);
            assertNotNull(record);
            assertEquals(status, record.status());
        }
    }

    private static QuartzTaskHistoryRecord generateRecord() {
        return QuartzTaskHistoryRecord.builder()
                .status(getRandomStatus())
                .taskType("taskType")
                .serverId("serverId")
                .taskId("taskId")
                .username("username")
                .lastModifiedDate(2L)
                .startedDate(1L)
                .build();
    }

    private static Status getRandomStatus() {
        return Status.values()[Math.abs(new Random().nextInt()) % Status.values().length];
    }

    private static Status generateRandomStatusExcept(Status status) {
        Status randomStatus;
        do {
            randomStatus = getRandomStatus();
        } while (randomStatus != status);
        return randomStatus;
    }
}