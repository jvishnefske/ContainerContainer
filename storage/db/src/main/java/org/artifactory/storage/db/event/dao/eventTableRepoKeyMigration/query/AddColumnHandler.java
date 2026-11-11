package org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query;


import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class AddColumnHandler {
    private static final Logger log = LoggerFactory.getLogger(AddColumnHandler.class);
    private final DbType dbType;
    private static final String ADD_COLUMN_DERBY = "ALTER TABLE node_events ADD COLUMN repo varchar(64) DEFAULT NULL";
    private static final String ADD_COLUMN_POSTGRES =
            "ALTER TABLE node_events ADD COLUMN repo varchar(64) DEFAULT NULL";
    private static final String ADD_COLUMN_MARIA =
            "ALTER TABLE node_events ADD COLUMN repo varchar(64) DEFAULT NULL, ALGORITHM=INPLACE, LOCK=NONE";
    private static final String ADD_COLUMN_MSSQL = "ALTER TABLE node_events ADD repo varchar(64) DEFAULT NULL";
    private static final String ADD_COLUMN_ORACLE = "ALTER TABLE node_events ADD repo varchar(64) DEFAULT NULL";

    public AddColumnHandler(DbType dbType) {
        this.dbType = dbType;
    }

    public String getQueryByDBType() {
        String sqlQuery;
        switch (this.dbType) {
            case DERBY -> sqlQuery = ADD_COLUMN_DERBY;
            case POSTGRESQL -> sqlQuery = ADD_COLUMN_POSTGRES;
            case ORACLE -> sqlQuery = ADD_COLUMN_ORACLE;
            case MARIADB, MYSQL -> sqlQuery = ADD_COLUMN_MARIA;
            case MSSQL -> sqlQuery = ADD_COLUMN_MSSQL;
            default -> {
                log.info("Data base is not supported: ", this, dbType);
                throw new IllegalArgumentException();
            }
        }
        return sqlQuery;
    }
}
