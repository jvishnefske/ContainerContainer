package org.artifactory.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomer Slutsky
 */
class TmpEventLogTruncateJobTest extends ArtifactoryHomeBoundJUnitTest {
    @Spy
    TmpEventLogTruncateJob cleanupJob;
    @Mock
    ArtifactoryContext context;
    @Mock
    EventsDao eventsDao;
    AutoCloseable mocks;

    @BeforeEach
    public void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.beanForType(EventsDao.class)).thenReturn(eventsDao);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void teardown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
    void onExecuteCleanup(Integer time) throws JobExecutionException, SQLException {
        when(cleanupJob.getCurrentHour()).thenReturn(time);
        when(eventsDao.hasPartition(any())).thenReturn(true);
        Optional<TmpEventPartitions> partitionToClean = TmpEventPartitions.getPartitionsToTruncate(time);
        when(context.isReady()).thenReturn(true);
        cleanupJob.onExecute(mock(JobExecutionContext.class));
        partitionToClean.get().getPartitionsToTruncate().forEach(partition -> {
            try {
                verify(eventsDao).truncateTmpPartition(partition);
            } catch (SQLException e) {
                Assertions.fail();
            }
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
    void onExecuteNoPartition(Integer time) throws JobExecutionException, SQLException {
        when(cleanupJob.getCurrentHour()).thenReturn(time);
        when(eventsDao.hasPartition(any())).thenReturn(false);
        Optional<TmpEventPartitions> partitionToClean = TmpEventPartitions.getPartitionsToTruncate(time);
        when(context.isReady()).thenReturn(true);
        cleanupJob.onExecute(mock(JobExecutionContext.class));
        partitionToClean.get().getPartitionsToTruncate().forEach(partition -> {
            try {
                verify(eventsDao, times(0)).truncateTmpPartition(partition);
            } catch (SQLException e) {
                Assertions.fail();
            }
        });
    }

    @Test
    void onExecuteNoContext() throws JobExecutionException, SQLException {
        when(context.isReady()).thenReturn(false);
        cleanupJob.onExecute(mock(JobExecutionContext.class));
        verify(eventsDao, times(0)).truncateTmpPartition(anyString());
    }

    @Test
    void onExecuteSqlException() throws JobExecutionException, SQLException {
        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(TmpEventLogTruncateJob.class, Level.ERROR);
        when(context.isReady()).thenReturn(true);
        when(cleanupJob.getCurrentHour()).thenReturn(0);
        when(eventsDao.hasPartition(any())).thenReturn(true);
        doThrow(SQLException.class).when(eventsDao).truncateTmpPartition(any());
        cleanupJob.onExecute(mock(JobExecutionContext.class));
        List<ILoggingEvent> loggedEvents = appender.getLoggedEvents();
        assertEquals(2, loggedEvents.size());
        assertTrue(loggedEvents.get(0).getMessage().contains("Failed to perform node_events_tmp partition truncate"));
    }

    @Test
    void testGetCurrentTimestamp() {
        int currentTimestamp = cleanupJob.getCurrentHour();
        assertTrue(currentTimestamp >= 0 && currentTimestamp <= 23);
    }
}