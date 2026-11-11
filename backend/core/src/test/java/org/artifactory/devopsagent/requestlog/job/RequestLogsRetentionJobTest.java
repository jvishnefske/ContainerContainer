package org.artifactory.devopsagent.requestlog.job;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.devopsagent.requestlog.dao.OutboundRequestLogDao;
import org.artifactory.storage.db.devopsagent.requestlog.dao.RequestLogDao;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLogsRetentionJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Test
    void nullContextSkipsExecution() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(null);

            new RequestLogsRetentionJob().onExecute(mock(JobExecutionContext.class));
        }
    }

    @Test
    void contextNotReadySkipsExecution() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.isReady()).thenReturn(false);

            new RequestLogsRetentionJob().onExecute(mock(JobExecutionContext.class));

            verify(ctx, never()).beanForType(RequestLogDao.class);
        }
    }

    @Test
    void happyPathCallsBothDaos() throws JobExecutionException, SQLException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.isReady()).thenReturn(true);
            RequestLogDao inboundDao = mock(RequestLogDao.class);
            OutboundRequestLogDao outboundDao = mock(OutboundRequestLogDao.class);
            when(ctx.beanForType(RequestLogDao.class)).thenReturn(inboundDao);
            when(ctx.beanForType(OutboundRequestLogDao.class)).thenReturn(outboundDao);

            long beforeMs = System.currentTimeMillis();
            new RequestLogsRetentionJob().onExecute(mock(JobExecutionContext.class));
            long afterMs = System.currentTimeMillis();

            ArgumentCaptor<Long> inboundCutoff = ArgumentCaptor.forClass(Long.class);
            verify(inboundDao).deleteOlderThanChunked(inboundCutoff.capture(), anyInt(), anyLong());
            verify(outboundDao).deleteOlderThanChunked(anyLong(), anyInt(), anyLong());

            long retentionDays = ConstantValues.devopsAgentRequestLogRetentionDays.getLong();
            long minCutoff = beforeMs - (retentionDays * 86_400_000L);
            long maxCutoff = afterMs - (retentionDays * 86_400_000L);
            assertTrue(inboundCutoff.getValue() >= minCutoff && inboundCutoff.getValue() <= maxCutoff,
                    "cutoff should be ~" + retentionDays + " days ago");
        }
    }

    @Test
    void sqlExceptionWrappedAsJobExecutionException() throws SQLException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.isReady()).thenReturn(true);
            RequestLogDao inboundDao = mock(RequestLogDao.class);
            OutboundRequestLogDao outboundDao = mock(OutboundRequestLogDao.class);
            when(ctx.beanForType(RequestLogDao.class)).thenReturn(inboundDao);
            when(ctx.beanForType(OutboundRequestLogDao.class)).thenReturn(outboundDao);
            when(inboundDao.deleteOlderThanChunked(anyLong(), anyInt(), anyLong()))
                    .thenThrow(new SQLException("timeout"));

            assertThrows(JobExecutionException.class,
                    () -> new RequestLogsRetentionJob().onExecute(mock(JobExecutionContext.class)));
        }
    }
}
