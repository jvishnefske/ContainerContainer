/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2023 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.fs.dao;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import lombok.Setter;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable.Columns;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.jfrog.storage.DbType;
import org.jfrog.storage.SqlDaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.sql.Statement.RETURN_GENERATED_KEYS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;
import static org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable.INSERT_QUERY;
import static org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable.SELECT_JOINED_COLUMNS;
import static org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable.TABLE_NAME;
import static org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable.UPDATE_STATUS_WHERE_IN_STATUSES_AND_LAST_MODIFIED_BEFORE_QUERY;
import static org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable.UPDATE_STATUS_WHERE_RUN_ID_IN_QUERY;
import static org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable.UPDATE_STATUS_WHERE_STATUS_QUERY;

/**
 * A data access object for the quartz task history table.
 *
 * @author Mor Merhav
 */
@Repository
public class QuartzTaskHistoryDao extends BaseDao {

    private final SqlDaoHelper<QuartzTaskHistoryRecord> daoHelper;

    @Setter
    private Supplier<Long> timeSupplier = System::currentTimeMillis;

    @Autowired
    public QuartzTaskHistoryDao(ArtifactoryJdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        super(jdbcHelper);
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                TABLE_NAME, QuartzTaskHistoryDao::toRecord,
                ConstantValues.taskHistoryBatchSize.getInt());
    }

    /**
     * Insert a quartz task history record
     *
     * @return the inserted record's generated run ID.
     * @throws SQLException          internal SQL exception
     * @throws IllegalStateException thrown in case there was a failure to retrieve the generated run ID
     */
    public int insert(@Nonnull QuartzTaskHistoryRecord taskHistoryRecord) throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource()
                .getConnection(); PreparedStatement stmt = getGenKeysPreparedStmtByDb(INSERT_QUERY, conn)) {
            stmt.setString(Columns.EXTERNAL_ID.getIndex(), taskHistoryRecord.externalId());
            stmt.setString(Columns.TASK_TYPE.getIndex(), taskHistoryRecord.taskType());
            stmt.setLong(Columns.STARTED_DATE.getIndex(), taskHistoryRecord.startedDate());
            stmt.setLong(Columns.FINISHED_DATE.getIndex(), taskHistoryRecord.finishedDate());
            stmt.setLong(Columns.LAST_MODIFIED_DATE.getIndex(), taskHistoryRecord.lastModifiedDate());
            stmt.setString(Columns.STATUS.getIndex(), taskHistoryRecord.status().toString());
            stmt.setString(Columns.STATUS_MESSAGE.getIndex(), taskHistoryRecord.statusMessage());
            stmt.setString(Columns.TASK_ID.getIndex(), taskHistoryRecord.taskId());
            stmt.setString(Columns.SERVER_ID.getIndex(), taskHistoryRecord.serverId());
            stmt.setInt(Columns.MANUAL_TRIGGER.getIndex(), taskHistoryRecord.manualTrigger() ? 1 : 0);
            stmt.setString(Columns.USERNAME.getIndex(), taskHistoryRecord.username());
            stmt.executeUpdate();
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
                throw new IllegalStateException("Failed to retrieve generated run ID");
            }
        }
    }

    public boolean update(QuartzTaskHistoryRecord taskHistoryRecord) throws SQLException {
        StringBuilder updateSql = new StringBuilder("UPDATE ").append(TABLE_NAME).append(" SET ");
        List<Object> params = new ArrayList<>();
        updateSql.append(Columns.LAST_MODIFIED_DATE.getName()).append(" = ?, ");
        params.add(taskHistoryRecord.lastModifiedDate());

        if (taskHistoryRecord.startedDate() != -1) {
            updateSql.append(Columns.STARTED_DATE.getName()).append(" = ?, ");
            params.add(taskHistoryRecord.startedDate());
        }
        if (taskHistoryRecord.finishedDate() != -1) {
            updateSql.append(Columns.FINISHED_DATE.getName()).append(" = ?, ");
            params.add(taskHistoryRecord.finishedDate());
        }
        if (taskHistoryRecord.status() != null) {
            updateSql.append(Columns.STATUS.getName()).append(" = ?, ");
            params.add(taskHistoryRecord.status().toString());
        }
        if (taskHistoryRecord.statusMessage() != null) {
            updateSql.append(Columns.STATUS_MESSAGE.getName()).append(" = ?");
            params.add(taskHistoryRecord.statusMessage());
        }
        if (updateSql.toString().endsWith(", ")) {
            updateSql.setLength(updateSql.length() - 2); // Remove the trailing comma and space
        }

        updateSql.append(" WHERE ").append(Columns.RUN_ID.getName()).append(" = ?");
        params.add(taskHistoryRecord.runId());
        return daoHelper.executeUpdate(updateSql.toString(), params.toArray()) > 0;
    }

    /**
     * Fetch quartz task history record by its run ID
     *
     * @param runId the runId of the record to return
     * @return the quartz task history record if found by the run ID, null otherwise.
     * @throws SQLException internal SQL exception
     */
    @Nullable
    public QuartzTaskHistoryRecord get(int runId) throws SQLException {
        String selectQuery = new ArtifactoryQueryWriter().select(SELECT_JOINED_COLUMNS).from(TABLE_NAME)
                .where(Columns.RUN_ID.getName() + " = ?").build();
        return daoHelper.findFirstByQuery(selectQuery, runId).orElse(null);
    }

    /**
     * Get all records which were not soft deleted
     *
     * @return all non-soft-deleted records
     * @throws SQLException internal SQL exception
     */
    public Set<QuartzTaskHistoryRecord> getAllNonDeleted() throws SQLException {
        return new HashSet<>(daoHelper.findAllByProperty(Columns.DELETED.getName(), 0));
    }

    /**
     * Update record status by run ID
     *
     * @param runId  the run ID by which to update
     * @param status the status to update to
     * @return true if record was updated, false otherwise.
     * @throws SQLException internal SQL exception
     */
    public boolean updateStatus(int runId, @Nonnull Status status) throws SQLException {
        return daoHelper.updateByProperties(List.of(Columns.RUN_ID.getName()), Stream.of(runId),
                List.of(Columns.STATUS.getName(), Columns.LAST_MODIFIED_DATE.getName()),
                Stream.of(status.toString(), timeSupplier.get())) > 0;
    }

    /**
     * Update record status and message by run ID
     *
     * @param runId         the run ID by which to update
     * @param status        the status to update to
     * @param statusMessage the status message to update to
     * @return true if record was updated, false otherwise.
     * @throws SQLException internal SQL exception
     */
    public boolean updateStatus(int runId, @Nonnull Status status, @Nonnull String statusMessage) throws SQLException {
        return daoHelper.updateByProperties(List.of(Columns.RUN_ID.getName()), Stream.of(runId),
                List.of(Columns.STATUS.getName(), Columns.STATUS_MESSAGE.getName(),
                        Columns.LAST_MODIFIED_DATE.getName()),
                Stream.of(status.toString(), statusMessage, timeSupplier.get())) > 0;
    }

    /**
     * Find a record by external ID in one of the given statuses.
     *
     * @param externalId the record external ID
     * @param statuses   statuses to find the record by.
     * @return the record if found, {@code null} otherwise.
     * @throws SQLException             internal SQL exception
     * @throws IllegalArgumentException if {@param statuses} is empty.
     */
    @Nullable
    public QuartzTaskHistoryRecord findByExternalIdAndStatusIsOneOf(@Nonnull String externalId,
            @Nonnull @NotEmpty Set<Status> statuses) throws SQLException {
        Set<String> statusesStr = convertToStrList(statuses);
        String selectQuery = new ArtifactoryQueryWriter().select(SELECT_JOINED_COLUMNS).from(TABLE_NAME)
                .where(Columns.EXTERNAL_ID.getName() + " = ?" + " AND " + Columns.STATUS.getName() + " IN (#)" +
                        " AND " +
                        Columns.DELETED.getName() + " = 0").build();
        return daoHelper.findFirstByQuery(selectQuery, externalId, statusesStr).orElse(null);
    }

    /**
     * Permanently delete a quartz task history record by its run ID.
     *
     * @param runId the run ID to hard-delete by
     * @return true if a record has been hard-deleted, false otherwise.
     * @throws SQLException internal SQL exception
     */
    public boolean hardDelete(int runId) throws SQLException {
        return daoHelper.deleteByProperty(Columns.RUN_ID.getName(), runId) > 0;
    }

    /**
     * Mark a quartz task history record as deleted by setting the deleted column to {@code true}.
     *
     * @param runId the run ID to soft-delete by.
     * @return true if a record has been soft-deleted, false otherwise.
     * @throws SQLException internal SQL exception
     */
    public boolean softDelete(int runId) throws SQLException {
        return daoHelper.updateByProperties(List.of(Columns.RUN_ID.getName()), Stream.of(runId),
                List.of(Columns.DELETED.getName(), Columns.LAST_MODIFIED_DATE.getName()),
                Stream.of(1, timeSupplier.get())) > 0;
    }

    /**
     * Delete all data
     *
     * @throws SQLException internal SQL exception
     */
    public void deleteAll() throws SQLException {
        daoHelper.deleteAll();
    }

    public int updateToStatusWhereStatus(Status updateTo, Status whereStatus) throws SQLException {
        return daoHelper.executeUpdate(UPDATE_STATUS_WHERE_STATUS_QUERY, updateTo.toString(), whereStatus.toString());
    }

    public int updateToStatusWhereInStatusesAndLastModifiedBefore(Status updateTo, @NotEmpty Set<Status> statuses,
            long lastModifiedBefore) throws SQLException {
        return daoHelper.executeUpdate(UPDATE_STATUS_WHERE_IN_STATUSES_AND_LAST_MODIFIED_BEFORE_QUERY,
                updateTo.toString(), convertToStrList(statuses), lastModifiedBefore);
    }

    public Set<QuartzTaskHistoryRecord> findByStatuses(@NotEmpty Set<Status> statuses) throws SQLException {
        String query = new ArtifactoryQueryWriter()
                        .select(SELECT_JOINED_COLUMNS)
                        .from(TABLE_NAME)
                        .where(Columns.STATUS.getName() + " IN (#)")
                        .build();
        return new HashSet<>(daoHelper.findAllByQuery(query, convertToStrList(statuses)));
    }

    public int updateStatusWhereRunIdIn(Status status, @NotEmpty Set<Integer> runIds) throws SQLException {
        return daoHelper.executeUpdate(UPDATE_STATUS_WHERE_RUN_ID_IN_QUERY, status.toString(), runIds);
    }

    private static PreparedStatement getGenKeysPreparedStmtByDb(String query, Connection conn) throws SQLException {
        if (ArtifactoryHome.get().getDBProperties().getDbType() == DbType.ORACLE) {
            return conn.prepareStatement(query, new String[] {Columns.RUN_ID.getName()});
        }
        return conn.prepareStatement(query, RETURN_GENERATED_KEYS);
    }

    @Nonnull
    private static Set<String> convertToStrList(@Nonnull Set<Status> statuses) {
        return statuses.stream().map(Object::toString).collect(Collectors.toSet());
    }

    private static QuartzTaskHistoryRecord toRecord(ResultSet resultSet) throws SQLException {
        return QuartzTaskHistoryRecord.builder()
                .runId(resultSet.getInt(Columns.RUN_ID.getName()))
                .externalId(resultSet.getString(Columns.EXTERNAL_ID.getName()))
                .taskType(resultSet.getString(Columns.TASK_TYPE.getName()))
                .startedDate(resultSet.getLong(Columns.STARTED_DATE.getName()))
                .finishedDate(resultSet.getLong(Columns.FINISHED_DATE.getName()))
                .lastModifiedDate(resultSet.getLong(Columns.LAST_MODIFIED_DATE.getName()))
                .status(Status.valueOf(resultSet.getString(Columns.STATUS.getName())))
                .statusMessage(resultSet.getString(Columns.STATUS_MESSAGE.getName()))
                .deleted(resultSet.getInt(Columns.DELETED.getName()) != 0)
                .taskId(resultSet.getString(Columns.TASK_ID.getName()))
                .serverId(resultSet.getString(Columns.SERVER_ID.getName()))
                .manualTrigger(resultSet.getInt(Columns.MANUAL_TRIGGER.getName()) != 0)
                .username(resultSet.getString(Columns.USERNAME.getName()))
                .build();
    }
}
