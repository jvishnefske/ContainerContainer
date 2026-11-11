package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.model.SearchCriteria;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link org.artifactory.common.ConstantValues#devopsTroubleshootingEnabled} gates
 * producer and read paths in {@link RequestLogServiceImpl}.
 */
public class RequestLogServiceImplFeatureFlagTest
        extends AbstractRequestLogServiceImplFeatureFlagTest<RequestLogServiceImpl, RequestLogRow> {

    private RequestLogDao requestLogDao;

    @Override
    RequestLogServiceImpl createService() {
        requestLogDao = mock(RequestLogDao.class);
        return new RequestLogServiceImpl(requestLogDao, QUEUE_CAPACITY);
    }

    @Override
    RequestLogRow rowWithStatus(int statusCode) {
        return RequestLogRow.builder()
                .id(0L)
                .requestTime(System.currentTimeMillis())
                .traceId("trace-1")
                .username("ff-test-user")
                .httpMethod("GET")
                .requestPath("/api/test")
                .statusCode(statusCode)
                .durationMs(1L)
                .build();
    }

    @Override
    void enqueue(RequestLogRow row) {
        service.enqueueInbound(row);
    }

    @Override
    void doSearch(SearchCriteria criteria) {
        service.searchInbound(criteria);
    }

    @Override
    void stubInsertBatch() throws SQLException {
        when(requestLogDao.insertBatch(any())).thenReturn(1);
    }

    @Override
    void stubSearch() throws SQLException {
        when(requestLogDao.search(any())).thenReturn(Collections.emptyList());
    }

    @Override
    void verifyInsertBatchNever() throws SQLException {
        verify(requestLogDao, never()).insertBatch(any());
    }

    @Override
    int verifyInsertBatchCalledOnce() throws SQLException {
        ArgumentCaptor<List<RequestLogRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(requestLogDao).insertBatch(captor.capture());
        return captor.getValue().size();
    }

    @Override
    void verifySearchNever() throws SQLException {
        verify(requestLogDao, never()).search(any());
    }

    @Override
    void verifySearchCalled() throws SQLException {
        verify(requestLogDao).search(any());
    }
}
