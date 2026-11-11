package org.artifactory.storage.db.traffic.summary.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.traffic.summary.entity.TrafficSummaryEntity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO implementation for traffic_summary table operations
 *
 * @author kunalm
 */
@Slf4j
@Repository
public class TrafficSummaryDao extends BaseDao {

    public TrafficSummaryDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    /**
     * Retrieves traffic summary records within a date range
     *
     * @param startTime start time (inclusive) in milliseconds
     * @param endTime   end time (inclusive) in milliseconds
     * @return list of traffic summary entities within the specified date range
     */
    public List<TrafficSummaryEntity> getByDateRange(long startTime, long endTime) {
        try {
            String sql = "SELECT id, time, total_request_count, upload_request_count, download_request_count, " +
                    "total_bytes, upload_bytes, download_bytes " +
                    "FROM traffic_summary " +
                    "WHERE time >= ? AND time <= ? " +
                    "ORDER BY time DESC";

            return mapToEntityList(jdbcHelper.executeSelect(sql, startTime, endTime));
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve traffic summary records by date range", e);
        }
    }

    /**
     * Inserts a traffic summary record
     *
     * @param entity TrafficSummaryEntity
     */
    public void insert(TrafficSummaryEntity entity) {
        if (entity == null) {
            return;
        }

        try {
            String sql = "INSERT INTO traffic_summary (time, total_request_count, upload_request_count, " +
                    "download_request_count, total_bytes, upload_bytes, download_bytes) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";

            jdbcHelper.executeUpdate(sql,
                    entity.time(),
                    entity.totalRequests(),
                    entity.uploadRequests(),
                    entity.downloadRequests(),
                    entity.totalBytes(),
                    entity.uploadBytes(),
                    entity.downloadBytes()
            );
        } catch (SQLException e) {
            throw new StorageException("Failed to insert traffic summary records in bulk", e);
        }
    }

    /**
     * Deletes traffic summary records within a date range
     *
     * @param thresholdTime threshold time in milliseconds; records older than this will be deleted
     */
    public void deleteByThresholdTime(long thresholdTime) {
        try {
            String sql = "DELETE FROM traffic_summary WHERE time < ?";
            jdbcHelper.executeUpdate(sql, thresholdTime);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete traffic summary records before threshold date", e);
        }
    }

    private List<TrafficSummaryEntity> mapToEntityList(ResultSet rs) throws SQLException {
        List<TrafficSummaryEntity> results = new ArrayList<>();
        while (rs.next()) {
            results.add(TrafficSummaryEntity.builder()
                    .id(rs.getLong("id"))
                    .time(rs.getLong("time"))
                    .totalRequests(rs.getLong("total_request_count"))
                    .uploadRequests(rs.getLong("upload_request_count"))
                    .downloadRequests(rs.getLong("download_request_count"))
                    .totalBytes(rs.getLong("total_bytes"))
                    .uploadBytes(rs.getLong("upload_bytes"))
                    .downloadBytes(rs.getLong("download_bytes"))
                    .build());
        }
        return results;
    }
}
