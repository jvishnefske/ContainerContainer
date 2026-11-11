package org.artifactory.storage.db.devopsagent.requestlog.service;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchResult;
import org.artifactory.storage.db.devopsagent.requestlog.model.TraceResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Inbound-direction implementation of {@link RequestLogService}.
 * Queue management, flush lifecycle, validation, and cursor logic are inherited from
 * {@link AbstractRequestLogServiceImpl}.
 */
@Service
@Slf4j
public class RequestLogServiceImpl extends AbstractRequestLogServiceImpl<RequestLogRow>
        implements RequestLogService {

    static final long DEFAULT_TRACE_WINDOW_MS = 24L * 60L * 60L * 1000L;
    static final String TRACE_ID_REQUIRED_MESSAGE = "'traceId' is required";

    private final RequestLogDao requestLogDao;

    @Autowired
    private OutboundRequestLogService outboundRequestLogService;

    /** Wall-clock supplier — overridable for unit tests. */
    @Setter
    private LongSupplier clock = System::currentTimeMillis;

    @Autowired
    public RequestLogServiceImpl(RequestLogDao requestLogDao) {
        this(requestLogDao, resolveQueueCapacity());
    }

    RequestLogServiceImpl(RequestLogDao requestLogDao, int queueCapacity) {
        super(queueCapacity, ConstantValues.devopsTroubleshootingEnabled::getBoolean);
        this.requestLogDao = requestLogDao;
    }

    @Override
    protected void insertBatch(List<RequestLogRow> rows) throws SQLException {
        requestLogDao.insertBatch(rows);
    }

    @Override
    protected String direction() {
        return "inbound";
    }

    @Override
    public void enqueueInbound(RequestLogRow row) {
        enqueue(row);
    }

    @Override
    public SearchResult<RequestLogRow> searchInbound(SearchCriteria criteria) {
        return doSearch(criteria, () -> requestLogDao.search(criteria), "search_inbound");
    }

    @Override
    public TraceResult findTrace(String traceId, Long timeFrom, Long timeTo) {
        assertFeatureEnabled();
        if (traceId == null || traceId.isBlank()) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, TRACE_ID_REQUIRED_MESSAGE);
        }
        long now = clock.getAsLong();
        long from = timeFrom != null ? timeFrom : (now - DEFAULT_TRACE_WINDOW_MS);
        long to = timeTo != null ? timeTo : now;
        if (from > to) {
            throw new RequestLogException(HttpStatus.SC_BAD_REQUEST, TIME_FROM_AFTER_TIME_TO_MESSAGE);
        }
        try {
            List<RequestLogRow> inbound = requestLogDao.findByTraceId(
                    traceId, from, to, TRACE_PER_DIRECTION_CAP + 1);
            boolean truncatedInbound = inbound.size() > TRACE_PER_DIRECTION_CAP;
            if (truncatedInbound) {
                inbound = inbound.subList(0, TRACE_PER_DIRECTION_CAP);
            }
            List<OutboundRequestLogRow> outbound =
                    outboundRequestLogService.findOutboundByTrace(traceId, from, to);
            boolean truncatedOutbound = outbound.size() > TRACE_PER_DIRECTION_CAP;
            if (truncatedOutbound) {
                outbound = outbound.subList(0, TRACE_PER_DIRECTION_CAP);
            }
            return new TraceResult(inbound, truncatedInbound, outbound, truncatedOutbound);
        } catch (SQLException e) {
            log.error("agentic_api.request_logs.internal_error endpoint=trace", e);
            throw new RequestLogException(HttpStatus.SC_INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, e);
        }
    }
}
