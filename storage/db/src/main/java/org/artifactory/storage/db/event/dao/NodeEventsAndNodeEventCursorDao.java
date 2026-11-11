/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
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

package org.artifactory.storage.db.event.dao;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.event.dao.query.MirrorsLagQueryHandler;
import org.artifactory.storage.db.event.dao.query.QueueEventsTypesQueryHandler;
import org.artifactory.storage.db.event.dao.query.RepoStatusQueryHandler;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.event.EventsTypesStatusInfo;
import org.artifactory.storage.event.cursor.RawMirrorLagInfo;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Repository
public class NodeEventsAndNodeEventCursorDao extends BaseDao {
    static final String STATUS_COL = "status";
    private int LAG_LIMIT = ConstantValues.federatedStatusMirrorsLagLimit.getInt();
    private DbProperties dbProperties;
    private DbType dbType;

    private MirrorsLagQueryHandler mirrorsLagQueryHandler;
    private QueueEventsTypesQueryHandler queueEventsTypesQueryHandler;
    private RepoStatusQueryHandler repoStatusQueryHandler;

    @Autowired
    public NodeEventsAndNodeEventCursorDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    public DbType getDbType() {
        return dbType;
    }

    @Autowired
    public void setDbProperties(DbProperties dbProperties) {
        this.dbProperties = dbProperties;
    }

    @PostConstruct
    public void init() {
        dbType = dbProperties.getDbType();
        this.mirrorsLagQueryHandler = new MirrorsLagQueryHandler(dbType);
        this.queueEventsTypesQueryHandler = new QueueEventsTypesQueryHandler(dbType);
        this.repoStatusQueryHandler = new RepoStatusQueryHandler();
    }

    public Map<String, Integer> getFederatedRepoStatuses() throws SQLException {
        Map<String, Integer> federatedRepoStatuses = new HashMap<>();
        for (NodeEventCursorStatus status : NodeEventCursorStatus.values()) {
            federatedRepoStatuses.put(status.name(), 0);
        }

        String sqlQuery = this.repoStatusQueryHandler.getQueryByDBType();
        log.debug("Executing the federated repo statuses query: {}", sqlQuery);
        try (ResultSet resultSet = jdbcHelper.executeSelect(sqlQuery)) {
            log.debug("Finished executing the repo statuses query");
            while (resultSet.next()) {
                String currentStatus = resultSet.getString(STATUS_COL);
                int currentCount = resultSet.getInt("count");
                try {
                    log.debug("Validating validity of repo status: " + currentStatus);
                    NodeEventCursorStatus.valueOf(currentStatus);
                    federatedRepoStatuses.put(currentStatus, currentCount);
                } catch (IllegalArgumentException e) {
                    log.error("Invalid status returned for repo statuses query, status: " + currentStatus);
                }
            }
        }
        return federatedRepoStatuses;
    }

    public List<RawMirrorLagInfo> getMirrorsLag() throws SQLException {
        List<RawMirrorLagInfo> mirrorsWithLag = new ArrayList<>();
        List<RawMirrorLagInfo> mirrorsWithoutLag = new ArrayList<>();
        String sqlQuery =
                this.mirrorsLagQueryHandler.getQueryByDBType(LAG_LIMIT);

        log.debug("The mirror lag query defined like this: {}", sqlQuery);
        try (ResultSet resultSet = jdbcHelper.executeSelect(sqlQuery)) {
            long currentTimeStamp = System.currentTimeMillis();
            log.debug("Finished executing the mirror lag query");
            while (resultSet.next()) {
                mirrorsLagQueryHandler.createMirrorLagFromResultSet(resultSet, currentTimeStamp,
                        mirrorsWithLag, mirrorsWithoutLag);
            }
        }
        log.debug("mirrors without lag: {}, with lag before adding: {}",
                mirrorsWithoutLag.size(), mirrorsWithLag.size());
        mirrorsWithLag.addAll(mirrorsWithoutLag);
        log.debug("mirrors with lag after adding: {}", mirrorsWithLag.size());
        return mirrorsWithLag;
    }

    private void EventTypesInfoFromResultSet(ResultSet rs, long currentTimeStamp,
            List<EventsTypesStatusInfo> withLagCursors,
            List<EventsTypesStatusInfo> noLagCursors) throws SQLException {
        long timeStampForNextEvent = rs.getLong("tsnextevent");
        long lagInMS = currentTimeStamp - timeStampForNextEvent;
        // if there is no next event, the returned value of tsNextEvent will be 0, in order to fill thuse tsNextEvent
        // at the end of the list we split them and then join them at the end of the list.
        // also to ignore events that after current timestamp
        if (timeStampForNextEvent == 0 || lagInMS < 0) {
            noLagCursors.add(
                    new EventsTypesStatusInfo(rs.getString("operator_id"), rs.getString(STATUS_COL),
                            rs.getLong("type_1"),
                            rs.getLong("type_2"), rs.getLong("type_3"),
                            rs.getLong("type_4"), 0, 0));
        } else {
            withLagCursors.add(
                    new EventsTypesStatusInfo(rs.getString("operator_id"), rs.getString(STATUS_COL),
                            rs.getLong("type_1"),
                            rs.getLong("type_2"), rs.getLong("type_3"),
                            rs.getLong("type_4"), lagInMS,
                            timeStampForNextEvent));
        }
    }

    public List<EventsTypesStatusInfo> getEventsTypesStatus(String repoKey, long currentTime) throws SQLException {
        String sqlQuery;
        // if there is no next event, the returned value of tsNextEvent will be 0, in order to fill thuse tsNextEvent
        // at the end of the list we split them and then join them at the end of the list.
        List<EventsTypesStatusInfo> mirrorsWithLag = new ArrayList<>();
        List<EventsTypesStatusInfo> mirrorsWithoutLag = new ArrayList<>();
        sqlQuery = this.queueEventsTypesQueryHandler.getQueryByDBType();
        try (ResultSet rs = jdbcHelper.executeSelect(sqlQuery,
                this.queueEventsTypesQueryHandler.fillStatementParams(repoKey, currentTime))) {
            while (rs.next()) {
                EventTypesInfoFromResultSet(rs, currentTime, mirrorsWithLag, mirrorsWithoutLag);
            }
        }
        mirrorsWithLag.addAll(mirrorsWithoutLag);
        return mirrorsWithLag;
    }
}
