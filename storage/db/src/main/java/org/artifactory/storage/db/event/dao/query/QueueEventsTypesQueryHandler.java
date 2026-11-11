package org.artifactory.storage.db.event.dao.query;

import org.artifactory.mirror.DeleteEventHintType;
import org.jfrog.storage.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QueueEventsSummaryQueryHandler provides a suitable query for each db in order to get the queue event summary
 * as part of federated monitoring.
 * 1. we collect all federated cursors with status SYNC
 * 2.second we find for each such cursor all events that are greater than the event marker of the cursor that
 * their hint is null (in case of 'delete' event also ["file:true"] or ["file:false"])
 * and that the timestamp is after the time the query was activated
 * we count all create, update, delete, props events
 */
public class QueueEventsTypesQueryHandler {
    private static final Logger log = LoggerFactory.getLogger(QueueEventsTypesQueryHandler.class);

    private final DbType dbType;

    private static final String DELETE_EVENT_TYPE_HINT_CRITERIA =
            "(ne.hints is null " +
                    "or ne.hints LIKE '%" + DeleteEventHintType.File.getDescription() + "%' " +
                    "or ne.hints LIKE '%" + DeleteEventHintType.Folder.getDescription() + "%') ";
    private static final String SELECT_QUEUE_EVENT_SUMMARY_DERBY =
            "select * from (select nec.*, (select timestamp from node_events ne where repo = nec.local_repo " +
                    "and nec.event_marker < ne.event_id and ne.hints is null fetch first 1 rows only) tsNextEvent, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and ne.event_type = 1 " +
                    "and ne.hints is null and ne.timestamp < ?) type_1, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 2 and ne.hints is null and ne.timestamp < ?) type_2, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and " +
                    "ne.event_type = 3 and " + DELETE_EVENT_TYPE_HINT_CRITERIA +
                    "and ne.timestamp < ?) type_3, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 4 and ne.hints is null and ne.timestamp < ?) type_4 " +
                    "from (select operator_id, status, event_marker, " +
                    "SUBSTR(operator_id, 1, LOCATE(?, operator_id)-1)  " +
                    "local_repo from node_event_cursor where type = ? and " +
                    "SUBSTR(operator_id, 1, LOCATE(?, operator_id) - 1) = ?) nec) " +
                    "mirrors_status_before_order_by order by tsNextEvent";
    private static final String SELECT_QUEUE_EVENT_SUMMARY_POSTGRES =
            "select * from (select nec.*, (select timestamp  from node_events ne where repo = nec.local_repo " +
                    "and nec.event_marker < ne.event_id and ne.hints is null LIMIT 1) as tsNextEvent, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 1 and ne.hints is null and ne.timestamp < ?) type_1, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 2 and ne.hints is null and ne.timestamp < ?) type_2, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and " +
                    "ne.event_type = 3 and " + DELETE_EVENT_TYPE_HINT_CRITERIA +
                    "and ne.timestamp < ?) type_3, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and ne.event_type = 4 " +
                    "and ne.hints is null and ne.timestamp < ?) type_4 " +
                    "from (select operator_id, status, event_marker, " +
                    "substr(operator_id, 0, POSITION(? IN operator_id)) local_repo " +
                    "from node_event_cursor where type = ? " +
                    "and substr(operator_id, 0, POSITION(? IN operator_id)) = ?) nec) " +
                    "mirrors_status_before_order_by order by tsNextEvent";
    private static final String SELECT_QUEUE_EVENT_SUMMARY_MARIA =
            "select * from (select nec.*, (select timestamp from node_events ne where repo = nec.local_repo " +
                    "and nec.event_marker < ne.event_id and ne.hints is null limit 1 ) tsNextEvent, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 1 and ne.hints is null and ne.timestamp < ?) type_1, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 2 and ne.hints is null and ne.timestamp < ?) type_2, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and " +
                    "ne.event_type = 3 and " + DELETE_EVENT_TYPE_HINT_CRITERIA +
                    "and ne.timestamp < ?) type_3, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and ne.event_type = 4 " +
                    "and ne.hints is null and ne.timestamp < ?) type_4 " +
                    "from (select operator_id, status, event_marker, " +
                    "SUBSTRING(operator_id, 1, INSTR(operator_id,?)-1) local_repo " +
                    "from node_event_cursor where type = ? " +
                    "and SUBSTRING(operator_id, 1, INSTR(operator_id,?)-1) = ?) nec) " +
                    "mirrors_status_before_order_by order by tsNextEvent";
    private static final String SELECT_QUEUE_EVENT_SUMMARY_MSSQL =
            "select * from (select nec.*, (Select top 1 * From (select timestamp from node_events ne where repo = " +
                    "nec.local_repo and nec.event_marker < ne.event_id and ne.hints is null) aa) tsNextEvent, " +
                    "(select count(*) from node_events ne where ne.repo = nec.local_repo and " +
                    "nec.event_marker < ne.event_id " +
                    "and ne.event_type = 1 and ne.hints is null and ne.timestamp < ?) type_1, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 2 and ne.hints is null and ne.timestamp < ?) type_2, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and " +
                    "ne.event_type = 3 and " + DELETE_EVENT_TYPE_HINT_CRITERIA +
                    "and ne.timestamp < ?) type_3, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and " +
                    "ne.event_type = 4 and ne.hints is null and ne.timestamp < ?) type_4 " +
                    "from (select operator_id, status, event_marker, " +
                    "SUBSTRING(operator_id, 1, CHARINDEX(?, operator_id) - 1)  local_repo " +
                    "from node_event_cursor where type = ? " +
                    "and SUBSTRING(operator_id, 1, CHARINDEX(?, operator_id) - 1) = ?) nec) " +
                    "mirrors_status_before_order_by order by tsNextEvent";
    private static final String SELECT_QUEUE_EVENT_SUMMARY_ORACLE =
            "select * from (select nec.*, (select timestamp from node_events ne where repo = nec.local_repo " +
                    "and nec.event_marker < ne.event_id and ne.hints is null and ROWNUM <=1) " +
                    "tsNextEvent, (select count(*) from node_events ne where ne.repo = nec.local_repo and " +
                    "nec.event_marker < ne.event_id and ne.event_type = 1 " +
                    "and ne.hints is null and ne.timestamp < ?) type_1, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id " +
                    "and ne.event_type = 2 and ne.hints is null and ne.timestamp < ?) type_2, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and " +
                    "ne.event_type = 3 and " + DELETE_EVENT_TYPE_HINT_CRITERIA +
                    "and ne.timestamp < ?) type_3, " +
                    "(select count(*) from node_events ne " +
                    "where ne.repo = nec.local_repo and nec.event_marker < ne.event_id and " +
                    "ne.event_type = 4 and ne.hints is null and ne.timestamp < ?) type_4 " +
                    "from (select operator_id, status, event_marker, SUBSTR(operator_id, 1, INSTR(operator_id, ?)-1) " +
                    "local_repo from node_event_cursor where type = ? " +
                    "and SUBSTR(operator_id, 1, INSTR(operator_id, ?)-1) = ?) nec) " +
                    "mirrors_status_before_order_by order by tsNextEvent";

    public QueueEventsTypesQueryHandler(DbType dbType) {
        this.dbType = dbType;
    }

    public String getQueryByDBType() {
        String sqlQuery;
        switch (this.dbType) {
            case DERBY -> sqlQuery = SELECT_QUEUE_EVENT_SUMMARY_DERBY;
            case POSTGRESQL -> sqlQuery = SELECT_QUEUE_EVENT_SUMMARY_POSTGRES;
            case ORACLE -> sqlQuery = SELECT_QUEUE_EVENT_SUMMARY_ORACLE;
            case MARIADB, MYSQL -> sqlQuery = SELECT_QUEUE_EVENT_SUMMARY_MARIA;
            case MSSQL -> sqlQuery = SELECT_QUEUE_EVENT_SUMMARY_MSSQL;
            default -> {
                log.info("Database is not supported: {}, {}", this, dbType);
                throw new IllegalArgumentException();
            }
        }
        return sqlQuery;
    }

    public Object[] fillStatementParams(String repoKey, long currentTime) {
        return new Object[] {
                currentTime, currentTime, currentTime, currentTime,
                "-federated_replication", "FEDERATED_REPLICATION", "-federated_replication", repoKey
        };
    }
}
