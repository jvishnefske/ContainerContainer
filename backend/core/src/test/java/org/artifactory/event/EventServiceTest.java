package org.artifactory.event;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.service.EventsServiceImpl;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Test
public class EventServiceTest extends ArtifactoryHomeBoundTest {

    private AutoCloseable mocks;

    @Mock
    MigrationStatusStorageService migrationStatusStorageService;
    @Mock
    EventsDao eventsDao;

    EventsServiceImpl eventsService;

    @BeforeMethod
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        setStringSystemProperty(ConstantValues.shiftEventsInsertBulkSize, "500");
        setStringSystemProperty(ConstantValues.shiftEventsCopyBulkSize, "100");
        eventsService = new EventsServiceImpl(List.of());
        eventsService.setEventsDao(eventsDao);
        eventsService.setMigrationStatusStorageService(migrationStatusStorageService);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void testCountEventsRangeByRepoKey() throws SQLException {
        when(eventsDao.countRangeEventsByRepoPath(1515653250000L, 1515653250004L, "repo")).thenReturn(3);
        int count = eventsService.countEventsRange(1515653250000L, 1515653250004L, "repo",
                QueueCursorType.FEDERATED_REPLICATION.name());
        assertThat(count).isEqualTo(3);
    }

    @Test
    public void testCountEventsRangeWithoutRepoKey() throws SQLException {
        when(eventsDao.countRangeEvents(1515653250000L, 1515653250004L)).thenReturn(3);
        int count = eventsService.countEventsRange(1515653250000L, 1515653250004L, null,
                QueueCursorType.FEDERATION_SERVICE.name());
        assertThat(count).isEqualTo(3);
        verify(eventsDao).countRangeEvents(1515653250000L, 1515653250004L);
        verify(eventsDao, never()).countRangeEventsByRepoPath(anyLong(), anyLong(), anyString());
    }

    @Test
    public void testCountEventsRangeByRepoKeyWithFailure() throws SQLException {
        try {
            when(eventsDao.countRangeEventsByRepoPath(anyLong(), anyLong(), anyString()))
                    .thenThrow(new SQLException("bad"));
            eventsService.countEventsRange(1515653250000L, 1515653250004L, "repo",
                    QueueCursorType.FEDERATED_REPLICATION.name());
        } catch (StorageException e) {
            assertThat(e.getMessage()).contains("Failed to get event range on repo");
        }
    }

    @Test(description = "")
    public void testShiftEventWithUpdateMigrationFinished() throws SQLException {
        ArtifactoryHome homeMock = mock(ArtifactoryHome.class);
        ArtifactoryHome.bind(homeMock);
        ArtifactorySystemProperties props = mock(ArtifactorySystemProperties.class);
        when(homeMock.getArtifactoryProperties()).thenReturn(props);
        when(props.getBooleanProperty(eq(ConstantValues.eventTableRepoKeyMigrationEnable))).thenReturn(true);
        eventsService.setMigrationStatusStorageService(migrationStatusStorageService);
        Mockito.when(migrationStatusStorageService.findMigrationById(anyString()))
                .thenReturn(new MigrationStatus("fd", 6565, 0, null));
        eventsService.shiftEvents();
        Mockito.verify(eventsDao, Mockito.times(0)).shiftAndDeleteTmpEvents(true);
        Mockito.verify(eventsDao, Mockito.times(1)).shiftAndDeleteTmpEvents(false);
        eventsService.shiftEvents();
        Mockito.verify(eventsDao, Mockito.times(2)).shiftAndDeleteTmpEvents(false);
        Mockito.verify(eventsDao, Mockito.times(0)).shiftAndDeleteTmpEvents(true);
        eventsService.updateMonitorMigrationFinished();
        eventsService.shiftEvents();
        Mockito.verify(eventsDao, Mockito.times(1)).shiftAndDeleteTmpEvents(true);
        Mockito.verify(eventsDao, Mockito.times(2)).shiftAndDeleteTmpEvents(false);
        when(props.getBooleanProperty(eq(ConstantValues.eventTableRepoKeyMigrationEnable))).thenReturn(false);
        eventsService.shiftEvents();
        Mockito.verify(eventsDao, Mockito.times(1)).shiftAndDeleteTmpEvents(true);
        Mockito.verify(eventsDao, Mockito.times(3)).shiftAndDeleteTmpEvents(false);
    }
}
