package org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query.AddColumnHandler;
import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query.UpdateRepoColumnHandler;
import org.jfrog.storage.DbProperties;
import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.query.AddIndexOnRepoAndEventHandler;
import org.artifactory.storage.db.event.service.EventsServiceImpl;
import org.artifactory.storage.db.init.migration.MarkerMigrationInfoBlob;
import org.artifactory.storage.db.init.migration.model.MigrationStatus;
import org.artifactory.storage.db.init.migration.service.MigrationStatusStorageService;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.common.ClockUtils;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.jfrog.converter.util.PreDefinedConditions.indexExists;

@Repository
public class EventTableRepoKeyMigrationDao extends BaseDao {

    private final MigrationStatusStorageService migrationStatusStorageService;
    private EventsServiceImpl eventService;
    private AddColumnHandler addColumnHandler;
    private AddIndexOnRepoAndEventHandler addIndexOnRepoAndEventHandler;
    private UpdateRepoColumnHandler updateRepoColumnHandler;
    private DbProperties dbProperties;
    private DbType dbType;
    private static final Logger log = LoggerFactory.getLogger(EventTableRepoKeyMigrationDao.class);
    private volatile boolean isEventTableRepoKeyMigrationFinished = false;
    public static final String REPO_COLUMN_NAME = "repo";
    public static final String EVENT_ID_COLUMN_NAME = "event_id";
    public static final String EVENTS_TABLE = "node_events";
    public static final String EVENTS_INDEX_NAME = "node_events_repo_event_id";

    public EventTableRepoKeyMigrationDao(MigrationStatusStorageService migrationStatusStorageService,
            ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
        this.migrationStatusStorageService = migrationStatusStorageService;
    }

    @Autowired
    public void setEventServiceDao(EventsServiceImpl eventsService) {
        this.eventService = eventsService;
    }

    @Autowired
    public void setDbProperties(DbProperties dbProperties) {
        this.dbProperties = dbProperties;
    }

    @PostConstruct
    public void init() {
        dbType = dbProperties.getDbType();
        this.addColumnHandler = new AddColumnHandler(dbType);
        this.addIndexOnRepoAndEventHandler = new AddIndexOnRepoAndEventHandler(dbType);
        this.updateRepoColumnHandler = new UpdateRepoColumnHandler(dbType);
    }

    public boolean checkIfMigrationFinished(String serverID) {
        // check in memory if migration already finished
        if (isEventTableRepoKeyMigrationFinished) {
            return true;
        }
        // check migration job exist in the DB
        MigrationStatus eventTableRepoKeyMigration =
                this.migrationStatusStorageService.findMigrationById(eventService.getMonitorMigrationName());
        if (eventTableRepoKeyMigration == null) {
            // not exist need to create
            MigrationStatus migrationStatus =
                    new MigrationStatus(eventService.getMonitorMigrationName(), ClockUtils.epochMillis(), 0,
                            new MarkerMigrationInfoBlob(serverID));
            this.migrationStatusStorageService.insertMigrationWithInfoBlob(migrationStatus);
            log.info("start execute events table repo key migration in the first time");
            return false;
        }
        // if exist check if migration finished
        if (!eventTableRepoKeyMigration.isFinished()) {
            log.info("start execute events table repo key again because its finished unsuccessfully");
            return false;
        }
        this.isEventTableRepoKeyMigrationFinished = true;
        return true;
    }


    public void handleAddRepoColumn() throws SQLException {
        log.debug("start to add column" + REPO_COLUMN_NAME + " to " + EVENTS_TABLE);
        addRepoColumn();
        log.debug("finish to add column" + REPO_COLUMN_NAME + " to " + EVENTS_TABLE);
        log.debug("update event dao that the" + REPO_COLUMN_NAME + " has been added to " + EVENTS_TABLE);
        updateDaoRepoColumnAddedToEventTable();
    }

    private void addRepoColumn() throws SQLException {
        if (!checkIfColumnExist()) {
            String sqlQuery;
            sqlQuery = this.addColumnHandler.getQueryByDBType();
            sqlQuery = String.format(sqlQuery);
            jdbcHelper.executeUpdate(sqlQuery);
        }
    }

    public Boolean checkIfColumnExist() throws SQLException {
        return DbUtils.columnExists(jdbcHelper, dbType, EVENTS_TABLE, REPO_COLUMN_NAME);
    }

    public void updateDaoRepoColumnAddedToEventTable() {
        eventService.updateMonitorMigrationFinished();
    }

    public void addIndexOnRepoAndEventId() throws SQLException {
        log.debug("start to add index on " + EVENTS_TABLE);
        List<String> columnNames = List.of(REPO_COLUMN_NAME, EVENT_ID_COLUMN_NAME);
        String sqlQueryOnlineSuffix = "";
        if (!checkIfIndexExist(columnNames)) {
            sqlQueryOnlineSuffix = getSqlQueryOnlineSuffix();

            String sqlQuery = this.addIndexOnRepoAndEventHandler.getQueryAddIndexByDBType();
            jdbcHelper.executeUpdate(sqlQuery + sqlQueryOnlineSuffix);
        }
        log.info("finish to add {} index on {}", sqlQueryOnlineSuffix, EVENTS_TABLE);
    }

    public String getSqlQueryOnlineSuffix() {
        String sqlQueryOnlineSuffix = "";
        String checkOnlineQuery = this.addIndexOnRepoAndEventHandler.getQueryCheckOnlineIndexSupportedByDBType();
        if (StringUtils.isNotEmpty(checkOnlineQuery)) {
            try (ResultSet resultSet = jdbcHelper.executeSelect(checkOnlineQuery)) {
                while (resultSet.next()) {
                    sqlQueryOnlineSuffix = resultSet.getLong("cnt") > 0 ?
                            " " + this.addIndexOnRepoAndEventHandler.getQueryOnlineIndexCmdByDBType() : "";
                }
            } catch (Exception e) {
                log.warn("failed to read indication whether online index is supported");
            }
        }
        return sqlQueryOnlineSuffix;
    }

    private Boolean checkIfIndexExist(List<String> columnNames) throws SQLException {
        Connection connection = jdbcHelper.getDataSource().getConnection();
        boolean resultIndexExist = indexExists(EVENTS_TABLE, EVENTS_INDEX_NAME, columnNames).test(connection, dbType);
        DbUtils.close(connection);
        return resultIndexExist;
    }

    public void updateRepoInNodeEvent() {
        try {
            // we want to make sure that we stop shifting without repo key
            eventService.waitForShiftToEnd();
            log.debug("Local error-threshold exclusive lock acquired");
            log.debug("update event dao that the" + REPO_COLUMN_NAME + " has been added to " + EVENTS_TABLE);
            String sqlQuery;
            sqlQuery = this.updateRepoColumnHandler.getQueryByDBType();
            sqlQuery = String.format(sqlQuery, ConstantValues.eventTableRepoKeyMigrationUpdateBatches.getInt());
            log.debug(sqlQuery);
            int countUpdatedRows = jdbcHelper.executeUpdate(sqlQuery);
            while (countUpdatedRows != 0) {
                Thread.sleep(ConstantValues.eventTableRepoKeyMigrationUpdateSleepIntervalMillis.getLong());
                countUpdatedRows = jdbcHelper.executeUpdate(sqlQuery);
                log.debug("number of rows that was updated:" + countUpdatedRows);
            }
            log.debug("finished update the all nodes about the new column " + REPO_COLUMN_NAME + " to " + EVENTS_TABLE);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void finishedMigration() {
        this.migrationStatusStorageService.migrationDone(eventService.getMonitorMigrationName());
        isEventTableRepoKeyMigrationFinished = true;
        log.info("successfully finished execute events table repo key migration");
    }

    public boolean getIsEventTableRepoKeyMigrationFinished() {
        if (this.isEventTableRepoKeyMigrationFinished) {
            return true;
        }
        // check migration job exist in the DB
        MigrationStatus eventTableRepoKeyMigration =
                this.migrationStatusStorageService.findMigrationById(eventService.getMonitorMigrationName());
        if (eventTableRepoKeyMigration == null) {
            log.debug("Monitoring is unavailable - event repo migration not found in the DB");
            return false;
        }
        boolean migrationFinished = eventTableRepoKeyMigration.isFinished();
        if (!migrationFinished) {
            log.debug("Monitoring is unavailable, migration in progress");
            return false;
        }
        this.isEventTableRepoKeyMigrationFinished = true;
        return true;

    }
}
