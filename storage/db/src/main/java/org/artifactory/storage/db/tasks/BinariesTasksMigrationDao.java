package org.artifactory.storage.db.tasks;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbType;
import org.jfrog.storage.binstore.providers.base.PersistedRemoteTask;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Shahar Palmor
 */
@Repository
@Slf4j
public class BinariesTasksMigrationDao extends BaseDao {

    private final DbType dbType;
    private static final String INSERT_SQL = """
            INSERT INTO binaries_tasks (id, sha1, time, local_repo_key, remote_url_hash, start_time, count, server_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Autowired
    public BinariesTasksMigrationDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.dbType = dbProperties.getDbType();
    }

    public List<PersistedRemoteTask> getTasks() throws SQLException {
        List<PersistedRemoteTask> tasks = new ArrayList<>();
        QueryWriter queryWriter = new QueryWriter(dbType);
        queryWriter.select("id, local_repo_key, sha1").from("binaries_tasks").limit(500L);
        try (ResultSet rs = jdbcHelper.executeSelect(queryWriter.build())) {
            while (rs.next()) {
                tasks.add(new PersistedRemoteTask(rs.getLong("id"), rs.getString("sha1"),
                        rs.getString("local_repo_key")));
            }
        }
        return tasks;
    }

    public int deleteTasksByIds(Set<Long> ids) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM binaries_tasks WHERE id IN (#)", ids);
    }

    public void createTasks(List<PersistedRemoteTask> tasks) throws SQLException {
        List<Object[]> params = preparePersistedTasksParamsForInsertion(tasks);
        jdbcHelper.executeBulkUpdate(INSERT_SQL, params);
    }

    private List<Object[]> preparePersistedTasksParamsForInsertion(List<PersistedRemoteTask> binariesTasks) {
        List<Object[]> params = new ArrayList<>();
        for (PersistedRemoteTask task : binariesTasks) {
            Object[] paramSet = new Object[8];
            paramSet[0] = task.getId();
            paramSet[1] = task.getSha1();
            paramSet[2] = System.currentTimeMillis();
            paramSet[3] = task.getRepoKey();
            paramSet[4] = null;
            paramSet[5] = null;
            paramSet[6] = 0;
            paramSet[7] = null;
            log.debug("About to created RemoteBinaryTask sha1: {}, localRepoKey: {}", task.getSha1(),
                    task.getRepoKey());
            params.add(paramSet);
        }
        return params;
    }
}
