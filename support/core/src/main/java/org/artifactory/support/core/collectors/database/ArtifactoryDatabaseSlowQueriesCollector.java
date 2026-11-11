package org.artifactory.support.core.collectors.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.support.database.dao.DatabaseActivityDao;
import org.artifactory.storage.db.support.database.model.DatabaseActivity;
import org.jfrog.common.JsonUtils;
import org.jfrog.storage.DbType;
import org.jfrog.support.common.core.collectors.system.DatabaseSlowQueriesCollector;

import java.util.List;

import static org.artifactory.support.constant.SupportBundleConstants.EMPTY;
import static org.artifactory.support.constant.SupportBundleConstants.EMPTY_RESPONSE_FROM_DATABASE;
import static org.artifactory.support.constant.SupportBundleConstants.MSSQL_SLOW_QUERY_STATEMENT;
import static org.artifactory.support.constant.SupportBundleConstants.MYSQL_AND_MARIADB_SLOW_QUERY_STATEMENT;
import static org.artifactory.support.constant.SupportBundleConstants.POSTGRESQL_SLOW_QUERY_STATEMENT;
import static org.artifactory.support.constant.SupportBundleConstants.SUPPORTED;
import static org.artifactory.support.constant.SupportBundleConstants.UN_SUPPORTED_DERBY;
import static org.artifactory.support.constant.SupportBundleConstants.UN_SUPPORTED_DERBY_DATABASE_RESPONSE;
import static org.artifactory.support.constant.SupportBundleConstants.UN_SUPPORTED_ORACLE;
import static org.artifactory.support.constant.SupportBundleConstants.UN_SUPPORTED_ORACLE_DATABASE_RESPONSE;

/**
 * Database Slow Queries Collector
 *
 * @author vigneshc
 */
@Slf4j
@RequiredArgsConstructor
public class ArtifactoryDatabaseSlowQueriesCollector extends DatabaseSlowQueriesCollector {

    private final DbService dbService;
    private final JsonUtils jacksonMapper;
    private final DatabaseActivityDao databaseActivityDao;

    @Override
    protected String collectSlowQueriesFromDatabase() {
        String slowQuerySqlStatement = null;
        String status = null;
        String slowQueries = null;
        DbType databaseType = dbService.getDatabaseType();
        switch (databaseType) {
            case POSTGRESQL -> slowQuerySqlStatement = POSTGRESQL_SLOW_QUERY_STATEMENT;
            case MYSQL, MARIADB -> slowQuerySqlStatement = MYSQL_AND_MARIADB_SLOW_QUERY_STATEMENT;
            case MSSQL -> slowQuerySqlStatement = MSSQL_SLOW_QUERY_STATEMENT;
            case DERBY -> status = UN_SUPPORTED_DERBY;
            case ORACLE -> status = UN_SUPPORTED_ORACLE;
            default -> {
                // Do Nothing
            }
        }
        if (slowQuerySqlStatement != null) {
            List<DatabaseActivity> databaseActivity =
                    databaseActivityDao.getSlowQueriesFromDatabase(slowQuerySqlStatement, databaseType);
            if (!databaseActivity.isEmpty()) {
                status = SUPPORTED;
            } else {
                status = EMPTY;
            }
            slowQueries = writeSlowQueries(status, databaseActivity);
        } else {
            slowQueries = writeSlowQueries(status, null);
        }
        return slowQueries;
    }

    private String writeSlowQueries(String status, List<DatabaseActivity> databaseActivities) {
        String content = null;
        switch (status) {
            case EMPTY -> content = EMPTY_RESPONSE_FROM_DATABASE;
            case UN_SUPPORTED_DERBY -> content = UN_SUPPORTED_DERBY_DATABASE_RESPONSE;
            case UN_SUPPORTED_ORACLE -> content = UN_SUPPORTED_ORACLE_DATABASE_RESPONSE;
            case SUPPORTED -> content = jacksonMapper.valueToString(databaseActivities, true);
            default -> {
                // Do Nothing
            }
        }
        return content;
    }
}