package org.artifactory.storage.db.fs.dao;

import com.jfrog.ph.acl.model.migration.MigrationStatus;
import com.jfrog.ph.acl.model.migration.RepoMigration;
import com.jfrog.ph.acl.service.migration.RepoMigrationsPersistence;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.util.DbUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO implementation for {@code repo_migrations} table.
 * pattern: interface in package-handler-acl,
 * implementation in artifactory-oss storage layer.
 *
 * @author gyanarp
 */
@Slf4j
public class RepoMigrationsDaoImpl extends BaseDao implements RepoMigrationsPersistence {

    public static final String TABLE_NAME = "repo_migrations";
    private static final QueryControls DEFAULT_CONTROLS = QueryControls.builder().build();
    private static final String SELECT_ALL_FROM = "SELECT * FROM ";
    private static final int SAFETY_MAX_LIMIT = 1000;
    private static final int SAFETY_DEFAULT_LIMIT = 100;

    private final DbService dbService;

    public RepoMigrationsDaoImpl(ArtifactoryJdbcHelper jdbcHelper, DbService dbService) {
        super(jdbcHelper);
        this.dbService = dbService;
    }

    private int insert(RepoMigration migration) throws SQLException {
        log.trace("Executing INSERT for migration jobId: {}, repo: {}, status: {}",
                migration.getJobId(), migration.getRepoName(), migration.getMigrationStatus());

        String sql = "INSERT INTO " + TABLE_NAME + " " +
                "(repo_name, start_time, end_time, dry_run, package_type, migration_status, " +
                "target_repo, job_id, repository_type, scheduled_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        return jdbcHelper.executeUpdate(DEFAULT_CONTROLS, sql,
                migration.getRepoName(),
                migration.getStartTime(),
                migration.getEndTime(),
                migration.getDryRun(),
                migration.getPackageType(),
                toStatusString(migration.getMigrationStatus()),
                migration.getTargetRepo(),
                migration.getJobId(),
                migration.getRepositoryType(),
                migration.getScheduledTime());
    }

    private int update(RepoMigration migration) throws SQLException {
        log.trace("Executing UPDATE for migration jobId: {}, repo: {}, status: {}",
                migration.getJobId(), migration.getRepoName(), migration.getMigrationStatus());

        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "start_time = ?, end_time = ?, dry_run = ?, " +
                "package_type = ?, migration_status = ?, target_repo = ?, " +
                "job_id = ?, repository_type = ?, " +
                "scheduled_time = ? " +
                "WHERE repo_name = ?";

        return jdbcHelper.executeUpdate(DEFAULT_CONTROLS, sql,
                migration.getStartTime(),
                migration.getEndTime(),
                migration.getDryRun(),
                migration.getPackageType(),
                toStatusString(migration.getMigrationStatus()),
                migration.getTargetRepo(),
                migration.getJobId(),
                migration.getRepositoryType(),
                migration.getScheduledTime(),
                migration.getRepoName());
    }

    @Override
    public int upsert(RepoMigration migration) throws SQLException {
        log.trace("Executing UPSERT for migration jobId: {}, repo: {}, status: {}",
                migration.getJobId(), migration.getRepoName(), migration.getMigrationStatus());

        RepoMigration existing = findByRepoName(migration.getRepoName());
        if (existing != null) {
            return update(migration);
        }
        return insert(migration);
    }

    @Override
    public RepoMigration findByJobId(String jobId) throws SQLException {
        log.trace("Querying migration by jobId: {}", jobId);
        String sql = SELECT_ALL_FROM + TABLE_NAME + " WHERE job_id = ?";
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
    public RepoMigration findByRepoName(String repoName) throws SQLException {
        log.trace("Querying migration by repoName: {}", repoName);
        String sql = SELECT_ALL_FROM + TABLE_NAME + " WHERE repo_name = ?";
        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(DEFAULT_CONTROLS, sql, repoName);
            if (rs.next()) {
                return mapFromResultSet(rs);
            }
            return null;
        } finally {
            DbUtils.close(rs);
        }
    }

    @Override
    public List<RepoMigration> query(String packageType, String migrationStatus, Boolean dryRun,
            String repoName, Integer limit, Integer offset) throws SQLException {
        log.trace("Querying migrations with filters: packageType={}, status={}, dryRun={}, repoName={}, "
                + "limit={}, offset={}", packageType, migrationStatus, dryRun, repoName, limit, offset);

        StringBuilder sql = new StringBuilder(SELECT_ALL_FROM + TABLE_NAME + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        addFilter(sql, params, "package_type", packageType);
        addMigrationStatusFilter(sql, params, migrationStatus);
        addBooleanFilter(sql, params, "dry_run", dryRun);
        addFilter(sql, params, "repo_name", repoName);

        sql.append(" ORDER BY start_time DESC");
        addPagination(sql, params, limit, offset);

        return executeQuery(sql.toString(), params);
    }

    @Override
    public long count(String packageType, String migrationStatus, Boolean dryRun,
            String repoName) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE 1=1");
        List<Object> params = new ArrayList<>();

        addFilter(sql, params, "package_type", packageType);
        addMigrationStatusFilter(sql, params, migrationStatus);
        addBooleanFilter(sql, params, "dry_run", dryRun);
        addFilter(sql, params, "repo_name", repoName);

        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(DEFAULT_CONTROLS, sql.toString(), params.toArray());
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } finally {
            DbUtils.close(rs);
        }
    }

    private void addFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (value != null && !value.isEmpty()) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(value);
        }
    }

    private void addBooleanFilter(StringBuilder sql, List<Object> params, String column, Boolean value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(value);
        }
    }

    private void addMigrationStatusFilter(StringBuilder sql, List<Object> params, String migrationStatus) {
        if (migrationStatus == null || migrationStatus.isEmpty()) {
            return;
        }
        String[] statusValues = migrationStatus.split(",");
        if (statusValues.length == 1) {
            sql.append(" AND migration_status = ?");
            params.add(statusValues[0].trim());
        } else {
            sql.append(" AND migration_status IN (");
            for (int i = 0; i < statusValues.length; i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                params.add(statusValues[i].trim());
            }
            sql.append(")");
        }
    }

    private void addPagination(StringBuilder sql, List<Object> params, Integer limit, Integer offset) {
        int effectiveLimit = (limit != null && limit > 0)
                ? Math.min(limit, SAFETY_MAX_LIMIT) : SAFETY_DEFAULT_LIMIT;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;

        DbType dbType = dbService.getDatabaseType();
        if (dbType == DbType.POSTGRESQL || dbType == DbType.MYSQL || dbType == DbType.MARIADB) {
            sql.append(" LIMIT ?");
            params.add(effectiveLimit);
            if (effectiveOffset > 0) {
                sql.append(" OFFSET ?");
                params.add(effectiveOffset);
            }
        } else {
            sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            params.add(effectiveOffset);
            params.add(effectiveLimit);
        }
    }

    private List<RepoMigration> executeQuery(String sql, List<Object> params) throws SQLException {
        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(DEFAULT_CONTROLS, sql, params.toArray());
            List<RepoMigration> migrations = new ArrayList<>();
            while (rs.next()) {
                migrations.add(mapFromResultSet(rs));
            }
            return migrations;
        } finally {
            DbUtils.close(rs);
        }
    }

    private RepoMigration mapFromResultSet(ResultSet rs) throws SQLException {
        return RepoMigration.builder()
                .repoName(rs.getString("repo_name"))
                .startTime(rs.getTimestamp("start_time"))
                .endTime(rs.getTimestamp("end_time"))
                .dryRun(rs.getBoolean("dry_run"))
                .packageType(rs.getString("package_type"))
                .migrationStatus(MigrationStatus.fromString(rs.getString("migration_status")))
                .targetRepo(rs.getString("target_repo"))
                .jobId(rs.getString("job_id"))
                .repositoryType(rs.getString("repository_type"))
                .scheduledTime(rs.getTimestamp("scheduled_time"))
                .build();
    }

    private String toStatusString(MigrationStatus status) {
        return status != null ? status.name() : null;
    }
}
