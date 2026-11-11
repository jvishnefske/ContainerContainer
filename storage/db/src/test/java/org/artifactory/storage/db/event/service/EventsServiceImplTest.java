package org.artifactory.storage.db.event.service;

import lombok.SneakyThrows;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.EventType;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.entity.EventsDaoParams;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorType.FEDERATED_REPLICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class EventsServiceImplTest {

    private EventsServiceImpl eventsService;

    @Mock
    private EventsDao eventsDao;

    @Mock
    private ArtifactoryContext context;

    private ArtifactorySystemProperties artifactorySystemProperties;

    MigrationStatusStorageService mockMigrationStatusStorageService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ArtifactoryJdbcHelper jdbcHelper = Mockito.mock(ArtifactoryJdbcHelper.class);
        //eventsDao = new EventsDao(jdbcHelper);
        ArtifactoryHome artifactoryHome = mock(ArtifactoryHome.class);
        artifactorySystemProperties = mock(ArtifactorySystemProperties.class);
        when(artifactoryHome.getArtifactoryProperties()).thenReturn(artifactorySystemProperties);
        ArtifactoryContextThreadBinder.bind(context);
        ArtifactoryHome.bind(artifactoryHome);
        when(context.beanForType(IQueryBuilder.class)).thenReturn(getQueryBuilder(DbType.DERBY));
        when(context.beanForType(EventsDao.class))
                .thenReturn(eventsDao);
        eventsService = new EventsServiceImpl(List.of());
        eventsService.setEventsDao(eventsDao);
        mockMigrationStatusStorageService = mock(MigrationStatusStorageService.class);
        eventsService.setMigrationStatusStorageService(mockMigrationStatusStorageService);
    }

    @AfterEach
    void tearDown() throws Exception {
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
    void testGetEventsAfterWhenRepoColumnMigrationIsFalse() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(false);
        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;

        EventsDaoParams.builder().afterRepoColumnMigration(false).repoKey("repo1").suffix("fds/fds").eventId(1).limit(2)
                .build();
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");
        ArgumentCaptor<EventsDaoParams> eventsDaoParamsCaptor =
                ArgumentCaptor.forClass(EventsDaoParams.class);
        verify(eventsDao).loadEventsByParams(eventsDaoParamsCaptor.capture());
        assertEquals(eventId, eventsDaoParamsCaptor.getValue().getEventId());
        assertEquals(repoKey, eventsDaoParamsCaptor.getValue().getRepoKey());
        assertEquals(fileSuffix, eventsDaoParamsCaptor.getValue().getSuffix());
        assertFalse(eventsDaoParamsCaptor.getValue().getAfterRepoColumnMigration());
        assertFalse(eventsDaoParamsCaptor.getValue().isInclusive());
        assertEquals(10, eventsDaoParamsCaptor.getValue().getLimit());
    }

    @Test
    void testGetEventsAfterWhenRepoColumnMigrationIsTrueAndMigrationNotFinished() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 6565, 0, null));
        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;
        new ArrayList<>();
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");

        ArgumentCaptor<EventsDaoParams> eventsDaoParamsCaptor =
                ArgumentCaptor.forClass(EventsDaoParams.class);
        verify(eventsDao).loadEventsByParams(eventsDaoParamsCaptor.capture());
        assertEquals(eventId, eventsDaoParamsCaptor.getValue().getEventId());
        assertEquals(repoKey, eventsDaoParamsCaptor.getValue().getRepoKey());
        assertEquals(fileSuffix, eventsDaoParamsCaptor.getValue().getSuffix());
        assertFalse(eventsDaoParamsCaptor.getValue().getAfterRepoColumnMigration());
        assertFalse(eventsDaoParamsCaptor.getValue().isInclusive());
        assertEquals(10, eventsDaoParamsCaptor.getValue().getLimit());
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");
        verify(mockMigrationStatusStorageService, times(1)).findMigrationById(any());
    }

    @Test
    void testGetEventsAfterWhenRepoColumnMigrationIsTrueAndMigrationNotFinishedNull()
            throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(null);
        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;
        new ArrayList<>();
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");

        ArgumentCaptor<EventsDaoParams> eventsDaoParamsCaptor =
                ArgumentCaptor.forClass(EventsDaoParams.class);
        verify(eventsDao).loadEventsByParams(eventsDaoParamsCaptor.capture());
        assertEquals(eventId, eventsDaoParamsCaptor.getValue().getEventId());
        assertEquals(repoKey, eventsDaoParamsCaptor.getValue().getRepoKey());
        assertEquals(fileSuffix, eventsDaoParamsCaptor.getValue().getSuffix());
        assertFalse(eventsDaoParamsCaptor.getValue().getAfterRepoColumnMigration());
        assertFalse(eventsDaoParamsCaptor.getValue().isInclusive());
        assertEquals(10, eventsDaoParamsCaptor.getValue().getLimit());
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");
        verify(mockMigrationStatusStorageService, times(1)).findMigrationById(any());
    }

    @Test
    void testGetEventsAfterWhenRepoColumnMigrationIsTrueAndMigrationFinished() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 6565, 456, null));
        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");

        ArgumentCaptor<EventsDaoParams> eventsDaoParamsCaptor =
                ArgumentCaptor.forClass(EventsDaoParams.class);
        ArrayList<EventRecord> mockResponseLoadEventsDao = new ArrayList<>();
        int timestamp = 1345;
        mockResponseLoadEventsDao.add(new EventRecord(eventId, timestamp, EventType.update, fileSuffix));
        when(eventsDao.loadEventsByParams(any())).thenReturn(mockResponseLoadEventsDao);
        verify(eventsDao).loadEventsByParams(eventsDaoParamsCaptor.capture());
        assertEquals(eventId, eventsDaoParamsCaptor.getValue().getEventId());
        assertEquals(repoKey, eventsDaoParamsCaptor.getValue().getRepoKey());
        assertEquals(fileSuffix, eventsDaoParamsCaptor.getValue().getSuffix());
        assertTrue(eventsDaoParamsCaptor.getValue().getAfterRepoColumnMigration());
        assertFalse(eventsDaoParamsCaptor.getValue().isInclusive());
        assertEquals(10, eventsDaoParamsCaptor.getValue().getLimit());
        List<EventInfo> getEventsAfterResult = eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");
        verify(mockMigrationStatusStorageService, times(1)).findMigrationById(any());
        assertEquals(1, getEventsAfterResult.size());
        assertEquals(fileSuffix, getEventsAfterResult.get(0).getPath());
        assertEquals(eventId, getEventsAfterResult.get(0).getEventId());
        assertEquals(timestamp, getEventsAfterResult.get(0).getTimestamp());
    }

    @Test
    void testGetEventsAfterWhenRepoColumnMigrationIsTrueAndMigrationFinishedException() {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenAnswer(
                (Answer<Boolean>) invocation -> {
                    throw new Exception();
                });

        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;

        StorageException exception =
                assertThrows(
                        StorageException.class,
                        () -> eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, ""));
        assertEquals("Failed to retrieve event", exception.getMessage());
    }

    @Test
    void testCountEventsAfterWhenRepoColumnMigrationIsTrueAndMigrationFinished() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 6565, 456, null));
        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");

        ArgumentCaptor<EventsDaoParams> eventsDaoParamsCaptor =
                ArgumentCaptor.forClass(EventsDaoParams.class);
        verify(eventsDao).loadEventsByParams(eventsDaoParamsCaptor.capture());
        assertEquals(eventId, eventsDaoParamsCaptor.getValue().getEventId());
        assertEquals(repoKey, eventsDaoParamsCaptor.getValue().getRepoKey());
        assertEquals(fileSuffix, eventsDaoParamsCaptor.getValue().getSuffix());
        assertTrue(eventsDaoParamsCaptor.getValue().getAfterRepoColumnMigration());
        assertFalse(eventsDaoParamsCaptor.getValue().isInclusive());
        assertEquals(10, eventsDaoParamsCaptor.getValue().getLimit());
        when(eventsService.countEventsAfter(eventId, repoKey, fileSuffix)).thenReturn(6);
        int result = eventsService.countEventsAfter(eventId, repoKey, fileSuffix);
        verify(mockMigrationStatusStorageService, times(1)).findMigrationById(any());
        assertEquals(6, result);
    }

    @Test
    void testCountEventsAfterAfterWhenRepoColumnMigrationIsFalse() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(false);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 6565, 456, null));
        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;
        eventsService.getEventsAfter(eventId, repoKey, fileSuffix, limit, "");

        ArgumentCaptor<EventsDaoParams> eventsDaoParamsCaptor =
                ArgumentCaptor.forClass(EventsDaoParams.class);
        verify(eventsDao).loadEventsByParams(eventsDaoParamsCaptor.capture());
        assertEquals(eventId, eventsDaoParamsCaptor.getValue().getEventId());
        assertEquals(repoKey, eventsDaoParamsCaptor.getValue().getRepoKey());
        assertEquals(fileSuffix, eventsDaoParamsCaptor.getValue().getSuffix());
        assertFalse(eventsDaoParamsCaptor.getValue().getAfterRepoColumnMigration());
        assertFalse(eventsDaoParamsCaptor.getValue().isInclusive());
        assertEquals(10, eventsDaoParamsCaptor.getValue().getLimit());
        when(eventsService.countEventsAfter(eventId, repoKey, fileSuffix)).thenReturn(6);
        int result = eventsService.countEventsAfter(eventId, repoKey, fileSuffix);
        verify(mockMigrationStatusStorageService, times(0)).findMigrationById(any());
        assertEquals(6, result);
    }

    /**
     * Migration is over affects if eventsDao will be run with repo column or not
     * The value returned from the dao method doesn't really matter,
     * the test makes sure the correct parameter is passed to the dao method
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testHasEventsAfterTimestampExists(boolean isMigrationOver) throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(isMigrationOver);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 1, 2, null));
        when(eventsDao.hasEventsAfterTimestamp(anyLong(), anyString(), anyBoolean())).thenReturn(true);

        eventsService.hasEventsAfterTimestamp(1L, "repo");
        verify(eventsDao, times(1)).hasEventsAfterTimestamp(anyLong(), anyString(), eq(isMigrationOver));
    }

    @Test
    @SneakyThrows
    void testHasEventsAfterTimestampThrows() {
        when(eventsDao.hasEventsAfterTimestamp(anyLong(), anyString(), anyBoolean()))
                .thenThrow(new SQLException("TEST"));
        Exception e = assertThrows(StorageException.class, () ->
                eventsService.hasEventsAfterTimestamp(1L, "repo"));
        assertTrue(e.getMessage().contains("TEST"));
    }

    @Test
    void testCountEventsAfterWhenRepoColumnMigrationIsTrueAndMigrationFinishedException() {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenAnswer(
                (Answer<Boolean>) invocation -> {
                    throw new Exception();
                });

        long eventId = 12345L;
        String repoKey = "exampleRepo";
        String fileSuffix = ".txt";
        int limit = 10;

        StorageException exception =
                assertThrows(
                        StorageException.class, () -> eventsService.countEventsAfter(eventId, repoKey, fileSuffix));
        assertEquals("Failed to retrieve event", exception.getMessage());
    }

    @Test
    void testGetTempEventsCount() throws SQLException {
        when(eventsDao.getTempEventsCount()).thenReturn(3L);
        long tempEventsCount = eventsService.getTempEventsCount();
        assertEquals(3L, tempEventsCount);
    }

    @Test
    void testGetTempEventsCountWithException() throws SQLException {
        SQLException exception = new SQLException("SQL exception");
        when(eventsDao.getTempEventsCount()).thenThrow(exception);
        StorageException storageException = assertThrows(StorageException.class, eventsService::getTempEventsCount);
        assertEquals("Failed to get event count from node events tmp", storageException.getMessage());
    }

    @Test
    void testGetRemainingEventsInQueue() throws SQLException {
        EventRecord path = new EventRecord(1L, EventType.create, "path");
        List<EventRecord> eventRecords = List.of(path);
        when(eventsDao.eventsLeftForCursorToReach("repo-federated_replicationurl%", "federation", "repo", 1L,
                false)).thenReturn(eventRecords);
        List<EventInfo> remainingEventsInQueue =
                eventsService.getRemainingEventsInQueue("url", "federation", "repo", 1L);
        assertEquals(1, remainingEventsInQueue.size());
        EventInfo eventInfo = remainingEventsInQueue.get(0);
        assertEquals(path.getEventId(), eventInfo.getEventId());
        assertEquals(path.getPath(), eventInfo.getPath());
        assertEquals(path.getType(), eventInfo.getType());
    }

    @Test
    void testGetRemainingEventsInQueueAfterMigration() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 6565, 456, null));

        EventRecord path = new EventRecord(1L, EventType.create, "path");
        List<EventRecord> eventRecords = List.of(path);
        when(eventsDao.eventsLeftForCursorToReach("repo-federated_replicationurl%", "federation", "repo", 1L,
                true)).thenReturn(eventRecords);
        List<EventInfo> remainingEventsInQueue =
                eventsService.getRemainingEventsInQueue("url", "federation", "repo", 1L);
        assertEquals(1, remainingEventsInQueue.size());
        EventInfo eventInfo = remainingEventsInQueue.get(0);
        assertEquals(path.getEventId(), eventInfo.getEventId());
        assertEquals(path.getPath(), eventInfo.getPath());
        assertEquals(path.getType(), eventInfo.getType());
    }

    @Test
    void testGetEventIdByTimestampAfterMigration() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 6565, 456, null));

        when(eventsDao.getEventIdByTimestamp(1L, "repo", true)).thenReturn(
                3L);
        Long result =
                eventsService.getFirstEventIdAfterProvidedTimestamp(1L, "repo", FEDERATED_REPLICATION.getName());
        assertEquals(3L, result);
    }

    @Test
    void testGetEventIdByTimestampAfterMigrationNoRepo() throws SQLException {
        when(artifactorySystemProperties.getBooleanProperty(
                ConstantValues.eventTableRepoKeyMigrationEnable)).thenReturn(true);
        when(mockMigrationStatusStorageService.findMigrationById(anyString())).thenReturn(
                new MigrationStatus("fd", 6565, 456, null));

        when(eventsDao.getEventIdByTimestamp(1L, null, true)).thenReturn(
                3L);
        Long result =
                eventsService.getFirstEventIdAfterProvidedTimestamp(1L, null, FEDERATED_REPLICATION.getName());
        assertEquals(3L, result);
    }

    @Test
    void testGetEventIdByTimestampBeforeMigration() throws SQLException {
        when(eventsDao.getEventIdByTimestamp(1L, "repo", false)).thenReturn(
                3L);
        Long result =
                eventsService.getFirstEventIdAfterProvidedTimestamp(1L, "repo", FEDERATED_REPLICATION.getName());
        assertEquals(3L, result);
    }

    @Test
    void testGetEventIdByTimestampWithException() throws SQLException {
        when(eventsDao.getEventIdByTimestamp(1L, "repo", false)).thenThrow(
                new SQLException("test"));

        assertThrows(StorageException.class,
                () -> eventsService.getFirstEventIdAfterProvidedTimestamp(1L, "repo", FEDERATED_REPLICATION.getName()));
    }
}
