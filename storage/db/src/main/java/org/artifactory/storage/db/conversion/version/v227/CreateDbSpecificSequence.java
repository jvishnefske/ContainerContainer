package org.artifactory.storage.db.conversion.version.v227;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.util.IdGenerator;
import org.jfrog.converter.AbstractDbConverter;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;

/**
 * A converter with 2 phases in 1 TX:
 * 1. static script that enhanced node_events and node_events_error and creates node_events_tmp
 * 2. dynamic script SQL that creates event_id sequence with dynamic start value
 *
 * @author shaharz
 */

@Slf4j
public class CreateDbSpecificSequence extends AbstractDbConverter {
    private static final String SEQ_NAME = "node_events_event_id_seq";
    private static final String CREATE_SEQ_AUTO_INC_MYSQL =
            "ALTER TABLE node_events MODIFY COLUMN event_id BIGINT AUTO_INCREMENT";
    private static final String CREATE_SEQ_AUTO_INC_INITIAL_VALUE_MYSQL =
            "ALTER TABLE node_events AUTO_INCREMENT=%d";

    private static final String CREATE_SEQ_MSSQL_DERBY = "CREATE SEQUENCE " + SEQ_NAME + " AS BIGINT START WITH %1$s";
    private static final String CREATE_SEQ_PG = "CREATE SEQUENCE " + SEQ_NAME + " START WITH %1$s";

    // In oracle the default precision is number of 28 digits, limiting it to 19.
    private static final String CREATE_SEQ_ORA = "CREATE SEQUENCE " + SEQ_NAME +
            " START WITH %1$s MAXVALUE 9999999999999999999";

    // In MsSql we can't use the sequence explicitly in as Insert Select,
    // hence setting it as default value of event_id column
    private static final String ALTER_NODE_EVENTS_EVENT_ID_DEF_AS_SEQ_MSSQL = "ALTER TABLE node_events ADD " +
            "DEFAULT NEXT VALUE FOR " + SEQ_NAME + " FOR event_id";

    public static final String CURRENT_ID_QUERY =
            "SELECT current_id FROM unique_ids WHERE index_type = '" + IdGenerator.INDEX_TYPE_GENERAL + "'";

    public CreateDbSpecificSequence() {
        super("Create dynamic sequence for node events", executeUnconditional());
    }

    private void createSequence(DbType dbType, long start, Statement statement, Connection conn) throws SQLException {
        List<String> sqls;
        switch (dbType) {
            case DERBY:
                sqls = Collections.singletonList(String.format(CREATE_SEQ_MSSQL_DERBY, start));
                break;
            case MSSQL:
                sqls = Arrays.asList(String.format(CREATE_SEQ_MSSQL_DERBY, start),
                        ALTER_NODE_EVENTS_EVENT_ID_DEF_AS_SEQ_MSSQL);
                break;
            case ORACLE:
                sqls = Collections.singletonList(String.format(CREATE_SEQ_ORA, start));
                break;
            case POSTGRESQL:
                sqls = Collections.singletonList(String.format(CREATE_SEQ_PG, start));
                break;
            case MYSQL:
            case MARIADB:
                sqls = Arrays.asList(
                        CREATE_SEQ_AUTO_INC_MYSQL,
                        String.format(CREATE_SEQ_AUTO_INC_INITIAL_VALUE_MYSQL, start));
                break;
            default:
                throw new UnsupportedOperationException("dbType: " + dbType + " not supported");
        }

        try {
            for (String sql : sqls) {
                statement.executeUpdate(sql);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        }
    }

    @VisibleForTesting
    boolean sequenceExists(Connection connection, DbType dbType) throws SQLException {
        return DbUtils.sequenceExists(connection, dbType, SEQ_NAME);
    }

    private long start(Statement statement) throws SQLException {
        long start = 1;
        try (ResultSet resultSet = statement.executeQuery(CURRENT_ID_QUERY)) {
            if (resultSet.next()) {
                start = resultSet.getLong(1);
            }
        }
        return start + ConstantValues.nodeEventsEventIdMigrationMargin.getLong();
    }

    @Override
    protected void executeConverter(ChangeLogContext context) throws DbConverterExecutionException {
        try {
            Connection conn = context.getConnection();
            DbType dbType = context.getDbType();

            if (sequenceExists(conn, dbType)) {
                log.info("Sequence '{}' already exists - Skipping upgrade.", SEQ_NAME);
                return;
            }

            conn.setAutoCommit(false);
            try {
                log.info("Creating sequence '{}'", SEQ_NAME);
                //Create sequence/auto increment with initial value
                //Important to invoke first since in mysql you can't modify column if it has FK
                try (Statement statement = conn.createStatement()) {
                    createSequence(dbType, start(statement), statement, conn);
                }

                conn.commit();
                log.info("Sequence '{}' created", SEQ_NAME);
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            //conversion error is runtime and logged in the util, no need to catch it here.
            String msg = "Could not convert DB: CREATE SEQUENCE " + SEQ_NAME;
            log.error("{} due to {}", msg, e.getMessage(), e);
            throw new DbConverterExecutionException(msg, e);
        }
    }
}
