package org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query;

import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateRepoColumnHandler {
    private static final Logger log = LoggerFactory.getLogger(UpdateRepoColumnHandler.class);
    private DbType dbType;
    private static final String UPDATE_REPO_DERBY =
            "UPDATE node_events SET repo = substr(path, 1, LOCATE('/', path) - 1) where event_id in (select EVENT_ID " +
                    "from node_events " +
                    "where repo is null fetch first %s rows only)";
    private static final String UPDATE_REPO_POSTGRES =
            "UPDATE node_events SET repo = substr(path, 0, POSITION('/' IN path)) where event_id in (select event_id " +
                    "from node_events " +
                    "where repo is null LIMIT %s)";
    private static final String UPDATE_REPO_MARIA =
            "UPDATE node_events SET repo = SUBSTRING(path, 1, INSTR(path, '/') - 1) where repo is null limit %s";
    private static final String UPDATE_REPO_MSSQL =
            "UPDATE node_events SET repo = SUBSTRING(path, 1, CHARINDEX('/', path) - 1) where event_id in (select top" +
                    " %s " +
                    "event_id from node_events where repo is null)";
    private static final String UPDATE_REPO_ORACLE =
            "UPDATE node_events SET repo = SUBSTR(path, 1, INSTR(path, '/') - 1) where repo is null and ROWNUM <=%s";

    public UpdateRepoColumnHandler(DbType dbType) {
        this.dbType = dbType;
    }

    public String getQueryByDBType() {
        String sqlQuery;
        switch (this.dbType) {
            case DERBY -> sqlQuery = UPDATE_REPO_DERBY;
            case POSTGRESQL -> sqlQuery = UPDATE_REPO_POSTGRES;
            case ORACLE -> sqlQuery = UPDATE_REPO_ORACLE;
            case MARIADB, MYSQL -> sqlQuery = UPDATE_REPO_MARIA;
            case MSSQL -> sqlQuery = UPDATE_REPO_MSSQL;
            default -> {
                log.info("Data base is not supported: ", this, dbType);
                throw new IllegalArgumentException();
            }
        }
        return sqlQuery;
    }
}
