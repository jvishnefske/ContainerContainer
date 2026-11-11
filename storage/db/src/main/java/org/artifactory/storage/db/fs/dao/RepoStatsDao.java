package org.artifactory.storage.db.fs.dao;

import lombok.Setter;
import org.artifactory.repo.RepoStatDto;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Data Access Object for repository_stats table.
 */
@Repository
public class RepoStatsDao extends BaseDao {

    private static final String TABLE_NAME = "repository_stats";
    private static final String REPO_KEY_COLUMN = "repository_key";
    private static final String LAST_STATISTICS_MODIFIED_COLUMN = "last_statistics_updated";
    private static final String LAST_DOWNLOADED_COLUMN = "last_downloaded";
    private static final String LAST_UPLOADED_COLUMN = "last_uploaded";

    private static final String GET_QUERY = "SELECT * FROM "
            + TABLE_NAME + " WHERE " + REPO_KEY_COLUMN + " = ?";

    private static final String GET_ALL_BY_DELTA_QUERY = "SELECT * FROM "
            + TABLE_NAME + " WHERE " + LAST_STATISTICS_MODIFIED_COLUMN + " > ?";

    private static final String UPDATE_LAST_DOWNLOADED = "UPDATE " + TABLE_NAME + " SET "
            + LAST_DOWNLOADED_COLUMN + " = ?, " + LAST_STATISTICS_MODIFIED_COLUMN + " = ? "
            + "WHERE " + REPO_KEY_COLUMN  + " = ? AND " + LAST_DOWNLOADED_COLUMN + " < ?";

    private static final String UPDATE_LAST_UPLOADED = "UPDATE " + TABLE_NAME + " SET "
            + LAST_UPLOADED_COLUMN + " = ?, " + LAST_STATISTICS_MODIFIED_COLUMN + " = ? "
            + " WHERE " + REPO_KEY_COLUMN  + " = ? AND " + LAST_UPLOADED_COLUMN + " < ?";

    private static final String INSERT_QUERY = "INSERT INTO " + TABLE_NAME +  "("
            + REPO_KEY_COLUMN + ", " + LAST_DOWNLOADED_COLUMN + ", " +
            LAST_UPLOADED_COLUMN + ", " + LAST_STATISTICS_MODIFIED_COLUMN + ") VALUES (?, ?, ?, ?)";

    public RepoStatsDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    @Setter
    private LongSupplier currentTimeSupplier = System::currentTimeMillis;

    public void save(String repoKey, RepoStatDto stat) throws SQLException {
        jdbcHelper.executeUpdate(INSERT_QUERY, repoKey,
                stat.getLastDownloaded(), stat.getLastUploaded(), currentTimeSupplier.getAsLong());
    }

    public RepoStatDto get(String repoKey) throws SQLException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(GET_QUERY, repoKey)) {
            if (resultSet.next()) {
                return mapRepoStat(resultSet);
            }
            return null;
        }
    }

    public List<RepoStatDto> getAllByDelta(Long currentTime) throws SQLException {
        List<RepoStatDto> stats = new ArrayList<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(GET_ALL_BY_DELTA_QUERY, currentTime)) {
            while (resultSet.next()) {
                stats.add(mapRepoStat(resultSet));
            }
            return stats;
        }
    }

    public void update(String repoKey, RepoStatDto stat) throws SQLException {
        if (stat.getLastDownloaded() > 0) {
            jdbcHelper.executeUpdate(UPDATE_LAST_DOWNLOADED, stat.getLastDownloaded(), currentTimeSupplier.getAsLong(),
                    repoKey, stat.getLastDownloaded());
        }
        if (stat.getLastUploaded() > 0) {
            jdbcHelper.executeUpdate(UPDATE_LAST_UPLOADED, stat.getLastUploaded(), currentTimeSupplier.getAsLong(),
                    repoKey, stat.getLastUploaded());
        }
    }

    public boolean exists(String repoKey) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select("1 as keyExists")
                .from(TABLE_NAME)
                .where(REPO_KEY_COLUMN + "=?")
                .limit(1L)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query, repoKey)) {
            return resultSet.next();
        }
    }

    public void upsert(String repoKey, RepoStatDto repoStat) throws SQLException {
        if (exists(repoKey)) {
            update(repoKey, repoStat);
        } else {
            save(repoKey, repoStat);
        }
    }

    // Used only for testing purposes
    public void deleteAll() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM " + TABLE_NAME);
    }

    private RepoStatDto mapRepoStat(ResultSet resultSet) throws SQLException {
        return RepoStatDto.builder()
                .repoKey(resultSet.getString(REPO_KEY_COLUMN))
                .lastStatisticsModified(resultSet.getLong(LAST_STATISTICS_MODIFIED_COLUMN))
                .lastDownloaded(resultSet.getLong(LAST_DOWNLOADED_COLUMN))
                .lastUploaded(resultSet.getLong(LAST_UPLOADED_COLUMN))
                .build();
    }
}
