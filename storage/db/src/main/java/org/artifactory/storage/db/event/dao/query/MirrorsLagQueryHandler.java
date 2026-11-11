package org.artifactory.storage.db.event.dao.query;

import org.artifactory.storage.event.cursor.RawMirrorLagInfo;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class MirrorsLagQueryHandler {
    private static final Logger log = LoggerFactory.getLogger(MirrorsLagQueryHandler.class);

    private DbType dbType;

    private static final String SELECT_LAG_FEDERATED_CURSORS_DERBY =
            "select federated_cursors.*, (select timestamp  from node_events ne where repo = federated_cursors" +
                    ".local_repo and federated_cursors.event_marker < ne.event_id " +
                    "and ne.hints is null fetch first 1 rows only) as tsNextEvent, federated_cursors.local_repo from " +
                    "(select operator_id, event_marker, SUBSTR(operator_id, 1, LOCATE('-federated_replication', " +
                    "operator_id)-1) local_repo from node_event_cursor where type = 'FEDERATED_REPLICATION' and " +
                    "status = 'HEALTHY') federated_cursors order by tsNextEvent fetch first %s rows only";
    private static final String SELECT_LAG_FEDERATED_CURSORS_POSTGRES =
            "select federated_cursors.*, (select timestamp  from node_events ne where repo = federated_cursors" +
                    ".local_repo and federated_cursors.event_marker < ne.event_id " +
                    "and ne.hints is null LIMIT 1) as tsNextEvent, federated_cursors.local_repo from (select " +
                    "operator_id, event_marker, substr(operator_id, 0, POSITION('-federated_replication' IN " +
                    "operator_id)) local_repo from node_event_cursor where type = 'FEDERATED_REPLICATION' and status " +
                    "= 'HEALTHY') federated_cursors order by tsNextEvent LIMIT %s";
    private static final String SELECT_LAG_FEDERATED_MYSQL_MARIA = "select federated_cursors.*, (select timestamp" +
            " from node_events ne where repo = federated_cursors.local_repo " +
            "and federated_cursors.event_marker < ne.event_id and ne.hints is null limit 1 ) tsNextEvent, " +
            "federated_cursors.local_repo from (select operator_id, event_marker, SUBSTR(operator_id, 1, INSTR" +
            "(operator_id, '-federated_replication') - 1) local_repo from node_event_cursor where type = " +
            "'FEDERATED_REPLICATION' and status = 'HEALTHY' ) federated_cursors order by " +
            "tsNextEvent is null, tsNextEvent limit %s";
    private static final String SELECT_LAG_FEDERATED_CURSORS_MSSQL =
            "Select top %s * From (select federated_cursors.*, (Select top 1 * From (select timestamp from " +
                    "node_events ne where repo = federated_cursors.local_repo" +
                    " and federated_cursors.event_marker < ne.event_id and ne.hints is null ) aa ) tsNextEvent from " +
                    "(select operator_id, event_marker, SUBSTRING(operator_id, 1, CHARINDEX('-federated_replication'," +
                    " operator_id) - 1) local_repo from node_event_cursor where type = 'FEDERATED_REPLICATION' and " +
                    "status = 'HEALTHY') federated_cursors ) aa order by tsNextEvent";
    private static final String SELECT_LAG_FEDERATED_CURSORS_ORACLE =
            "select * from (select federated_cursors.*, (select timestamp from node_events ne where repo = SUBSTR" +
                    "(operator_id, 0, " +
                    "INSTR(operator_id, '-federated_replication')-1) and " +
                    "federated_cursors.event_marker < ne.event_id and ne.hints is null and ROWNUM <=1) tsNextEvent " +
                    "from (select operator_id, event_marker, SUBSTR(operator_id, 0, INSTR(operator_id, " +
                    "'-federated_replication')) local_repo from node_event_cursor where type = " +
                    "'FEDERATED_REPLICATION' and status = 'HEALTHY' ) federated_cursors order by tsNextEvent) where " +
                    "ROWNUM <=%s";


    public MirrorsLagQueryHandler(DbType dbType) {
        this.dbType = dbType;
    }

    public String getQueryByDBType(int mirrorsNumber) {
        switch (this.dbType) {
            case DERBY -> {return String.format(SELECT_LAG_FEDERATED_CURSORS_DERBY, mirrorsNumber);}
            case POSTGRESQL -> {return String.format(SELECT_LAG_FEDERATED_CURSORS_POSTGRES, mirrorsNumber);}
            case ORACLE -> {return String.format(SELECT_LAG_FEDERATED_CURSORS_ORACLE, mirrorsNumber);}
            case MARIADB, MYSQL -> {return String.format(SELECT_LAG_FEDERATED_MYSQL_MARIA, mirrorsNumber);}
            case MSSQL -> {return String.format(SELECT_LAG_FEDERATED_CURSORS_MSSQL, mirrorsNumber);}
            default -> {
                log.info("Data base is not supported: {}", dbType);
                throw new IllegalArgumentException();
            }
        }
    }

    public void createMirrorLagFromResultSet(
            ResultSet resultSet, long currentTimeStamp, List<RawMirrorLagInfo> withLagCursors,
            List<RawMirrorLagInfo> noLagCursors) throws SQLException {
        String operatorId = resultSet.getString("operator_id");
        long timeStampForNextEvent = resultSet.getLong("tsnextevent");
        long lagInMS = currentTimeStamp - timeStampForNextEvent;
        log.debug("Returned result from mirror lag: operator id: {}, timeStampForNextEvent: {}, lagInMS: {}",
                operatorId, timeStampForNextEvent, lagInMS);
        // if there is no next event returned value is 0
        if (timeStampForNextEvent == 0 || lagInMS < 0) {
            noLagCursors.add(new RawMirrorLagInfo(operatorId, 0, 0));
        } else {
            withLagCursors.add(
                    new RawMirrorLagInfo(operatorId, lagInMS, timeStampForNextEvent));
        }
    }
}
