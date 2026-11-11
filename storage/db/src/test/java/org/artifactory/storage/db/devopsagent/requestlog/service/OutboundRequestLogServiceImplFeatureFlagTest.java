package org.artifactory.storage.db.devopsagent.requestlog.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.devopsagent.requestlog.dao.OutboundRequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
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
 * Verifies the feature-flag gate in {@link OutboundRequestLogServiceImpl}.
 */
public class OutboundRequestLogServiceImplFeatureFlagTest
        extends AbstractRequestLogServiceImplFeatureFlagTest<OutboundRequestLogServiceImpl, OutboundRequestLogRow> {

    private OutboundRequestLogDao dao;

    @Override
    OutboundRequestLogServiceImpl createService() {
        dao = mock(OutboundRequestLogDao.class);
        return new OutboundRequestLogServiceImpl(dao, QUEUE_CAPACITY,
                ConstantValues.devopsTroubleshootingEnabled::getBoolean);
    }

    @Override
    OutboundRequestLogRow rowWithStatus(int statusCode) {
        return OutboundRequestLogRow.builder()
                .id(0L)
                .requestTime(System.currentTimeMillis())
                .traceId("trace-out-1")
                .repoKey("npm-remote")
                .httpMethod("GET")
                .requestUrl("https://registry.npmjs.org/axios")
                .statusCode(statusCode)
                .durationMs(50L)
                .build();
    }

    @Override
    void enqueue(OutboundRequestLogRow row) {
        service.enqueueOutbound(row);
    }

    @Override
    void doSearch(SearchCriteria criteria) {
        service.searchOutbound(criteria);
    }

    @Override
    void stubInsertBatch() throws SQLException {
        when(dao.insertBatch(any())).thenReturn(1);
    }

    @Override
    void stubSearch() throws SQLException {
        when(dao.search(any())).thenReturn(Collections.emptyList());
    }

    @Override
    void verifyInsertBatchNever() throws SQLException {
        verify(dao, never()).insertBatch(any());
    }

    @Override
    int verifyInsertBatchCalledOnce() throws SQLException {
        ArgumentCaptor<List<OutboundRequestLogRow>> captor = ArgumentCaptor.forClass(List.class);
        verify(dao).insertBatch(captor.capture());
        return captor.getValue().size();
    }

    @Override
    void verifySearchNever() throws SQLException {
        verify(dao, never()).search(any());
    }

    @Override
    void verifySearchCalled() throws SQLException {
        verify(dao).search(any());
    }
}
