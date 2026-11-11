package org.artifactory.storage.db.devopsagent.requestlog.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.devopsagent.requestlog.dao.OutboundRequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Outbound-direction implementation of {@link OutboundRequestLogService}.
 * Queue management, flush lifecycle, validation, and cursor logic are inherited from
 * {@link AbstractRequestLogServiceImpl}.
 */
@Service
@Slf4j
public class OutboundRequestLogServiceImpl extends AbstractRequestLogServiceImpl<OutboundRequestLogRow>
        implements OutboundRequestLogService {

    private final OutboundRequestLogDao dao;

    @Autowired
    public OutboundRequestLogServiceImpl(OutboundRequestLogDao dao) {
        this(dao, resolveQueueCapacity(), ConstantValues.devopsTroubleshootingEnabled::getBoolean);
    }

    OutboundRequestLogServiceImpl(OutboundRequestLogDao dao, int queueCapacity,
            BooleanSupplier featureFlag) {
        super(queueCapacity, featureFlag);
        this.dao = dao;
    }

    @Override
    protected void insertBatch(List<OutboundRequestLogRow> rows) throws SQLException {
        dao.insertBatch(rows);
    }

    @Override
    protected String direction() {
        return "outbound";
    }

    @Override
    public void enqueueOutbound(OutboundRequestLogRow row) {
        enqueue(row);
    }

    @Override
    public SearchResult<OutboundRequestLogRow> searchOutbound(SearchCriteria criteria) {
        return doSearch(criteria, () -> dao.search(criteria), "search_outbound");
    }

    @Override
    public List<OutboundRequestLogRow> findOutboundByTrace(String traceId, long from, long to) {
        try {
            return dao.findByTraceId(traceId, from, to, TRACE_PER_DIRECTION_CAP + 1);
        } catch (SQLException e) {
            log.error("agentic_api.request_logs.internal_error endpoint=trace_outbound", e);
            throw new RequestLogException(HttpStatus.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, e);
        }
    }
}
