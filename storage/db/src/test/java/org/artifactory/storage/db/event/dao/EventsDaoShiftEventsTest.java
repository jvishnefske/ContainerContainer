package org.artifactory.storage.db.event.dao;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class EventsDaoShiftEventsTest {

    private EventsDao eventsDao;
    private MeterRegistry meterRegistry;

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    @Mock
    private ArtifactoryHome artifactoryHome;
    @Mock
    private ArtifactorySystemProperties artifactorySystemProperties;
    @Mock
    private DbService dbService;
    @Mock
    private ResultSet resultSet;

    private final int eventsCount = (EventsDao.TMP_EVENTS_DELETE_BATCH_SIZE * 2) + 1;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryHome.bind(artifactoryHome);
        when(dbProperties.getDbType()).thenReturn(DbType.MYSQL);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.shiftEventsSqlLimit)).thenReturn(1000L);
        when(artifactorySystemProperties.getLongProperty(ConstantValues.shiftEventsCopyBulkSize)).thenReturn(100L);
        AtomicLong sequence = new AtomicLong(500);
        when(dbService.nextId()).thenAnswer(invocation -> sequence.incrementAndGet());
        when(jdbcHelper.executeSelect(any(String.class))).thenReturn(resultSet);
        AtomicLong rsSize = new AtomicLong(eventsCount);

        when(resultSet.next()).thenAnswer(rs -> rsSize.get() > 0);
        when(resultSet.getLong("timestamp")).thenReturn(System.currentTimeMillis());
        when(resultSet.getInt("event_type")).thenReturn(1);
        when(resultSet.getString("path")).thenReturn("repo1");
        when(resultSet.getString("hints")).thenReturn("hints");
        when(resultSet.getString("repo")).thenReturn("repo");
        when(resultSet.getLong("tmp_event_id")).thenAnswer(invocation -> rsSize.getAndDecrement());
        createEventsDao();
    }

    @AfterMethod
    private void teardown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    public void testDerbyEventIdSequence() throws Exception {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        createEventsDao();
        testShiftAndDeleteTmpEvents();
    }

    @Test
    public void testShiftAndDeleteTmpEvents() throws Exception {
        when(jdbcHelper.executeUpdate(startsWith("DELETE FROM node_events_tmp"), any(List.class)))
                .then(invocation -> invocation.getArgument(1, List.class).size());

        Set<String> repos = eventsDao.shiftAndDeleteTmpEvents(false);
        assertEquals(repos, Set.of("repo1"));

        int expectedPartitions = (int) Math.ceil((double) eventsCount / EventsDao.TMP_EVENTS_DELETE_BATCH_SIZE);

        if (DbType.DERBY.equals(dbProperties.getDbType())) {
            verify(dbService, times(eventsCount)).nextId();
        }
        verify(jdbcHelper, times(expectedPartitions))
                .executeBulkUpdate(startsWith("INSERT INTO node_events"), any());

        ArgumentCaptor<List<Long>> idsToDelete = ArgumentCaptor.forClass(List.class);
        verify(jdbcHelper, times(expectedPartitions))
                .executeUpdate(startsWith("DELETE FROM node_events_tmp"), idsToDelete.capture());

        assertEquals(idsToDelete.getAllValues().size(), expectedPartitions);

        Set<Long> deletedIds = idsToDelete.getAllValues().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        for (long id = eventsCount; id > 0; id--) {
            assertTrue(deletedIds.contains(id));
        }

        assertEquals(meterRegistry.get("pq.shift.events").tag("type", "copy").counter().count(), eventsCount);
        assertEquals(meterRegistry.get("pq.shift.events").tag("type", "delete").counter().count(), eventsCount);
        assertEquals(meterRegistry.get("pq.shift.events.duration").tag("type", "copy").timer().count(),1);
        assertEquals(meterRegistry.get("pq.shift.events.duration").tag("type", "delete").timer().count(), 1);
        assertEquals(meterRegistry.get("pq.shift.events.duration").tag("type", "fetch").timer().count(), 1);
    }

    @Test
    public void testPostgresShiftAndDeleteTmpEventsUsesTupleInDelete() throws Exception {
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        createEventsDao();
        mockTempEvents(List.of(
                tempEvent(1L, 2),
                tempEvent(2L, 2),
                tempEvent(3L, 8),
                tempEvent(4L, 14)
        ));
        // Capture the single tuple-IN DELETE: arg[0] is the SQL, the remaining varargs are the
        // (tmp_event_id, timestamp, partition-key) triples, so the deleted-row count is varargs / 3.
        String[] capturedSql = new String[1];
        List<Object> capturedParams = new ArrayList<>();
        when(jdbcHelper.executeUpdate(startsWith("DELETE FROM node_events_tmp"), any(Object[].class)))
                .then(invocation -> {
                    Object[] all = invocation.getArguments();
                    capturedSql[0] = (String) all[0];
                    capturedParams.clear();
                    capturedParams.addAll(List.of(all).subList(1, all.length));
                    return capturedParams.size() / 3;
                });

        Set<String> repos = eventsDao.shiftAndDeleteTmpEvents(false);

        assertEquals(repos, Set.of("repo1"));
        // The whole batch is deleted with a single tuple-IN statement, not one DELETE per partition key.
        verify(jdbcHelper, times(1))
                .executeUpdate(startsWith("DELETE FROM node_events_tmp"), any(Object[].class));

        assertTrue(capturedSql[0].startsWith(
                "DELETE FROM node_events_tmp WHERE (tmp_event_id, timestamp, (timestamp % (86400000) / 3600000)) IN "),
                "Unexpected delete SQL: " + capturedSql[0]);
        assertTrue(capturedSql[0].endsWith("IN ((?, ?, ?), (?, ?, ?), (?, ?, ?), (?, ?, ?))"),
                "Unexpected tuple placeholders: " + capturedSql[0]);

        assertEquals(capturedParams, List.of(
                1L, TimeUnit.HOURS.toMillis(2), 2,
                2L, TimeUnit.HOURS.toMillis(2), 2,
                3L, TimeUnit.HOURS.toMillis(8), 8,
                4L, TimeUnit.HOURS.toMillis(14), 14));

        assertEquals(meterRegistry.get("pq.shift.events").tag("type", "delete").counter().count(), 4);
    }

    @Test
    public void testPostgresPartitionKeyForBoundaryTimestamps() {
        assertEquals(EventsDao.partitionKeyFor(0L), 0);
        assertEquals(EventsDao.partitionKeyFor(TimeUnit.HOURS.toMillis(1) - 1), 0);
        assertEquals(EventsDao.partitionKeyFor(TimeUnit.HOURS.toMillis(1)), 1);
        assertEquals(EventsDao.partitionKeyFor(TimeUnit.HOURS.toMillis(6)), 6);
        assertEquals(EventsDao.partitionKeyFor(TimeUnit.HOURS.toMillis(23)), 23);
        assertEquals(EventsDao.partitionKeyFor(TimeUnit.DAYS.toMillis(1)), 0);
    }

    @Test
    public void testShiftAndDeleteTmpEventsWithRepo() throws Exception {
        Set<String> repos = eventsDao.shiftAndDeleteTmpEvents(true);
        assertEquals(repos, Set.of("repo1"));
        ArgumentCaptor<List<Long>> idsToDelete = ArgumentCaptor.forClass(List.class);

        verify(jdbcHelper, times(3))
                    .executeBulkUpdate(startsWith("INSERT INTO node_events"), any());
        verify(jdbcHelper, times(3))
                .executeUpdate(startsWith("DELETE FROM node_events_tmp"), idsToDelete.capture());

        assertEquals(idsToDelete.getAllValues().size(), 3);

        Set<Long> deletedIds = idsToDelete.getAllValues().stream()
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        for (long id = eventsCount; id > 0; id--) {
            assertTrue(deletedIds.contains(id));
        }
        assertEquals(meterRegistry.get("pq.shift.events").tag("type", "copy").counter().count(), eventsCount);
        assertEquals(meterRegistry.get("pq.shift.events").tag("type", "delete").counter().count(), 0);
        assertEquals(meterRegistry.get("pq.shift.events.duration").tag("type", "copy").timer().count(), 1);
        assertEquals(meterRegistry.get("pq.shift.events.duration").tag("type", "delete").timer().count(), 1);
        assertEquals(meterRegistry.get("pq.shift.events.duration").tag("type", "fetch").timer().count(), 1);
    }

    @Test
    public void testOracleHintEnabledForDeleteTempEvents() throws Exception {
        // Setup Oracle database type with hint enabled
        when(dbProperties.getDbType()).thenReturn(DbType.ORACLE);
        when(artifactorySystemProperties
                        .getBooleanProperty(ConstantValues.shiftEventsOracleDbUsePKeyForDeleteTempEvents))
                .thenReturn(true);
        when(jdbcHelper.executeUpdate(startsWith("DELETE FROM node_events_tmp"), any(Object[].class))).thenReturn(3);

        createEventsDao();

        Set<String> repos = eventsDao.shiftAndDeleteTmpEvents(false);

        // Verify that Oracle hint SQL was used
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(jdbcHelper, atLeastOnce())
                .executeUpdate(sqlCaptor.capture(), paramsCaptor.capture());

        // Check that at least one of the SQL statements contains the Oracle hint
        boolean foundOracleHint = sqlCaptor.getAllValues().stream()
                .anyMatch(sql -> sql.contains("/*+ INDEX(node_events_tmp node_events_tmp_pk) */"));

        assertTrue(foundOracleHint, "Oracle hint should be used when enabled");
        assertEquals(repos, Set.of("repo1"));
    }

    @DataProvider
    public Object[][] oracleHintNotUsedScenarios() {
        return new Object[][]{
                {DbType.ORACLE, false, false, "Oracle hint should not be used when disabled (default)"},
                {DbType.ORACLE, true, true, "Oracle hint be used when enabled"},
                {DbType.MYSQL, null, false, "Oracle hint should not be used for non-Oracle databases"}
        };
    }

    @Test(dataProvider = "oracleHintNotUsedScenarios")
    public void testOracleHintNotUsedForDeleteTempEvents(DbType dbType, Boolean hintEnabled, boolean expectedResult,
                    String expectedMessage) throws Exception {
        // Setup database type
        when(dbProperties.getDbType()).thenReturn(dbType);

        // Setup hint property only for Oracle databases
        if (dbType == DbType.ORACLE && hintEnabled != null) {
            when(artifactorySystemProperties
                            .getBooleanProperty(ConstantValues.shiftEventsOracleDbUsePKeyForDeleteTempEvents))
                    .thenReturn(hintEnabled);
        }

        when(jdbcHelper.executeUpdate(startsWith("DELETE FROM node_events_tmp"), any(Object[].class))).thenReturn(3);

        createEventsDao();

        Set<String> repos = eventsDao.shiftAndDeleteTmpEvents(false);

        // Verify that regular SQL (without Oracle hint) was used
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);

        verify(jdbcHelper, atLeastOnce()).executeUpdate(sqlCaptor.capture(), paramsCaptor.capture());

        // Check that none of the SQL statements contains the Oracle hint
        boolean foundOracleHint = sqlCaptor.getAllValues().stream()
                .anyMatch(sql -> sql.contains("/*+ INDEX(node_events_tmp node_events_tmp_pk) */"));

        assertEquals(expectedResult, foundOracleHint, expectedMessage);
        assertEquals(repos, Set.of("repo1"));
    }

    private void mockTempEvents(List<TempEvent> tempEvents) throws Exception {
        AtomicLong rowIndex = new AtomicLong(-1);
        when(resultSet.next()).thenAnswer(invocation -> rowIndex.incrementAndGet() < tempEvents.size());
        when(resultSet.getLong("timestamp")).thenAnswer(invocation -> tempEvents.get((int) rowIndex.get()).timestamp());
        when(resultSet.getInt("event_type")).thenReturn(1);
        when(resultSet.getString("path")).thenReturn("repo1/test");
        when(resultSet.getString("hints")).thenReturn("hints");
        when(resultSet.getLong("tmp_event_id")).thenAnswer(invocation -> tempEvents.get((int) rowIndex.get()).id());
    }

    private static TempEvent tempEvent(long id, int hourOfDay) {
        return new TempEvent(id, TimeUnit.HOURS.toMillis(hourOfDay));
    }

    private record TempEvent(long id, long timestamp) {
    }

    private void createEventsDao() {
        meterRegistry = new SimpleMeterRegistry();
        eventsDao = new EventsDao(jdbcHelper, meterRegistry);
        eventsDao.setDbProperties(dbProperties);
        eventsDao.setDbService(dbService);
        eventsDao.init();
    }
}

