package org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query;

import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class AddIndexOnRepoAndEventHandler {
    private static final Logger log = LoggerFactory.getLogger(AddIndexOnRepoAndEventHandler.class);
    private final DbType dbType;
    private static final String ADD_INDEX_DERBY =
            "CREATE INDEX node_events_repo_event_id on node_events (repo,event_id)";
    private static final String ADD_INDEX_POSTGRES =
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS node_events_repo_event_id ON node_events (repo, event_id)";
    private static final String ADD_INDEX_MARIA =
            "ALTER TABLE node_events ADD INDEX node_events_repo_event_id (repo,event_id), ALGORITHM=INPLACE, LOCK=NONE";
    private static final String ADD_INDEX_MSSQL =
            """
            IF CONVERT(VARCHAR, (SELECT SERVERPROPERTY ('edition'))) like '%Enterprise%'
                CREATE INDEX node_events_repo_event_id ON node_events(repo, event_id) WITH (ONLINE = ON)
            ELSE
                CREATE INDEX node_events_repo_event_id ON node_events(repo, event_id);
            """;
    private static final String ADD_INDEX_ORACLE =
            "CREATE INDEX node_events_repo_event_id ON node_events (repo, event_id)";
    private static final String ONLINE_INDEX_CHECK_ORACLE =
            "SELECT COUNT(*) AS cnt FROM (SELECT BANNER FROM v$version WHERE LOWER(BANNER) LIKE '%enterprise%')";
    private static final String ONLINE_INDEX_ORACLE = "ONLINE";


    public AddIndexOnRepoAndEventHandler(DbType dbType) {
        this.dbType = dbType;
    }

    public String getQueryAddIndexByDBType() {
        String sqlQuery;
        switch (this.dbType) {
            case DERBY -> sqlQuery = ADD_INDEX_DERBY;
            case POSTGRESQL -> sqlQuery = ADD_INDEX_POSTGRES;
            case ORACLE -> sqlQuery = ADD_INDEX_ORACLE;
            case MARIADB, MYSQL -> sqlQuery = ADD_INDEX_MARIA;
            case MSSQL -> sqlQuery = ADD_INDEX_MSSQL;
            default -> {
                log.info("Data base is not supported: ", this, dbType);
                throw new IllegalArgumentException();
            }
        }
        return sqlQuery;
    }

    public String getQueryCheckOnlineIndexSupportedByDBType() {
        String sqlQuery = "";
        if (Objects.requireNonNull(this.dbType) == DbType.ORACLE) {
            sqlQuery = ONLINE_INDEX_CHECK_ORACLE;
        }
        return sqlQuery;
    }

    public String getQueryOnlineIndexCmdByDBType() {
        String sqlQuery = "";
        if (Objects.requireNonNull(this.dbType) == DbType.ORACLE) {
            sqlQuery = ONLINE_INDEX_ORACLE;
        }
        return sqlQuery;
    }
}
