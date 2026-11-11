package org.artifactory.sh.cleanup.events;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventHintType;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.event.dao.StorageEventsDao;
import org.artifactory.storage.event.StorageEventInfo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */

class StorageEventsServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    private static final long SIZE = 100L;

    @BeforeEach
    void init() {
        getOrCreateArtifactoryHomeStub().setProperty(ConstantValues.gcEventsCleanupDelayMillis, "0");
    }

    @AfterEach
    void after() {
        unbindArtifactoryHome();
    }

    @ParameterizedTest
    @MethodSource
    void testGetEventsAfter(StorageEventInfo.Data gcHints) throws SQLException {
        final StorageEventsDao storageEventsDao = mock(StorageEventsDao.class);
        final StorageEventsServiceImpl storageEventsService =
                spy(new StorageEventsServiceImpl(storageEventsDao));
        final StorageEventInfo eventInfo =
                new StorageEventInfo(1L, System.currentTimeMillis() - 2000, EventType.delete,
                        StringUtils.repeat("b", 40),
                        StringUtils.repeat("a", 64), SIZE, gcHints);

        when(storageEventsDao.loadEventsByParams(0L, 10L)).thenReturn(List.of(eventInfo));
        final List<EventInfo> eventsAfter =
                storageEventsService.getEventsAfter(0L, null, null, 10, QueueCursorType.GC.name());
        assertEquals(1, eventsAfter.size());
        assertEquals(SIZE,
                Long.valueOf(Objects.requireNonNull(
                        EventHintType.getValue(eventsAfter.get(0).getHints(), EventHintType.BinLength))));
        if (gcHints == null) {
            assertFalse(eventsAfter.get(0).getHints().contains(StorageEventInfo.Data.withGCDataHintPrefix(null)));
        } else {
            assertTrue(eventsAfter.get(0).getHints().contains(StorageEventInfo.Data.withGCDataHintPrefix(gcHints)));
        }
    }

    private static Object[][] testGetEventsAfter() {
        return new Object[][] {
                {null},
                {new StorageEventInfo.Data(1)}
        };
    }

    @ParameterizedTest
    @MethodSource("deleteAllPreviousBySHA1")
    void deleteAllPreviousBySHA1(int numberOfDeleted) throws SQLException {
        final String sha1 = "A";
        final StorageEventsDao storageEventsDao = mock(StorageEventsDao.class);
        if (numberOfDeleted < 0) {
            when(storageEventsDao.deleteAllPreviousBySHA1(sha1)).thenThrow(new SQLException("TEST"));
        } else {
            when(storageEventsDao.deleteAllPreviousBySHA1(sha1)).thenReturn(numberOfDeleted);
        }
        final StorageEventsServiceImpl storageEventsService = spy(new StorageEventsServiceImpl(storageEventsDao));

        int actual = storageEventsService.deleteAllPreviousBySha1(sha1);
        assertEquals(numberOfDeleted, actual);
        verify(storageEventsDao, times(1)).deleteAllPreviousBySHA1(sha1);
    }

    private static Stream<Arguments> deleteAllPreviousBySHA1() {
        return Stream.of(
                Arguments.of(-1),
                Arguments.of(0),
                Arguments.of(1),
                Arguments.of(2)
        );
    }

    @ParameterizedTest
    @MethodSource("getLatestEventsBySHA1")
    void getLatestEventsBySHA1(List<String> sha1s) throws SQLException {
        final StorageEventsDao storageEventsDao = mock(StorageEventsDao.class);
        boolean isPoisonPill = !sha1s.isEmpty() && sha1s.get(0).equals("POISON_PILL");
        if (isPoisonPill) {
            when(storageEventsDao.getLatestEventsBySHA1(sha1s)).thenThrow(new SQLException("TEST"));
        } else {
            when(storageEventsDao.getLatestEventsBySHA1(sha1s)).thenReturn(List.of());
        }
        final StorageEventsServiceImpl storageEventsService = spy(new StorageEventsServiceImpl(storageEventsDao));

        storageEventsService.getLatestEventsBySHA1(sha1s);
        verify(storageEventsDao, times(1)).getLatestEventsBySHA1(sha1s);
    }

    private static Stream<Arguments> getLatestEventsBySHA1() {
        return Stream.of(
                Arguments.of(List.of()),
                Arguments.of(List.of("A")),
                Arguments.of(List.of("POISON_PILL"))
        );
    }
}