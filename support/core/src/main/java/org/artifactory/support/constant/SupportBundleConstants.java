package org.artifactory.support.constant;

import lombok.experimental.UtilityClass;

/**
 * Constant file which is used for collecting Slow Queries task from database
 *
 * @author vigneshc
 */
@UtilityClass
public class SupportBundleConstants {

    public static final String POSTGRESQL_SLOW_QUERY_STATEMENT =
            " SELECT pid, TO_CHAR(age(clock_timestamp(), query_start), 'HH24:MI:SS') AS age, usename, " +
                    "state, wait_event_type, wait_event, query FROM pg_stat_activity WHERE state != 'idle' AND query " +
                    "NOT LIKE '%pg_stat_activity%' ORDER BY age DESC LIMIT 10";
    public static final String MYSQL_AND_MARIADB_SLOW_QUERY_STATEMENT =
            "SELECT ID,SEC_TO_TIME(TIME) AS TIME,USER,STATE,INFO FROM information_schema.processlist where state != " +
                    "'' and INFO NOT LIKE '%processlist%'  order by time desc LIMIT 10";
    public static final String MSSQL_SLOW_QUERY_STATEMENT =
            "SELECT r.session_id AS pid, FORMAT(DATEADD(ms, DATEDIFF(ms, r.start_time, GETDATE()), '19000101'), " +
                    "'HH:mm:ss') AS age, s.login_name AS usename, r.status AS state, st.text AS query FROM sys" +
                    ".dm_exec_requests r INNER JOIN sys.dm_exec_sessions s ON r.session_id = s.session_id OUTER APPLY" +
                    " sys.dm_exec_sql_text(r.sql_handle) AS st WHERE r.status != 'background' AND st.text IS NOT NULL" +
                    " AND st.text NOT LIKE '%sys.dm_exec_requests%' ORDER BY r.start_time DESC OFFSET 0 ROWS FETCH " +
                    "NEXT 10 ROWS ONLY;";
    public static final String SUPPORTED = "SUPPORTED";
    public static final String UN_SUPPORTED_DERBY = "UN_SUPPORTED_DERBY";
    public static final String UN_SUPPORTED_ORACLE = "UN_SUPPORTED_ORACLE";
    public static final String EMPTY = "EMPTY";
    public static final String EMPTY_RESPONSE_FROM_DATABASE =
            "{\"Response\":\"There are no slow running queries at this moment\"}";
    public static final String UN_SUPPORTED_DERBY_DATABASE_RESPONSE =
            "{\"Response\":\"Derby doesn't contain specific activity tables like other relational database management" +
                    " systems. Hence it is not supported\"}";
    public static final String UN_SUPPORTED_ORACLE_DATABASE_RESPONSE =
            "{\"Response\":\"In Oracle database, Access to Activity tables are restricted only to users with " +
                    "system-level privileges. Hence it is not supported\"}";
}