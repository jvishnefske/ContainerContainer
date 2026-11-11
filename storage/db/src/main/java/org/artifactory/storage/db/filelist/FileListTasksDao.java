package org.artifactory.storage.db.filelist;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Dao for file list tasks
 *
 * @author Shahar Palmor
 */
@Repository
@Slf4j
public class FileListTasksDao extends BaseDao {

    private static final String TABLE_NAME = "file_list_tasks";
    private DbProperties dbProperties;
    private DbType dbType;

    @Autowired
    public FileListTasksDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    @Autowired
    public void setDbProperties(DbProperties dbProperties) {
        this.dbProperties = dbProperties;
    }

    @PostConstruct
    public void init() {
        this.dbType = this.dbProperties.getDbType();
    }

    private static final String INSERT_FILE_LIST_TASK = """
            INSERT INTO %s (repo, path, heartbeat) VALUES (?, ?, NULL)
            """.formatted(TABLE_NAME);

    private static final String GET_FILE_LIST_TASK = "select * from " + TABLE_NAME + " WHERE repo = ?";


    public void create(FileListInfo fileListInfo) throws SQLException {
        log.debug("Creating new file list task for repo {}", fileListInfo.getRepo());
        jdbcHelper.executeUpdate(INSERT_FILE_LIST_TASK,
                fileListInfo.getRepo(), fileListInfo.getPath());
    }

    public int updateInactiveTask(String repo, Long currentHeartBeat) throws SQLException {
        long limit = Instant.now()
                .minus(ConstantValues.federatedFileListTaskHeartbeatExpiredMinutes.getLong(), ChronoUnit.MILLIS)
                .toEpochMilli();
        log.trace("About to update hb {} for repo {}", currentHeartBeat, repo);
        return jdbcHelper.executeUpdate(
                "UPDATE " + TABLE_NAME + " SET heartbeat = ? WHERE repo = ? AND (heartbeat IS NULL OR heartbeat < " +
                        limit + ")",
                currentHeartBeat, repo);
    }

    public int update(String repo, Long currentHeartBeat) throws SQLException {
        log.trace("About to update hb {} for repo {}", currentHeartBeat, repo);
        return jdbcHelper.executeUpdate(
                "UPDATE " + TABLE_NAME + " SET heartbeat = ? WHERE repo = ?",
                currentHeartBeat, repo);
    }

    public void delete(String repo) throws SQLException {
        log.debug("Deleting file list task for repo {}", repo);
        jdbcHelper.executeUpdate("DELETE FROM " + TABLE_NAME + " WHERE repo = ?",
                repo);
    }

    public FileListInfo retrieveFileListTask(String repoKey) throws SQLException {
        FileListInfo fileListInfo = null;
        try (ResultSet resultSet = jdbcHelper.executeSelect(GET_FILE_LIST_TASK, repoKey)) {
            if (resultSet.next()) {
                fileListInfo = new FileListInfo(resultSet.getString("repo"), resultSet.getString("path"),
                        resultSet.getLong("heartbeat"));
            }
            return fileListInfo;
        }
    }

    public int getActiveTasksCount() throws SQLException {
        long limit = Instant.now()
                .minus(ConstantValues.federatedFileListTaskHeartbeatExpiredMinutes.getLong(), ChronoUnit.MILLIS)
                .toEpochMilli();
        return jdbcHelper.executeSelectCount(
                "select count(*) from " + TABLE_NAME + " WHERE heartbeat IS NOT NULL AND heartbeat > " +
                        limit);
    }

    public List<FileListInfo> getInactiveTasks() throws SQLException {
        List<FileListInfo> inactiveTasks = new ArrayList<>();

        long limit = Instant.now()
                .minus(ConstantValues.federatedFileListTaskHeartbeatExpiredMinutes.getLong(), ChronoUnit.MILLIS)
                .toEpochMilli();
        String query = new QueryWriter(this.dbType)
                .select("repo, path, heartbeat")
                .from(TABLE_NAME)
                .where("heartbeat IS null OR heartbeat < " + limit)
                .build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query)) {
            FileListInfo fileListInfo = null;
            while (resultSet.next()) {
                fileListInfo = new FileListInfo(resultSet.getString("repo"), resultSet.getString("path"),
                        resultSet.getLong("heartbeat"));
                inactiveTasks.add(fileListInfo);
            }
            return inactiveTasks;
        }
    }
}
