package org.artifactory.storage.db.federation.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.federation.DeferredDanglingRefCleanupDao;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the {@code virtual_repo_dangling_refs} table.
 * Stores dangling-ref cleanup work that outlived JFBus retry budget
 * or failed to publish.
 */
@Slf4j
@Repository
public class DeferredDanglingRefCleanupDaoImpl extends BaseDao
        implements DeferredDanglingRefCleanupDao {

    private static final String TABLE = "virtual_repo_dangling_refs";

    private static final String INSERT_SQL =
            "INSERT INTO " + TABLE
                    + " (virtual_repo_key, child_repo_key, created, modified)"
                    + " VALUES (?, ?, ?, ?)";

    private static final String UPDATE_MODIFIED_SQL =
            "UPDATE " + TABLE + " SET modified = ?"
                    + " WHERE virtual_repo_key = ? AND child_repo_key = ?";

    private static final String DELETE_SQL =
            "DELETE FROM " + TABLE
                    + " WHERE virtual_repo_key = ? AND child_repo_key = ?";

    private static final String SELECT_ALL_SQL =
            "SELECT virtual_repo_key, child_repo_key FROM " + TABLE;

    @Autowired
    public DeferredDanglingRefCleanupDaoImpl(
            ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    @Override
    public void upsert(String virtualRepoKey, String childRepoKey)
            throws SQLException {
        long now = System.currentTimeMillis();
        int updated = jdbcHelper.executeUpdate(
                UPDATE_MODIFIED_SQL, now, virtualRepoKey, childRepoKey);
        if (updated == 0) {
            jdbcHelper.executeUpdate(
                    INSERT_SQL, virtualRepoKey, childRepoKey, now, now);
        }
    }

    @Override
    public void delete(String virtualRepoKey, String childRepoKey)
            throws SQLException {
        jdbcHelper.executeUpdate(DELETE_SQL, virtualRepoKey, childRepoKey);
    }

    @Override
    public List<DeferredDanglingRefEntry> findAll() throws SQLException {
        List<DeferredDanglingRefEntry> result = new ArrayList<>();
        try (ResultSet rs = jdbcHelper.executeSelect(SELECT_ALL_SQL)) {
            while (rs.next()) {
                result.add(new DeferredDanglingRefEntry(
                        rs.getString("virtual_repo_key"),
                        rs.getString("child_repo_key")));
            }
        }
        return result;
    }
}
