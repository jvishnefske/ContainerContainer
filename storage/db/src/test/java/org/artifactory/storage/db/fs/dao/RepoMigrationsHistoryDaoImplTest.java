package org.artifactory.storage.db.fs.dao;

import com.jfrog.ph.acl.model.migration.MigrationStatus;
import com.jfrog.ph.acl.model.migration.RepoMigrationHistory;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.QueryControls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoMigrationsHistoryDaoImplTest {

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;
    @Mock
    private ResultSet resultSet;

    private RepoMigrationsHistoryDaoImpl dao;

    @BeforeEach
    void setUp() {
        dao = new RepoMigrationsHistoryDaoImpl(jdbcHelper);
    }

    @Test
    void upsertInsertsWhenRecordDoesNotExist() throws SQLException {
        RepoMigrationHistory history = buildHistory("job-1", "test-repo", MigrationStatus.MIGRATION_COMPLETED);
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("job-1")))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        when(jdbcHelper.executeUpdate(any(QueryControls.class), anyString(), any(Object[].class))).thenReturn(1);

        int result = dao.upsert(history);

        assertEquals(1, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeUpdate(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("INSERT INTO"));
    }

    @Test
    void upsertUpdatesWhenRecordExists() throws SQLException {
        RepoMigrationHistory history = buildHistory("job-1", "test-repo", MigrationStatus.MIGRATION_COMPLETED);
        stubResultSetForHistory();
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("job-1")))
                .thenReturn(resultSet);
        when(jdbcHelper.executeUpdate(any(QueryControls.class), anyString(), any(Object[].class))).thenReturn(1);

        int result = dao.upsert(history);

        assertEquals(1, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeUpdate(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("UPDATE"));
    }

    @Test
    void findByJobIdReturnsNullWhenNotFound() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("missing-job")))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        RepoMigrationHistory result = dao.findByJobId("missing-job");

        assertNull(result);
    }

    @Test
    void findByJobIdReturnsHistoryWhenFound() throws SQLException {
        stubResultSetForHistory();
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("job-1")))
                .thenReturn(resultSet);

        RepoMigrationHistory result = dao.findByJobId("job-1");

        assertNotNull(result);
        assertEquals("job-1", result.getJobId());
        assertEquals("test-repo", result.getRepoName());
        assertEquals(MigrationStatus.MIGRATION_IN_PROGRESS, result.getMigrationStatus());
    }

    @Test
    void findByRepoNameReturnsEmptyListWhenNotFound() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("no-repo")))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        List<RepoMigrationHistory> results = dao.findByRepoName("no-repo");

        assertTrue(results.isEmpty());
    }

    @Test
    void findByRepoNameReturnsMultipleEntries() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("test-repo")))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("job_id")).thenReturn("job-1", "job-2");
        when(resultSet.getString("repo_name")).thenReturn("test-repo", "test-repo");
        when(resultSet.getString("target_repo")).thenReturn(null, null);
        when(resultSet.getString("migration_status")).thenReturn("MIGRATION_COMPLETED", "FAILED");
        when(resultSet.getTimestamp("start_time")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(resultSet.getTimestamp("end_time")).thenReturn(null);

        List<RepoMigrationHistory> results = dao.findByRepoName("test-repo");

        assertEquals(2, results.size());
        assertEquals("job-1", results.get(0).getJobId());
        assertEquals("job-2", results.get(1).getJobId());
    }

    @Test
    void queryBuildsCorrectFilters() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        List<RepoMigrationHistory> results = dao.query("job-1", "test-repo", "MIGRATION_COMPLETED");

        assertTrue(results.isEmpty());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("job_id = ?"));
        assertTrue(sql.contains("repo_name = ?"));
        assertTrue(sql.contains("migration_status = ?"));
        assertTrue(sql.contains("ORDER BY start_time DESC"));
    }

    @Test
    void querySkipsNullFilters() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        dao.query(null, null, "MIGRATION_COMPLETED");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertFalse(sql.contains("job_id = ?"));
        assertFalse(sql.contains("repo_name = ?"));
        assertTrue(sql.contains("migration_status = ?"));
    }

    private RepoMigrationHistory buildHistory(String jobId, String repoName, MigrationStatus status) {
        return RepoMigrationHistory.builder()
                .jobId(jobId)
                .repoName(repoName)
                .migrationStatus(status)
                .startTime(new Timestamp(System.currentTimeMillis()))
                .build();
    }

    private void stubResultSetForHistory() throws SQLException {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("job_id")).thenReturn("job-1");
        when(resultSet.getString("repo_name")).thenReturn("test-repo");
        when(resultSet.getString("target_repo")).thenReturn(null);
        when(resultSet.getString("migration_status")).thenReturn("MIGRATION_IN_PROGRESS");
        when(resultSet.getTimestamp("start_time")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(resultSet.getTimestamp("end_time")).thenReturn(null);
    }
}
