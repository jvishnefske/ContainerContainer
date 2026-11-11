package org.artifactory.storage.db.event.error.itest.dao;

import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.artifactory.storage.db.event.service.InternalEventsService;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.artifactory.storage.db.itest.DbTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

import javax.sql.DataSource;

import static org.artifactory.event.EventType.create;

/**
 * @author avishayh
 */
public abstract class ErrorsDaoBaseDbTest extends DbBaseTest {

    protected static final String TABLE_NODE_EVENTS_ERRORS = "node_events_errors";

    @Autowired
    DataSource dataSource;
    @Autowired
    InternalEventsService internalEventsService;


    JdbcTemplate jdbcTemplate;
    SimpleJdbcInsert simpleJdbcInsert;


    @BeforeClass
    public void setup() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        simpleJdbcInsert = DbTestUtils.createSimpleJdbcInsert(dataSource).withTableName(TABLE_NODE_EVENTS_ERRORS);
    }

    @AfterMethod
    public void cleanUpErrors() {
        internalEventsService.deleteAll();
    }

    protected NodeEventsErrorsDBO create(int errorId, String operationKey, String eventCategory) {
        return create(errorId, operationKey, create, eventCategory);
    }

    protected NodeEventsErrorsDBO create(int errorId, String operationKey, EventType eventType, String eventCategory) {
        return create(errorId, operationKey, eventType, eventCategory, "defaultPath");
    }

    protected NodeEventsErrorsDBO create(int errorId, String operationKey, EventType eventType, String eventCategory,
            String path) {
        return create(errorId, operationKey, eventType, eventCategory, path, 0, 2, "defaultErrMsg");
    }

    protected NodeEventsErrorsDBO create(int errorId, String operationKey, EventType eventType, String eventCategory,
            String path, int errorCount, int lastErrorTime, String errMsg) {
        return create(errorId, operationKey, eventType, eventCategory, path, errorCount, lastErrorTime, errMsg,
                0, null);
    }

    protected NodeEventsErrorsDBO create(long errorId, String operationKey, EventType eventType, String eventCategory,
            String path, int errorCount, int lastErrorTime, String errMsg, long eventTime, Long eventId) {
        return NodeEventsErrorsDBO.builder()
                .errorId(errorId)
                .errorCount(errorCount)
                .errorMessage(errMsg)
                .firstErrorTime(1)
                .lastErrorTime(lastErrorTime)
                .operationKey(operationKey)
                .eventCategory(eventCategory)
                .eventTime(eventTime)
                .eventId(eventId)
                .path(path)
                .eventType(eventType.code())
                .build();
    }

}
