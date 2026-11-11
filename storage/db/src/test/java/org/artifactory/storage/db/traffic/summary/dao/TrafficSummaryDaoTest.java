package org.artifactory.storage.db.traffic.summary.dao;

import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.traffic.summary.entity.TrafficSummaryEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author kunalm
 */
public class TrafficSummaryDaoTest {
    @Mock
    ArtifactoryJdbcHelper artifactoryJdbcHelper;
    private TrafficSummaryDao trafficSummaryDao;
    private AutoCloseable closeable;

    @BeforeEach
    void setup() {
        closeable = MockitoAnnotations.openMocks(this);
        trafficSummaryDao = new TrafficSummaryDao(artifactoryJdbcHelper);
    }

    @AfterEach
    void cleanup() throws Exception {
        closeable.close();
    }

    @Test
    void testGetByDateRange() throws SQLException {
        // Valid DB response
        ResultSet resultSetMock = createMockDbSearchResponse();
        doReturn(resultSetMock).when(artifactoryJdbcHelper).executeSelect(anyString(), any(), any());

        long startTime = System.currentTimeMillis();
        long endTime = startTime + 10000L;
        List<TrafficSummaryEntity> entity = trafficSummaryDao.getByDateRange(startTime, endTime);
        verify(artifactoryJdbcHelper).executeSelect(anyString(), any(), any());
        assertEquals(1, entity.size());
        assertEquals(1L, entity.getFirst().id());
        assertEquals(3L, entity.getFirst().totalRequests());
        assertEquals(1000L, entity.getFirst().totalBytes());

        // DB exception
        doThrow(SQLException.class).when(artifactoryJdbcHelper).executeSelect(anyString(), any(), any());
        assertThrows(StorageException.class, () -> trafficSummaryDao.getByDateRange(startTime, endTime));
    }

    @Test
    void testInsertTrafficSummary() throws SQLException {
        // No input
        trafficSummaryDao.insert(null);
        verify(artifactoryJdbcHelper, never()).executeUpdate(anyString(), any(), any(), any(), any(), any(), any(),
                any());

        // Valid input
        TrafficSummaryEntity entity = createTrafficSummaryEntity();
        trafficSummaryDao.insert(entity);
        verify(artifactoryJdbcHelper).executeUpdate(anyString(), any(), any(), any(), any(), any(), any(),
                any());

        // DB exception
        doThrow(SQLException.class).when(artifactoryJdbcHelper)
                .executeUpdate(anyString(), any(), any(), any(), any(), any(), any(),
                        any());
        assertThrows(StorageException.class, () -> trafficSummaryDao.insert(entity));

    }

    @Test
    void testDeleteByThresholdTime() throws SQLException {
        // Valid DB delete request
        long thresholdTime = System.currentTimeMillis() - 86400000L;
        trafficSummaryDao.deleteByThresholdTime(thresholdTime);
        verify(artifactoryJdbcHelper).executeUpdate(anyString(), anyLong());

        // DB exception
        doThrow(SQLException.class).when(artifactoryJdbcHelper).executeUpdate(anyString(), anyLong());
        assertThrows(StorageException.class, () -> trafficSummaryDao.deleteByThresholdTime(thresholdTime));
    }

    private ResultSet createMockDbSearchResponse() throws SQLException {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.next()).thenReturn(true).thenReturn(false);
        doReturn(1L).when(resultSetMock).getLong("id");
        doReturn(1759903128616L).when(resultSetMock).getLong("time");
        doReturn(3L).when(resultSetMock).getLong("total_request_count");
        doReturn(2L).when(resultSetMock).getLong("upload_request_count");
        doReturn(1L).when(resultSetMock).getLong("download_request_count");
        doReturn(1000L).when(resultSetMock).getLong("total_bytes");
        doReturn(700L).when(resultSetMock).getLong("upload_bytes");
        doReturn(300L).when(resultSetMock).getLong("download_bytes");
        return resultSetMock;
    }

    private TrafficSummaryEntity createTrafficSummaryEntity() {
        return TrafficSummaryEntity.builder()
                .time(123L)
                .totalRequests(10L)
                .uploadRequests(4L)
                .downloadRequests(6L)
                .totalBytes(1000L)
                .uploadBytes(400L)
                .downloadBytes(600L)
                .build();
    }
}
