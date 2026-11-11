package org.artifactory.storage.db.monitor;

import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code system_states} table.
 * <p>
 * The table is a generic key-value store for operational runtime states. Each row is identified
 * by a composite key of {@code (use_case_name, id)}. The {@code data} column holds a JSON blob
 * whose schema is defined per use case.
 * <p>
 * Upsert is implemented as INSERT then UPDATE on duplicate-key, which is the cross-dialect
 * pattern established throughout this codebase (see {@code TransparentRepositoryConfigServiceImpl}).
 *
 * @author Eli Fishman
 */
@Repository
public class SystemStatesDao extends BaseDao {

    @Autowired
    public SystemStatesDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    /**
     * Inserts or updates a record identified by {@code (useCaseName, id)}.
     * Attempts INSERT first; on a duplicate-key violation it falls back to UPDATE.
     */
    public void upsert(String useCaseName, String id, long timestamp,
            String nodeId, String actor, String data) throws SQLException {
        try {
            jdbcHelper.executeUpdate(
                    "INSERT INTO system_states (use_case_name, id, timestamp, node_id, actor, data)"
                    + " VALUES (?, ?, ?, ?, ?, ?)",
                    useCaseName, id, timestamp, nodeId, actor, data);
        } catch (SQLException e) {
            if (!isUniqueConstraintViolation(e)) {
                throw e;
            }
            jdbcHelper.executeUpdate(
                    "UPDATE system_states"
                    + " SET timestamp = ?, node_id = ?, actor = ?, data = ?"
                    + " WHERE use_case_name = ? AND id = ?",
                    timestamp, nodeId, actor, data, useCaseName, id);
        }
    }

    /**
     * Returns all records for the given use case whose {@code timestamp} is strictly after
     * {@code sinceTimestamp} (epoch ms).
     */
    public List<SystemStateRecord> findByUseCaseSince(
            String useCaseName, long sinceTimestamp) throws SQLException {
        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(
                    "SELECT use_case_name, id, timestamp, node_id, actor, data"
                    + " FROM system_states"
                    + " WHERE use_case_name = ? AND timestamp > ?",
                    useCaseName, sinceTimestamp);
            List<SystemStateRecord> results = new ArrayList<>();
            while (rs.next()) {
                results.add(new SystemStateRecord(
                        rs.getString("use_case_name"),
                        rs.getString("id"),
                        rs.getLong("timestamp"),
                        rs.getString("node_id"),
                        rs.getString("actor"),
                        rs.getString("data")));
            }
            return results;
        } finally {
            DbUtils.close(rs);
        }
    }

    /**
     * Deletes the single record identified by {@code (useCaseName, id)}.
     */
    public int deleteByUseCaseAndId(String useCaseName, String id) throws SQLException {
        return jdbcHelper.executeUpdate(
                "DELETE FROM system_states WHERE use_case_name = ? AND id = ?",
                useCaseName, id);
    }

    /**
     * Deletes all records for the given use case whose {@code timestamp} is strictly before
     * {@code cutoffMs} (epoch ms). Used for time-based retention cleanup.
     */
    public int deleteByUseCaseOlderThan(String useCaseName, long cutoffMs) throws SQLException {
        return jdbcHelper.executeUpdate(
                "DELETE FROM system_states WHERE use_case_name = ? AND timestamp < ?",
                useCaseName, cutoffMs);
    }
}
