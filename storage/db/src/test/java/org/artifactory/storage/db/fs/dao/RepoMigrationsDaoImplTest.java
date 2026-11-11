package org.artifactory.storage.db.fs.dao;

import com.jfrog.ph.acl.model.migration.MigrationStatus;
import com.jfrog.ph.acl.model.migration.RepoMigration;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoMigrationsDaoImplTest {

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;
    @Mock
    private DbService dbService;
    @Mock
    private ResultSet resultSet;

    private RepoMigrationsDaoImpl dao;

    @BeforeEach
    void setUp() {
        dao = new RepoMigrationsDaoImpl(jdbcHelper, dbService);
    }

    @Test
    void upsertInsertsWhenRecordDoesNotExist() throws SQLException {
        RepoMigration migration = buildMigration("new-repo", "job-2", "nuget", MigrationStatus.SCHEDULED_PENDING);
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("new-repo")))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        when(jdbcHelper.executeUpdate(any(QueryControls.class), anyString(), any(Object[].class))).thenReturn(1);

        int result = dao.upsert(migration);

        assertEquals(1, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeUpdate(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("INSERT INTO repo_migrations"));
    }

    @Test
    void upsertUpdatesWhenRecordExists() throws SQLException {
        RepoMigration migration =
                buildMigration("existing-repo", "job-3", "nuget", MigrationStatus.MIGRATION_COMPLETED);
        stubResultSetForMigration();
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("existing-repo")))
                .thenReturn(resultSet);
        when(jdbcHelper.executeUpdate(any(QueryControls.class), anyString(), any(Object[].class))).thenReturn(1);

        int result = dao.upsert(migration);

        assertEquals(1, result);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeUpdate(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("UPDATE"));
    }

    @Test
    void findByJobIdReturnsNullWhenNotFound() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("non-existent")))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        RepoMigration result = dao.findByJobId("non-existent");

        assertNull(result);
    }

    @Test
    void findByJobIdReturnsMigrationWhenFound() throws SQLException {
        stubResultSetForMigration();
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("job-1")))
                .thenReturn(resultSet);

        RepoMigration result = dao.findByJobId("job-1");

        assertNotNull(result);
        assertEquals("test-repo", result.getRepoName());
        assertEquals("job-1", result.getJobId());
        assertEquals(MigrationStatus.MIGRATION_IN_PROGRESS, result.getMigrationStatus());
    }

    @Test
    void findByRepoNameReturnsNullWhenNotFound() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), eq("missing-repo")))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        RepoMigration result = dao.findByRepoName("missing-repo");

        assertNull(result);
    }

    @Test
    void queryBuildsCorrectFilters() throws SQLException {
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        List<RepoMigration> results = dao.query("nuget", "MIGRATION_IN_PROGRESS", true, "my-repo", 10, 0);

        assertTrue(results.isEmpty());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("package_type = ?"));
        assertTrue(sql.contains("migration_status = ?"));
        assertTrue(sql.contains("dry_run = ?"));
        assertTrue(sql.contains("repo_name = ?"));
        assertTrue(sql.contains("ORDER BY start_time DESC"));
        assertTrue(sql.contains("LIMIT ?"));
    }

    @Test
    void queryHandlesMultipleStatuses() throws SQLException {
        when(dbService.getDatabaseType()).thenReturn(DbType.POSTGRESQL);
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        dao.query(null, "MIGRATION_IN_PROGRESS,MIGRATION_COMPLETED", null, null, null, null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcHelper).executeSelect(any(QueryControls.class), sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("migration_status IN (?, ?)"));
    }

    @Test
    void countReturnsZeroWhenEmpty() throws SQLException {
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString(), any(Object[].class)))
                .thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(0L);

        long count = dao.count("nuget", null, null, null);

        assertEquals(0, count);
    }

    private RepoMigration buildMigration(String repoName, String jobId, String packageType,
            MigrationStatus status) {
        return RepoMigration.builder()
                .repoName(repoName)
                .jobId(jobId)
                .packageType(packageType)
                .migrationStatus(status)
                .dryRun(false)
                .startTime(new Timestamp(System.currentTimeMillis()))
                .build();
    }

    private void stubResultSetForMigration() throws SQLException {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("repo_name")).thenReturn("test-repo");
        when(resultSet.getString("job_id")).thenReturn("job-1");
        when(resultSet.getString("package_type")).thenReturn("nuget");
        when(resultSet.getString("migration_status")).thenReturn("MIGRATION_IN_PROGRESS");
        when(resultSet.getBoolean("dry_run")).thenReturn(false);
        when(resultSet.getString("target_repo")).thenReturn(null);
        when(resultSet.getString("repository_type")).thenReturn("remote");
        when(resultSet.getTimestamp("start_time")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(resultSet.getTimestamp("end_time")).thenReturn(null);
        when(resultSet.getTimestamp("scheduled_time")).thenReturn(null);
    }
}
