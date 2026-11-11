package org.artifactory.storage.db.event.error.dao;

import com.google.common.collect.Lists;
import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.entity.EventRecordHints;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.artifactory.repo.event.EventInfo;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nonnull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author avishayh
 */
@Repository
public class EventAndErrorInfoDao extends BaseDao {

    public static final String FIELDS =
            "ne.event_id, ne.timestamp, ne.event_type, ne.path, ne.hints, " +
                    "nee.error_id, nee.first_error_time, nee.error_count";

    @Autowired
    public EventAndErrorInfoDao(ArtifactoryJdbcHelper jdbcHelper) {
        super(jdbcHelper);
    }

    public List<EventAndErrorInfo> loadErrorEvents(@Nonnull String category, @Nonnull String operationKey, int limit)
            throws SQLException {
        String sql = new ArtifactoryQueryWriter()
                .select(FIELDS)
                .from("node_events ne ")
                .innerJoin("node_events_errors nee", "ne.event_id = nee.event_id")
                .where("nee.event_category = ? AND nee.operation_key = ?")
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .orderBy("event_id ASC").build();

        ResultSet rs = null;
        try {
            rs = jdbcHelper.executeSelect(sql, category, operationKey);
            List<EventAndErrorInfo> entries = Lists.newArrayList();
            while (rs.next()) {
                EventInfo eventInfo = eventFromResultSet(rs);
                EventAndErrorInfo errorInfoDBO = EventAndErrorInfo.builder()
                        .eventInfo(eventInfo)
                        .errorId(rs.getLong("error_id"))
                        .errorCount(rs.getInt("error_count"))
                        .firstErrorTime(rs.getLong("first_error_time"))
                        .build();
                entries.add(errorInfoDBO);
            }
            return entries;
        } finally {
            DbUtils.close(rs);
        }
    }

    public int getErrorCount(@Nonnull String category, @Nonnull String operationKey)
            throws SQLException {
        String sql = new ArtifactoryQueryWriter()
                .select("COUNT(*)")
                .from("node_events ne ")
                .innerJoin("node_events_errors nee", "ne.event_id = nee.event_id")
                .where("nee.event_category = ? AND nee.operation_key = ?")
                .build();
        return jdbcHelper.executeSelectCount(sql,category, operationKey);
    }

    private EventInfo eventFromResultSet(ResultSet rs) throws SQLException {
        return new EventInfo(rs.getLong("event_id"), rs.getLong("timestamp"),
                EventType.fromCode(rs.getShort("event_type")), rs.getString("path"),
                EventRecordHints.fromJson(rs.getString("hints")));
    }
}
