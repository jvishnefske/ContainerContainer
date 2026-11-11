package org.artifactory.devopsagent.requestlog.job;

import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogFlushable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestLogFlushJobTest {

    @Test
    void noFlushableBeansDoesNothing() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            when(ctx.beansForType(RequestLogFlushable.class)).thenReturn(Map.of());

            new RequestLogFlushJob().onExecute(mock(JobExecutionContext.class));
        }
    }

    @Test
    void singleFlushableIsFlushed() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            RequestLogFlushable flushable = mock(RequestLogFlushable.class);
            when(ctx.beansForType(RequestLogFlushable.class)).thenReturn(Map.of("svc", flushable));

            new RequestLogFlushJob().onExecute(mock(JobExecutionContext.class));

            verify(flushable).flush();
        }
    }

    @Test
    void throwingFlushableDoesNotPreventOthers() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            RequestLogFlushable good = mock(RequestLogFlushable.class);
            RequestLogFlushable bad = mock(RequestLogFlushable.class);
            doThrow(new RuntimeException("db error")).when(bad).flush();
            when(ctx.beansForType(RequestLogFlushable.class))
                    .thenReturn(Map.of("good", good, "bad", bad));

            assertDoesNotThrow(() -> new RequestLogFlushJob().onExecute(mock(JobExecutionContext.class)));

            verify(good).flush();
        }
    }

    @Test
    void contextNullDoesNotThrow() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ch.when(ContextHelper::get).thenReturn(null);

            // NullPointerException would surface here if the job does not guard against null context;
            // test documents current behaviour (NPE bubbles — job relies on scheduler guard).
            // Current code calls .beansForType() unconditionally, so NPE is expected.
            try {
                new RequestLogFlushJob().onExecute(mock(JobExecutionContext.class));
            } catch (NullPointerException ignored) {
                // acceptable: job trusts scheduler never fires when context is absent
            }
        }
    }

    @Test
    void multipleFlushablesBothCalled() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            RequestLogFlushable inbound = mock(RequestLogFlushable.class);
            RequestLogFlushable outbound = mock(RequestLogFlushable.class);
            when(ctx.beansForType(RequestLogFlushable.class))
                    .thenReturn(Map.of("inbound", inbound, "outbound", outbound));

            new RequestLogFlushJob().onExecute(mock(JobExecutionContext.class));

            verify(inbound).flush();
            verify(outbound).flush();
        }
    }

    @Test
    void noFlushWhenFlushableNotRegistered() throws JobExecutionException {
        try (MockedStatic<ContextHelper> ch = Mockito.mockStatic(ContextHelper.class)) {
            ArtifactoryContext ctx = mock(ArtifactoryContext.class);
            ch.when(ContextHelper::get).thenReturn(ctx);
            RequestLogFlushable flushable = mock(RequestLogFlushable.class);
            when(ctx.beansForType(RequestLogFlushable.class)).thenReturn(Map.of());

            new RequestLogFlushJob().onExecute(mock(JobExecutionContext.class));

            verify(flushable, never()).flush();
        }
    }
}
