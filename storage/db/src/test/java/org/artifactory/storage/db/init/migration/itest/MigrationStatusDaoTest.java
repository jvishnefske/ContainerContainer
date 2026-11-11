package org.artifactory.storage.db.init.migration.itest;

import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.init.migration.entity.DbMigrationStatus;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.fest.util.Collections;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.JsonUtils;
import org.jfrog.common.StreamSupportUtils;
import org.jfrog.commons.continues.util.Direction;
import org.jfrog.storage.DbType;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.fest.assertions.Assertions.assertThat;
import static org.jfrog.commons.continues.util.Direction.ASC;
import static org.jfrog.commons.continues.util.Direction.DESC;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 * @author Omri Ziv
 */
@Test
public class MigrationStatusDaoTest extends DbBaseTest {
    @Autowired
    private MigrationStatusDao dao;
    private final Long TIME_2020 = 1591599149000L;

    public void testInsertMigration() throws SQLException {
        final long started = ClockUtils.epochMillis();
        dao.insertMigration(new DbMigrationStatus("metadata-service-migration", started, 0, null));
        Optional<DbMigrationStatus> migration = dao.findMigrationById("metadata-service-migration");
        assertTrue(migration.isPresent());
        assertEquals(migration.get().getIdentifier(), "metadata-service-migration");
        assertEquals(migration.get().getStarted(), started);
        assertEquals(migration.get().getFinished(), 0);
    }

    public void testInsertMigrationWithInfoBlob() throws SQLException {
        final long started = ClockUtils.epochMillis();
        dao.insertMigration(new DbMigrationStatus("metadata-service-migration-2", started, 0,
                "{\"migrationTargetNodeId\":, \"123456789\", \"currentEventLogTimestamp\": \"123456\"}".getBytes()));
        Optional<DbMigrationStatus> migration = dao.findMigrationById("metadata-service-migration-2");
        assertTrue(migration.isPresent());
        assertEquals(migration.get().getIdentifier(), "metadata-service-migration-2");
        assertEquals(migration.get().getStarted(), started);
        assertEquals(migration.get().getFinished(), 0);
        assertNotNull(migration.get().getMigrationInfoBlob());
        assertEquals(new String(migration.get().getMigrationInfoBlob()),
                "{\"migrationTargetNodeId\":, \"123456789\", \"currentEventLogTimestamp\": \"123456\"}");
    }

    @Test(dependsOnMethods = "testInsertMigration", expectedExceptions = SQLException.class)
    public void testUniqueConstraint() throws SQLException {
        dao.insertMigration(new DbMigrationStatus("metadata-service-migration", 0, 0, null));
    }

    @Test(dependsOnMethods = "testInsertMigration")
    public void testUpdateMigration() throws SQLException {
        long finished = ClockUtils.epochMillis() + 100;
        dao.updateFinishTimeById("metadata-service-migration", finished);
        Optional<DbMigrationStatus> migration = dao.findMigrationById("metadata-service-migration");
        assertTrue(migration.isPresent());
        assertEquals(migration.get().getIdentifier(), "metadata-service-migration");
        assertEquals(migration.get().getFinished(), finished);
    }

    @Test
    public void testFindAllUnfinishedByPrefix() throws SQLException {
        try {
            final long started = ClockUtils.epochMillis();
            DbMigrationStatus migrationStatus1 =
                    new DbMigrationStatus("retention_restore_1", started, 0, new byte[] {});
            DbMigrationStatus migrationStatus2 =
                    new DbMigrationStatus("retention_restore_2", started, 0, new byte[] {});
            DbMigrationStatus migrationStatus3 =
                    new DbMigrationStatus("retention_restore_3", started, 0, new byte[] {});
            DbMigrationStatus migrationStatus4 =
                    new DbMigrationStatus("retention_restore_4", started, started + 1, new byte[] {});

            dao.insertMigration(migrationStatus1);
            dao.insertMigration(migrationStatus2);
            dao.insertMigration(migrationStatus3);
            dao.insertMigration(migrationStatus4);

            List<DbMigrationStatus> actualStatuses =
                    Lists.newArrayList(dao.findAllUnfinishedByPrefix("retention_restore_"));
            assertNotNull(actualStatuses);
            assertEquals(actualStatuses.size(), 3);

            List<DbMigrationStatus> expectedStatuses =
                    Lists.newArrayList(migrationStatus1, migrationStatus2, migrationStatus3);

            expectedStatuses.sort(Comparator.comparing(DbMigrationStatus::getIdentifier));
            actualStatuses.sort(Comparator.comparing(DbMigrationStatus::getIdentifier));

            for (int i = 0; i < expectedStatuses.size(); i++) {
                DbMigrationStatus expected = expectedStatuses.get(i);
                DbMigrationStatus actual = actualStatuses.get(i);
                if (dbProperties.getDbType() == DbType.ORACLE) {
                    expected.setMigrationInfoBlob(null);
                }
                Assert.assertEquals(actual, expected);
            }

        } finally {
            int deleted = dao.delete("retention_restore_1");
            assertEquals(deleted, 1);
            deleted = dao.delete("retention_restore_2");
            assertEquals(deleted, 1);
            deleted = dao.delete("retention_restore_3");
            assertEquals(deleted, 1);
            deleted = dao.delete("retention_restore_4");
            assertEquals(deleted, 1);
        }
    }

    @Test
    public void testDelete() throws SQLException {
        final long started = ClockUtils.epochMillis();
        dao.insertMigration(new DbMigrationStatus("marker-migration-to-delete", started, 0, null));
        int deleted = dao.delete("marker-migration-to-delete");
        assertEquals(deleted, 1);
    }

    @Test
    public void testNotExistedDelete() throws SQLException {
        final long started = ClockUtils.epochMillis();
        try {
            dao.insertMigration(new DbMigrationStatus("marker-migration-to-delete", started, 0, null));
            int deleted = dao.delete("not-existed");
            assertEquals(deleted, 0);
        } finally {
            // Clean
            int deleted = dao.delete("marker-migration-to-delete");
            assertEquals(deleted, 1);
        }
    }

    @Test
    public void testFindAllUnfinishedIn() throws SQLException {
        final long started = ClockUtils.epochMillis();
        try {
            dao.insertMigration(new DbMigrationStatus("marker-migration-1", started, 0, null));
            dao.insertMigration(new DbMigrationStatus("marker-migration-2", started, started + 6, null));
            dao.insertMigration(new DbMigrationStatus("marker-migration-3", started, 0, null));
            dao.insertMigration(new DbMigrationStatus("marker-migration-4", started, 0, null));
            Collection<DbMigrationStatus> dbMigrationStatuses =
                    dao.findAllUnfinishedIn(Collections.list("marker-migration-1", "marker-migration-2",
                            "marker-migration-3"));
            assertEquals(dbMigrationStatuses.size(), 2);
            List<String> names = StreamSupportUtils.stream(dbMigrationStatuses)
                    .map(DbMigrationStatus::getIdentifier)
                    .collect(Collectors.toList());
            assertThat(names).containsExactly("marker-migration-1", "marker-migration-3");
        } finally {
            // Clean
            dao.delete("marker-migration-1");
            dao.delete("marker-migration-2");
            dao.delete("marker-migration-3");
            dao.delete("marker-migration-4");
        }
    }

    @Test(dataProvider = "directions")
    public void testFindMigrationByIdPrefix(Direction direction) throws SQLException {
        final long started = ClockUtils.epochMillis();
        try {
            dao.insertMigration(new DbMigrationStatus("abc-1", started + 1, 0, null));
            dao.insertMigration(new DbMigrationStatus("abc-2", started + 2, started + 6, null));
            dao.insertMigration(new DbMigrationStatus("abc-3", started + 3, 0, null));
            dao.insertMigration(new DbMigrationStatus("ccc-5", started + 4, 0, null));
            dao.insertMigration(new DbMigrationStatus("abc-4", started + 5, 0, null));
            Collection<DbMigrationStatus> dbMigrationStatuses =
                    dao.findMigrationsByIdPrefixBetweenDates("abc", null, direction,
                            null, null);
            assertEquals(dbMigrationStatuses.size(), 4);
            List<String> names = StreamSupportUtils.stream(dbMigrationStatuses)
                    .map(DbMigrationStatus::getIdentifier)
                    .collect(Collectors.toList());
            if (ASC == direction) {
                assertThat(names).containsExactly("abc-1", "abc-2", "abc-3", "abc-4");
            } else {
                assertThat(names).containsExactly("abc-4", "abc-3", "abc-2", "abc-1");
            }
        } finally {
            // Clean
            dao.delete("abc-1");
            dao.delete("abc-2");
            dao.delete("abc-3");
            dao.delete("abc-4");
            dao.delete("ccc-5");
        }
    }

    @Test(dataProvider = "betweenDates")
    public void testFindMigrationBetweenDates(Long from, Long to, String searchStr, List<String> expected)
            throws SQLException {
        try {
            dao.insertMigration(new DbMigrationStatus("abc-1", TIME_2020 - TimeUnit.DAYS.toMillis(60),
                    0, null));
            dao.insertMigration(new DbMigrationStatus("abc-2", TIME_2020 - TimeUnit.DAYS.toMillis(30),
                    TIME_2020 - TimeUnit.DAYS.toMillis(30) + 5, null));
            dao.insertMigration(new DbMigrationStatus("abc-3", TIME_2020, 0, null));
            dao.insertMigration(new DbMigrationStatus("abc-4", TIME_2020 + TimeUnit.DAYS.toMillis(30),
                    TIME_2020 + TimeUnit.DAYS.toMillis(30) + 15, null));
            dao.insertMigration(new DbMigrationStatus("ccc-5", TIME_2020 + TimeUnit.DAYS.toMillis(60),
                    0, null));
            Collection<DbMigrationStatus> dbMigrationStatuses =
                    dao.findMigrationsByIdPrefixBetweenDates(searchStr, null, DESC,
                            from, to);
            assertEquals(dbMigrationStatuses.size(), expected.size());
            List<String> names = StreamSupportUtils.stream(dbMigrationStatuses)
                    .map(DbMigrationStatus::getIdentifier)
                    .collect(Collectors.toList());
            assertThat(names).containsOnly(expected.toArray());
        } finally {
            // Clean
            dao.delete("abc-1");
            dao.delete("abc-2");
            dao.delete("abc-3");
            dao.delete("abc-4");
            dao.delete("ccc-5");
        }
    }

    @Test(dataProvider = "betweenDatesWithSeparator")
    public void testFindExistedMigrationBetweenDatesWithSeparator(Long startSearchDate, Long finishSearchDate)
            throws SQLException {
        String migrationKey = "migration_dd2_hello";
        long startDate = TIME_2020 - TimeUnit.DAYS.toMillis(60);
        long finishDate = TIME_2020 + TimeUnit.DAYS.toMillis(60);
        try {
            dao.insertMigration(new DbMigrationStatus(migrationKey, startDate, finishDate, null));

            String existedQuery = "migration_dd2";
            Collection<DbMigrationStatus> results =
                    dao.findMigrationsByIdPrefixBetweenDates(existedQuery, "_", DESC, startSearchDate,
                            finishSearchDate);
            assertEquals(results.size(), 1);
            String found = StreamSupportUtils.stream(results)
                    .map(DbMigrationStatus::getIdentifier)
                    .filter(id -> id.equals(migrationKey))
                    .findFirst()
                    .get();
            assertEquals(found, migrationKey);
        } finally {
            // clean
            dao.delete(migrationKey);
        }
    }

    @Test(dataProvider = "betweenDatesWithSeparator")
    public void testFindNonExistingMigrationBetweenDatesWithSeparator(Long startSearchDate, Long finishSearchDate)
            throws SQLException {
        String migrationKey = "migration_dd3_hello";
        long startDate = TIME_2020 - TimeUnit.DAYS.toMillis(60);
        long finishDate = TIME_2020 + TimeUnit.DAYS.toMillis(60);
        try {
            dao.insertMigration(new DbMigrationStatus(migrationKey, startDate, finishDate, null));

            String existedQuery = "migration_dd";
            Collection<DbMigrationStatus> results =
                    dao.findMigrationsByIdPrefixBetweenDates(existedQuery, "_", DESC, startSearchDate,
                            finishSearchDate);
            assertEquals(results.size(), 0);
        } finally {
            // clean
            dao.delete(migrationKey);
        }
    }

    @Test
    public void testFindAllByPrefix() throws SQLException {
        try {
            final long started = ClockUtils.epochMillis();
            DbMigrationStatus migrationStatus1 =
                    new DbMigrationStatus("retention_restore_1", started, 1, new byte[] {});
            DbMigrationStatus migrationStatus2 =
                    new DbMigrationStatus("retention_restore_2", started, 2, new byte[] {});
            DbMigrationStatus migrationStatus3 =
                    new DbMigrationStatus("retention_restore_3", started, 3, new byte[] {});
            DbMigrationStatus migrationStatus4 =
                    new DbMigrationStatus("retention_restore_4", started, 4, new byte[] {});

            dao.insertMigration(migrationStatus1);
            dao.insertMigration(migrationStatus2);
            dao.insertMigration(migrationStatus3);
            dao.insertMigration(migrationStatus4);

            List<DbMigrationStatus> actualStatuses =
                    Lists.newArrayList(dao.findFinishedStartedAfterDateByPrefix("retention_restore_", 2));
            assertNotNull(actualStatuses);
            assertEquals(actualStatuses.size(), 3);

            List<DbMigrationStatus> expectedStatuses =
                    Lists.newArrayList(migrationStatus2, migrationStatus3, migrationStatus4);

            expectedStatuses.sort(Comparator.comparing(DbMigrationStatus::getIdentifier));
            actualStatuses.sort(Comparator.comparing(DbMigrationStatus::getIdentifier));

            for (int i = 0; i < expectedStatuses.size(); i++) {
                DbMigrationStatus expected = expectedStatuses.get(i);
                DbMigrationStatus actual = actualStatuses.get(i);
                if (dbProperties.getDbType() == DbType.ORACLE) {
                    expected.setMigrationInfoBlob(null);
                }
                Assert.assertEquals(actual, expected);
            }

        } finally {
            int deleted = dao.delete("retention_restore_1");
            assertEquals(deleted, 1);
            deleted = dao.delete("retention_restore_2");
            assertEquals(deleted, 1);
            deleted = dao.delete("retention_restore_3");
            assertEquals(deleted, 1);
            deleted = dao.delete("retention_restore_4");
            assertEquals(deleted, 1);
        }
    }

    @Test
    public void testUpdateMigrationInfoBlob() throws SQLException {
        final long started = ClockUtils.epochMillis();
        String migrationId = "marker-migration-1";
        try {
            TestBlob testBlob = new TestBlob(1);
            dao.insertMigration(
                    new DbMigrationStatus(migrationId, started, 0, JsonUtils.getInstance().valueToByteArray(testBlob)));
            Optional<DbMigrationStatus> migrationById1 = dao.findMigrationById(migrationId);
            Assert.assertTrue(migrationById1.isPresent());
            DbMigrationStatus dbMigrationStatus = migrationById1.get();
            TestBlob returnedTestBlob =
                    JsonUtils.getInstance().readValue(dbMigrationStatus.getMigrationInfoBlob(), TestBlob.class);
            long num = returnedTestBlob.getNum();
            Assert.assertEquals(num, 1L);
            returnedTestBlob.setNum(num + 1L);
            dao.updateMigrationInfoBlob(new DbMigrationStatus(migrationId, started, 0,
                    JsonUtils.getInstance().valueToByteArray(returnedTestBlob)));
            Optional<DbMigrationStatus> migrationById2 = dao.findMigrationById(migrationId);
            Assert.assertTrue(migrationById2.isPresent());
            DbMigrationStatus dbMigrationStatus2 = migrationById2.get();
            TestBlob updatedTestBlob =
                    JsonUtils.getInstance().readValue(dbMigrationStatus2.getMigrationInfoBlob(), TestBlob.class);
            Assert.assertEquals(updatedTestBlob.getNum(), 2L);
        } finally {
            // Clean
            dao.delete(migrationId);
        }
    }

    @Test
    public void findStartedSinceContainsPrefix() throws SQLException {
        DateTime dateTime = DateTime.now();
        long timestampToSearch = timeMonthsAgo(4, dateTime);
        long olderTimestamp = timeMonthsAgo(5, dateTime);
        long newerTimestamp = timeMonthsAgo(3, dateTime);

        String olderKey = "marker-migration-older";
        String olderWithoutPrefixKey = "marker-prefix-older";
        String newerKey1 = "marker-migration-newer-1";
        String newerKey2 = "marker-migration-newer-2";
        dao.insertMigration(new DbMigrationStatus(olderKey, olderTimestamp, 0, null));
        dao.insertMigration(new DbMigrationStatus(olderWithoutPrefixKey, olderTimestamp, 0, null));
        dao.insertMigration(new DbMigrationStatus(newerKey1, newerTimestamp, 0, null));
        dao.insertMigration(new DbMigrationStatus(newerKey2, dateTime.getMillis(), 0, null));

        Collection<String> statusKeys =
                dao.findStartedBeforeDateContainsPrefix("migration", timestampToSearch);

        assertEquals(statusKeys.size(), 1);
        assertEquals(statusKeys.stream().findFirst().get(), olderKey);
    }

    @Data
    @AllArgsConstructor
    private static class TestBlob {
        private long num;
    }

    @DataProvider
    private Object[][] directions() {
        return new Object[][] {{ASC}, {DESC}};
    }

    @DataProvider
    private Object[][] betweenDates() {
        return new Object[][] {
                {TIME_2020, null, "abc", List.of("abc-3", "abc-4")},
                {TIME_2020 - 1L, null, "abc", List.of("abc-3", "abc-4")},
                {TIME_2020 + 1L, null, "abc", List.of("abc-4")},
                {null, null, "abc", List.of("abc-1", "abc-2", "abc-3", "abc-4")},
                {TIME_2020 - TimeUnit.DAYS.toMillis(300), TIME_2020 - TimeUnit.DAYS.toMillis(200), "abc",
                        java.util.Collections.emptyList()},
                {TIME_2020 - TimeUnit.DAYS.toMillis(300), TIME_2020 + TimeUnit.DAYS.toMillis(200), "abc",
                        List.of("abc-1", "abc-2", "abc-3", "abc-4")},
                {null, TIME_2020 + TimeUnit.DAYS.toMillis(200), "abc",
                        List.of("abc-1", "abc-2", "abc-3", "abc-4")},
                {null, TIME_2020, "abc",
                        List.of("abc-1", "abc-2", "abc-3")},
                {null, TIME_2020 - 1, "abc",
                        List.of("abc-1", "abc-2")},
        };
    }

    @DataProvider
    private Object[][] betweenDatesWithSeparator() {
        long startDate = TIME_2020 - TimeUnit.DAYS.toMillis(60) - 1;
        long finishDate = TIME_2020 + TimeUnit.DAYS.toMillis(60) + 1;
        return new Object[][] {
                {null, null},
                {startDate, null},
                {null, finishDate},
                {startDate, finishDate}
        };
    }

    private long timeMonthsAgo(int months, DateTime now) {
        org.joda.time.Period period = Period.months(months);
        return now.minus(period).getMillis();
    }
}
