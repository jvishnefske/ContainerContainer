package org.artifactory.storage.db.event.dao;

import lombok.SneakyThrows;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.entity.EventsDaoParams;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.querybuilder.DerbyQueryBuilder;
import org.jfrog.storage.util.querybuilder.IQueryBuilder;
import org.jfrog.storage.util.querybuilder.MysqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.OracleQueryBuilder;
import org.jfrog.storage.util.querybuilder.PostgresqlQueryBuilder;
import org.jfrog.storage.util.querybuilder.SqlServerQueryBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.artifactory.storage.db.event.dao.EventsDao.SELECT_ALL_EVENTS_FROM_CURSOR_TO_PROVIDED_EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventsDaoTest {
    private EventsDao eventsDao;
    private ArtifactoryJdbcHelper jdbcHelper;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ArtifactoryContext context;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        jdbcHelper = Mockito.mock(ArtifactoryJdbcHelper.class);
        eventsDao = new EventsDao(jdbcHelper,null);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        ArtifactorySystemProperties artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(artifactoryHome);
        when(context.beanForType(IQueryBuilder.class)).thenReturn(getQueryBuilder(DbType.DERBY));
    }

    @AfterEach
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    private IQueryBuilder getQueryBuilder(DbType type) {
        return switch (type) {
            case DERBY -> new DerbyQueryBuilder();
            case MARIADB, MYSQL -> new MysqlQueryBuilder();
            case ORACLE -> new OracleQueryBuilder();
            case MSSQL -> new SqlServerQueryBuilder();
            case POSTGRESQL -> new PostgresqlQueryBuilder();
        };
    }


    @Test
    void testGetResultSetWithEventIdAndRepoWhenRepoEventExist() throws SQLException {
        long eventId = 12345L;
        String repoKey = "my-repo";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .repoKey(repoKey)
                .afterRepoColumnMigration(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? AND repo = ? order by event_id ASC",
                eventId, repoKey + "/%", repoKey);
    }

    @Test
    void testGetResultSetWithEventIdAndRepoWhenRepoEventNotExist() throws SQLException {
        long eventId = 12345L;
        String repoKey = "my-repo";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .repoKey(repoKey)
                .afterRepoColumnMigration(false)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? order by event_id ASC", eventId,
                repoKey + "/%");
    }

    @Test
    void testGetResultSetWithEventIdAndSuffixWhenRepoColumnMigrationPassed() throws SQLException {
        long eventId = 12345L;
        String suffix = "/path/to/some/resource";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .suffix(suffix)
                .afterRepoColumnMigration(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? order by event_id ASC", 12345L,
                "%" + suffix);
    }

    @Test
    void testGetResultSetWithEventIdAndSuffixWhenRepoColumnMigrationNotPassed() throws SQLException {
        long eventId = 12345L;
        String suffix = "/path/to/some/resource";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .suffix(suffix)
                .afterRepoColumnMigration(false)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? order by event_id ASC", 12345L,
                "%" + suffix);
    }


    @Test
    void testGetResultSetWithEventIdAndRepoAndSuffixWhenRepoColumnMigrationPassed() throws SQLException {
        long eventId = System.currentTimeMillis();
        String suffix = "/path/to/some/resource";
        String repoKey = "repo1";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .repoKey(repoKey)
                .suffix(suffix)
                .afterRepoColumnMigration(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? AND repo = ? order by event_id ASC",
                eventId, repoKey + "/%" + suffix, repoKey);
    }

    @Test
    void testGetResultSetWithEventIdAndRepoAndSuffixWhenRepoColumnMigrationNotPassed() throws SQLException {
        long eventId = System.currentTimeMillis();
        String suffix = "/path/to/some/resource";
        String repoKey = "repo1";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .repoKey(repoKey)
                .suffix(suffix)
                .afterRepoColumnMigration(false)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? order by event_id ASC", eventId,
                repoKey + "/%" + suffix);
    }

    @Test
    void testGetResultSetWithEventIdAndRepoAndSuffixWhenRepoColumnMigrationNotPassedInclusive()
            throws SQLException {
        long eventId = System.currentTimeMillis();
        String suffix = "/path/to/some/resource";
        String repoKey = "repo1";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .repoKey(repoKey)
                .suffix(suffix)
                .afterRepoColumnMigration(false)
                .inclusive(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id >= ? AND path like ? order by event_id ASC", eventId,
                repoKey + "/%" + suffix);
    }

    @Test
    void testGetResultSetWithEventIdAndRepoAndSuffixWhenRepoColumnMigrationNotPassedCount() throws SQLException {
        long eventId = System.currentTimeMillis();
        String suffix = "/path/to/some/resource";
        String repoKey = "repo1";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .repoKey(repoKey)
                .suffix(suffix)
                .afterRepoColumnMigration(false)
                .inclusive(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, true);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select count(1) from node_events where event_id >= ? AND path like ?", eventId,
                repoKey + "/%" + suffix);
    }

    @Test
    void testGetResultSetWithEventIdAndRepoAndSuffixWhenRepoColumnMigrationPassedCount() throws SQLException {
        long eventId = System.currentTimeMillis();
        String suffix = "/path/to/some/resource";
        String repoKey = "repo1";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .repoKey(repoKey)
                .suffix(suffix)
                .afterRepoColumnMigration(true)
                .inclusive(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, true);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select count(1) from node_events where event_id >= ? AND path like ? AND repo = ?", eventId,
                repoKey + "/%" + suffix, repoKey);
    }

    @Test
    void testGetResultSetRepoWhenRepoColumnMigrationPassed() throws SQLException {
        long eventId = 0L;
        String repoKey = "my-repo";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .repoKey(repoKey)
                .afterRepoColumnMigration(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? AND repo = ? order by event_id ASC",
                eventId, repoKey + "/%", repoKey);
    }

    @Test
    void testGetResultSetRepoWhenRepoColumnMigrationNotPassed() throws SQLException {
        long eventId = 0L;
        String repoKey = "my-repo";
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .repoKey(repoKey)
                .afterRepoColumnMigration(false)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ? AND path like ? order by event_id ASC",
                eventId, repoKey + "/%");
    }

    @Test
    void testGetResultSetOnlyEventIdWhenRepoColumnMigrationNotPassed() throws SQLException {
        long eventId = 0L;
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .afterRepoColumnMigration(false)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ?  order by event_id ASC",
                eventId);
    }

    @Test
    void testGetResultSetOnlyEventIdWhenRepoColumnMigrationPassed() throws SQLException {
        long eventId = 0L;
        EventsDaoParams queryParams = EventsDaoParams.builder()
                .eventId(eventId)
                .afterRepoColumnMigration(true)
                .build();

        // Mock the JDBCHelper to return a ResultSet
        when(jdbcHelper.executeSelect(ArgumentMatchers.anyString(), ArgumentMatchers.any()))
                .thenReturn(resultSet);

        eventsDao.handleGetResultSet(queryParams, false);

        // Verify the arguments passed to executeSelect
        verify(jdbcHelper).executeSelect(
                "select * from node_events where event_id > ?  order by event_id ASC",
                eventId);
    }

    @Test
    void truncateTmpPartitionNextPartitionHasInfo() throws SQLException {
        when(jdbcHelper.executeSelect(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        eventsDao.truncateTmpPartition("partition");
        verify(jdbcHelper).executeSelect("Select 1 from partition where timestamp > 1 limit 1");
        verify(jdbcHelper, times(0)).executeUpdate(anyString());
    }

    @Test
    void truncateTmpPartitionNextPartitionHasNoInfo() throws SQLException {
        when(jdbcHelper.executeSelect(anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        eventsDao.truncateTmpPartition("partition");
        verify(jdbcHelper).executeSelect("Select 1 from partition where timestamp > 1 limit 1");
        verify(jdbcHelper).executeUpdate("TRUNCATE partition");
    }

    @Test
    void testPassedThreshold() {
        Duration fetchDuration = Duration.of(2000, ChronoUnit.MILLIS);
        Duration threshold = Duration.of(1000, ChronoUnit.MILLIS);
        assertTrue(eventsDao.passedThreshold(fetchDuration, threshold));
        assertFalse(eventsDao.passedThreshold(threshold, fetchDuration));
    }

    @Test
    void testEventsLeftForCursorToReach() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getLong("event_id")).thenReturn(1L);
        when(resultSet.getLong("timestamp")).thenReturn(123L);
        when(resultSet.getShort("event_type")).thenReturn((short) 1);
        when(resultSet.getString("path")).thenReturn("path");
        when(resultSet.getString("hints")).thenReturn("hints");

        String query = SELECT_ALL_EVENTS_FROM_CURSOR_TO_PROVIDED_EVENT + " repo = ?";
        when(jdbcHelper.executeSelect(query, "cursor", "federation", 2L, "repo")).thenReturn(resultSet);
        List<EventRecord> eventRecords = eventsDao.eventsLeftForCursorToReach("cursor", "federation", "repo", 2L, true);
        assertEquals(1, eventRecords.size());
        verify(resultSet, times(2)).next();
    }

    @Test
    void testEventsLeftForCursorToReachWithoutRepoKey() throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getLong("event_id")).thenReturn(1L);
        when(resultSet.getLong("timestamp")).thenReturn(123L);
        when(resultSet.getShort("event_type")).thenReturn((short) 1);
        when(resultSet.getString("path")).thenReturn("path");
        when(resultSet.getString("hints")).thenReturn("hints");

        String query = SELECT_ALL_EVENTS_FROM_CURSOR_TO_PROVIDED_EVENT + " path like ?";
        when(jdbcHelper.executeSelect(query, "cursor", "federation", 2L, "repo/%")).thenReturn(resultSet);
        List<EventRecord> eventRecords =
                eventsDao.eventsLeftForCursorToReach("cursor", "federation", "repo", 2L, false);
        assertEquals(1, eventRecords.size());
        verify(resultSet, times(2)).next();
    }

    @Test
    void testGetEventIdByTimestamp() throws SQLException {
        ArgumentCaptor<String> captor = ArgumentCaptor.captor();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("event_id")).thenReturn(4L);
        when(jdbcHelper.executeSelect(anyString(), eq(1L), eq("repo"))).thenReturn(resultSet);
        Long eventId = eventsDao.getEventIdByTimestamp(1L, "repo", true);
        assertEquals(4L, eventId);
        verify(jdbcHelper).executeSelect(captor.capture(), eq(1L), eq("repo"));
        String value = captor.getValue();
        assertTrue(value.contains("timestamp >= ? and repo=?"));
    }

    @Test
    void testGetEventIdByTimestampWithPath() throws SQLException {
        ArgumentCaptor<String> captor = ArgumentCaptor.captor();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("event_id")).thenReturn(4L);
        when(jdbcHelper.executeSelect(anyString(), eq(1L), eq("repo/%"))).thenReturn(resultSet);
        Long eventId = eventsDao.getEventIdByTimestamp(1L, "repo", false);
        assertEquals(4L, eventId);
        verify(jdbcHelper).executeSelect(captor.capture(), eq(1L), eq("repo/%"));
        String value = captor.getValue();
        assertTrue(value.contains("timestamp >= ? and path like ?"));
    }

    @Test
    void testGetEventIdByTimestampNoRepo() throws SQLException {
        ArgumentCaptor<String> captor = ArgumentCaptor.captor();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("event_id")).thenReturn(4L);
        when(jdbcHelper.executeSelect(anyString(), eq(1L))).thenReturn(resultSet);
        Long eventId = eventsDao.getEventIdByTimestamp(1L, null, false);
        assertEquals(4L, eventId);
        verify(jdbcHelper).executeSelect(captor.capture(), eq(1L));
        String value = captor.getValue();
        assertTrue(value.contains("timestamp >= ?"));
    }

    @ParameterizedTest
    @MethodSource("hasEventsAfterTimestampWithColumnRepo")
    void testHasEventsAfterTimestamp(boolean withColumnRepo, String queryVariant) throws SQLException {
        ArgumentCaptor<String> captor = ArgumentCaptor.captor();
        when(jdbcHelper.executeSelect(anyString(), anyLong(), anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        eventsDao.hasEventsAfterTimestamp(1L, "repo1", withColumnRepo);

        verify(jdbcHelper).executeSelect(captor.capture(), anyLong(), anyString());
        verify(resultSet, times(1)).next();
        String executedQueryString = captor.getValue();
        assertTrue(executedQueryString.contains(queryVariant));
    }

    @SneakyThrows
    @Test
    void testHasEventsAfterTimeStampNoCursor(){
        when(jdbcHelper.executeSelect(anyString(), anyLong(), anyString())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        boolean hasMore = eventsDao.hasEventsAfterTimestamp(1L, "repo1", true);
        assertFalse(hasMore);
    }

    @SneakyThrows
    @Test
    void testHasEventsAfterTimestampThrows(){
        when(jdbcHelper.executeSelect(anyString(), anyLong(), anyString())).thenThrow(new SQLException("TEST"));

        Exception exception =
                assertThrows(SQLException.class, () ->
                        eventsDao.hasEventsAfterTimestamp(1L, "repo", true));
        assertEquals("TEST", exception.getMessage());
    }

    private static Stream<Arguments> hasEventsAfterTimestampWithColumnRepo() {
        return Stream.of(
                Arguments.of(true, "repo = ?"),
                Arguments.of(false, "path like ?")
        );
    }

    @Test
    void testCountRangeEvents() throws SQLException {
        ArgumentCaptor<String> captor = ArgumentCaptor.captor();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(2);
        when(jdbcHelper.executeSelect(anyString(), eq(1L), eq(2L))).thenReturn(resultSet);
        int eventsCount = eventsDao.countRangeEvents(1L, 2);
        assertEquals(2L, eventsCount);
        verify(jdbcHelper).executeSelect(captor.capture(), eq(1L), eq(2L));
        String value = captor.getValue();
        assertTrue(value.contains("timestamp >= ? AND timestamp < ?"));
        assertFalse(value.contains("repo"));
    }
}