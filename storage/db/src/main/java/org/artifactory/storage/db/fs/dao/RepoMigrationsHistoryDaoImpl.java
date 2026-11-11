package org.artifactory.storage.db.fs.dao;

import com.jfrog.ph.acl.model.migration.MigrationStatus;
import com.jfrog.ph.acl.model.migration.RepoMigrationHistory;
import com.jfrog.ph.acl.service.migration.RepoMigrationsHistoryPersistence;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.util.DbUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO implementation for {@code repo_migrations_history} table.
 * pattern: interface in package-handler-acl,
 * implementation in artifactory-oss storage layer.
 *
 * @author gyanarp
 */
@Slf4j
public class RepoMigrationsHistoryDaoImpl extends BaseDao implements RepoMigrationsHistoryPersistence {

    public static final String TABLE_NAME = "repo_migrations_history";
    private static final String SELECT_FROM_SQL = "SELECT * FROM " + TABLE_NAME;
    private static final QueryControls DEFAULT_CONTROLS = QueryControls.builder().build();

    public RepoMigrationsHistoryDaoImpl(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    @Override
    public int upsert(RepoMigrationHistory history) throws SQLException {
        log.trace("Executing UPSERT for history jobId: {}, status: {}",
                history.getJobId(), history.getMigrationStatus());

        RepoMigrationHistory existing = findByJobId(history.getJobId());
        if (existing != null) {
            return updateByJobId(history);
        }
        return insertHistory(history);
    }

    private int insertHistory(RepoMigrationHistory history) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " " +
                "(job_id, repo_name, target_repo, " +
                "migration_status, start_time, end_time) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        return jdbcHelper.executeUpdate(DEFAULT_CONTROLS, sql,
                history.getJobId(),
                history.getRepoName(),
                history.getTargetRepo(),
                toStatusString(history.getMigrationStatus()),
                history.getStartTime(),
                history.getEndTime());
    }

    private int updateByJobId(RepoMigrationHistory history) throws SQLException {
        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "repo_name = ?, target_repo = ?, " +
                "migration_status = ?, start_time = ?, end_time = ? " +
                "WHERE job_id = ?";

        return jdbcHelper.executeUpdate(DEFAULT_CONTROLS, sql,
                history.getRepoName(),
                history.getTargetRepo(),
                toStatusString(history.getMigrationStatus()),
                history.getStartTime(),
                history.getEndTime(),
                history.getJobId());
    }

    @Override
    public RepoMigrationHistory findByJobId(String jobId) throws SQLException {
        log.trace("Querying history by jobId: {}", jobId);
        String sql = SELECT_FROM_SQL + " WHERE job_id = ?";
        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(DEFAULT_CONTROLS, sql, jobId);
            if (rs.next()) {
                return mapFromResultSet(rs);
            }
            return null;
        } finally {
            DbUtils.close(rs);
        }
    }

    @Override
    public List<RepoMigrationHistory> findByRepoName(String repoName) throws SQLException {
        log.trace("Querying history by repoName: {}", repoName);
        String sql = SELECT_FROM_SQL + " WHERE repo_name = ? ORDER BY start_time DESC";
        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(DEFAULT_CONTROLS, sql, repoName);
            List<RepoMigrationHistory> history = new ArrayList<>();
            while (rs.next()) {
                history.add(mapFromResultSet(rs));
            }
            return history;
        } finally {
            DbUtils.close(rs);
        }
    }

    @Override
    public List<RepoMigrationHistory> query(String jobId, String repoName, String status)
            throws SQLException {
        log.trace("Querying history with filters: jobId={}, repoName={}, status={}",
                jobId, repoName, status);
        StringBuilder sql = new StringBuilder(SELECT_FROM_SQL + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (jobId != null && !jobId.isEmpty()) {
            sql.append(" AND job_id = ?");
            params.add(jobId);
        }
        if (repoName != null && !repoName.isEmpty()) {
            sql.append(" AND repo_name = ?");
            params.add(repoName);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND migration_status = ?");
            params.add(status);
        }

        sql.append(" ORDER BY start_time DESC");

        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(DEFAULT_CONTROLS, sql.toString(), params.toArray());
            List<RepoMigrationHistory> history = new ArrayList<>();
            while (rs.next()) {
                history.add(mapFromResultSet(rs));
            }
            return history;
        } finally {
            DbUtils.close(rs);
        }
    }

    private RepoMigrationHistory mapFromResultSet(ResultSet rs) throws SQLException {
        return RepoMigrationHistory.builder()
                .jobId(rs.getString("job_id"))
                .repoName(rs.getString("repo_name"))
                .targetRepo(rs.getString("target_repo"))
                .migrationStatus(MigrationStatus.fromString(rs.getString("migration_status")))
                .startTime(rs.getTimestamp("start_time"))
                .endTime(rs.getTimestamp("end_time"))
                .build();
    }

    private String toStatusString(MigrationStatus status) {
        return status != null ? status.name() : null;
    }
}
