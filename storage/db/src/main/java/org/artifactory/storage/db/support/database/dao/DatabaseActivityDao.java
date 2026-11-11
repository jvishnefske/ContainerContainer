package org.artifactory.storage.db.support.database.dao;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.support.database.model.DatabaseActivity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * A Data Access Object for Database Statistics Activity table
 *
 * @author vigneshc
 */
@Repository
@Slf4j
public class DatabaseActivityDao extends BaseDao {

    private final QueryControls controls;

    @Autowired
    public DatabaseActivityDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
        this.controls = QueryControls.builder().build();
    }

    public List<DatabaseActivity> getSlowQueriesFromDatabase(String slowQuerySqlStatement, DbType databaseType) {
        ResultSet resultSet = null;
        List<DatabaseActivity> databaseActivity = Lists.newArrayList();

        try {
            resultSet = jdbcHelper.executeSelect(controls, slowQuerySqlStatement);
            while (resultSet.next()) {
                databaseActivity.add(serverFromResultSet(resultSet, databaseType));
            }
        } catch (SQLException e) {
            log.error("Exception occurred while collecting Database Slow Queries file, - {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Cause: ", e);
            }
        } finally {
            DbUtils.close(resultSet);
        }
        return databaseActivity;
    }

    // wait_event_type and wait_event columns are only available in Postgresql database.
    // Hence, making this condition check and populating the DatabaseActivity object.
    private DatabaseActivity serverFromResultSet(ResultSet rs, DbType databaseType) throws SQLException {
        if (databaseType == DbType.POSTGRESQL) {
            return new DatabaseActivity(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getString(7));
        } else {
            return new DatabaseActivity(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), null, null,
                    rs.getString(5));
        }
    }
}