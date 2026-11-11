package org.artifactory.api.request;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.repo.RepoPath;
import org.artifactory.request.ArtifactoryRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GracefulShutdownAwareTest {

    private static final String UBER_TRACE_ID = GracefulShutdownAware.UBER_TRACE_ID;
    private final String TRACKING_STARTED_MSG = "Process {} tracking started";
    private final String TRACKING_STOPPED_MSG = "Process {} tracking stopped";

    private ArtifactoryRequest request;
    private ArtifactoryResponse response;
    private Logger log;
    private Cache<String, Object> tracker;
    private RepoPath repoPath;

    @BeforeEach
    void setUp() {
        request = mock(ArtifactoryRequest.class);
        response = mock(ArtifactoryResponse.class);
        log = mock(Logger.class);
        tracker = CacheBuilder.newBuilder().build();
        repoPath = mock(RepoPath.class);
        when(request.getRepoPath()).thenReturn(repoPath);
        MDC.remove(UBER_TRACE_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(UBER_TRACE_ID);
    }

    @Test
    void testTrackNoTraceIdSuccess() throws IOException {
        GracefulShutdownAware.Process process = mock(GracefulShutdownAware.Process.class);
        GracefulShutdownAware.track(process, request, response, tracker, log);

        verify(process).execute(request, response);
        verify(log).debug(eq("Process {} tracking started"), eq((Object) null));
        verify(log).debug(eq("Process {} tracking stopped"), eq((Object) null));
        assertTrue(tracker.asMap().isEmpty());
        verify(log, never()).warn(anyString(), (Object) any());
        verify(log, never()).error(anyString(), (Throwable) any());
        verify(log, never()).error(anyString(), (Object) any());
    }

    @Test
    void testTrackWithTraceIdSuccess() throws IOException {
        String traceId = "trace-123";
        MDC.put(UBER_TRACE_ID, traceId);
        GracefulShutdownAware.Process process = mock(GracefulShutdownAware.Process.class);

        GracefulShutdownAware.track(process, request, response, tracker, log);

        verify(process).execute(request, response);
        verify(log).debug(eq("Process {} tracking started"), eq((Object) traceId));
        verify(log).debug(eq("Process {} tracking stopped"), eq((Object) traceId));
        assertNull(tracker.getIfPresent(traceId));
        assertTrue(tracker.asMap().isEmpty());
        verify(log, never()).warn(anyString(), (Object) any());
        verify(log, never()).error(anyString(), (Throwable) any());
        verify(log, never()).error(anyString(), (Object) any());
    }

    @Test
    void testTrackShutdownIllegalStateException() throws Exception {
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        when(ctx.isReady()).thenReturn(false);

        try (MockedStatic<ContextHelper> mocked = mockStatic(ContextHelper.class)) {
            mocked.when(ContextHelper::get).thenReturn(ctx);

            GracefulShutdownAware.Process process = (req, resp) -> {
                throw new IllegalStateException("this web application instance has been stopped already");
            };

            assertDoesNotThrow(() -> GracefulShutdownAware.track(process, request, response, tracker, log));
            verify(log).warn(eq("Request processing interrupted during shutdown: {}"), eq((Object) repoPath),
                    any(Throwable.class));
        }
    }

    @Test
    void testTrackShutdownClassNotFoundExceptionInCauseChain() throws IOException {
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        when(ctx.isReady()).thenReturn(false);

        try (MockedStatic<ContextHelper> mocked = org.mockito.Mockito.mockStatic(ContextHelper.class)) {
            mocked.when(ContextHelper::get).thenReturn(ctx);

            RuntimeException wrapped = new RuntimeException("top", new ClassNotFoundException("stopped already"));
            GracefulShutdownAware.Process process = (req, resp) -> {throw wrapped;};

            assertDoesNotThrow(() -> GracefulShutdownAware.track(process, request, response, tracker, log));

            verify(log).warn(eq("Request processing interrupted during shutdown: {}"), eq((Object) repoPath),
                    eq(wrapped));
            verify(log, never()).error(anyString(), (Throwable) any());
            verify(log, never()).error(anyString(), (Object) any());
        }
    }

    @Test
    void testTrackShutdownErrorInCauseChain() throws IOException {
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        when(ctx.isReady()).thenReturn(false);

        try (MockedStatic<ContextHelper> mocked = org.mockito.Mockito.mockStatic(ContextHelper.class)) {
            mocked.when(ContextHelper::get).thenReturn(ctx);

            RuntimeException wrapped =
                    new RuntimeException("top", new IllegalStateException("something stopped already something"));
            GracefulShutdownAware.Process process = (req, resp) -> {throw wrapped;};

            assertDoesNotThrow(() -> GracefulShutdownAware.track(process, request, response, tracker, log));

            verify(log).warn(eq("Request processing interrupted during shutdown: {}"), eq((Object) repoPath),
                    any(Throwable.class));
            verify(log, never()).error(anyString(), (Throwable) any());
            verify(log, never()).error(anyString(), (Object) any());
        }
    }

    @Test
    void testTrackNonShutdownException() {
        String traceId = "trace-non-shutdown";
        MDC.put(UBER_TRACE_ID, traceId);
        RuntimeException boom = new RuntimeException("boom");
        GracefulShutdownAware.Process process = (req, resp) -> {throw boom;};

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> GracefulShutdownAware.track(process, request, response, tracker, log));

        assertSame(boom, thrown);

        verify(log).debug(eq("Process {} tracking started"), eq((Object) traceId));
        verify(log).debug(eq("Process {} tracking stopped"), eq((Object) traceId));
        assertNull(tracker.getIfPresent(traceId));
        assertTrue(tracker.asMap().isEmpty());
        verify(log, never()).warn(anyString(), (Object) any());
    }


    @Test
    void testTrackIllegalStateExceptionWithoutStoppedAlready() {
        IllegalStateException e = new IllegalStateException("different message");
        GracefulShutdownAware.Process process = (req, resp) -> {throw e;};

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> GracefulShutdownAware.track(process, request, response, tracker, log));

        assertSame(e, thrown);
        verify(log, never()).warn(anyString(), (Object) any());
    }


    @Test
    void testTrackIllegalStateExceptionNullMessage() {
        IllegalStateException e = new IllegalStateException((String) null);
        GracefulShutdownAware.Process process = (req, resp) -> {throw e;};

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> GracefulShutdownAware.track(process, request, response, tracker, log));

        assertSame(e, thrown);
        verify(log, never()).warn(anyString(), (Object) any());
    }


    @Test
    void testTrackIOException() {
        IOException ioException = new IOException("IO error");
        GracefulShutdownAware.Process process = (req, resp) -> {throw ioException;};

        IOException thrown = assertThrows(IOException.class,
                () -> GracefulShutdownAware.track(process, request, response, tracker, log));

        assertSame(ioException, thrown);

        verify(log, never()).warn(anyString(), (Object) any());
    }

    @Test
    void testTrackWithSupplierDoesNotSwallowException() throws Exception {
        ArtifactoryContext ctx = mock(ArtifactoryContext.class);
        when(ctx.isReady()).thenReturn(false);
        IllegalStateException shutdownLike =
                new IllegalStateException("this web application instance has been stopped already");

        try (MockedStatic<ContextHelper> mocked = mockStatic(ContextHelper.class)) {
            mocked.when(ContextHelper::get).thenReturn(ctx);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> GracefulShutdownAware.track(() -> {
                        throw shutdownLike;
                    }, tracker, log));
            assertSame(shutdownLike, thrown);
            // as opposed to the Process-based overload, 
            // the supplier overload does not swallow the exception and does not log a warning
            verify(log, never()).warn(anyString(), (Object) any());
            verify(log).debug(TRACKING_STARTED_MSG, (Object) null);
            verify(log).debug(TRACKING_STOPPED_MSG, (Object) null);
        }
    }

    @Test
    void testTrackSupplierWithMdcReturnsValueAndClearsTracker() {
        String traceId = "supplier-mdc-ok";
        MDC.put(UBER_TRACE_ID, traceId);
        assertEquals("result", GracefulShutdownAware.track(() -> "result", tracker, log));
        assertTrue(GracefulShutdownAware.noActiveTracking(tracker));
        verify(log).debug(TRACKING_STARTED_MSG, traceId);
        verify(log).debug(TRACKING_STOPPED_MSG, traceId);
    }

    @Test
    void testTrackSupplierWithoutMdcReturnsValueAndDoesNotRegisterInTracker() {
        assertEquals(7, GracefulShutdownAware.track(() -> 7, tracker, log));
        assertTrue(GracefulShutdownAware.noActiveTracking(tracker));
        verify(log).debug(TRACKING_STARTED_MSG, (Object) null);
        verify(log).debug(TRACKING_STOPPED_MSG, (Object) null);
    }

    @Test
    void testTrackSupplierWithMdcPropagatesExceptionAndClearsTracker() {
        String traceId = "supplier-mdc-fail";
        MDC.put(UBER_TRACE_ID, traceId);
        RuntimeException boom = new RuntimeException("supplier-boom");
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> GracefulShutdownAware.track(() -> {
                    throw boom;
                }, tracker, log));
        assertSame(boom, thrown);
        assertTrue(GracefulShutdownAware.noActiveTracking(tracker));
        verify(log).debug(TRACKING_STARTED_MSG, traceId);
        verify(log).debug(TRACKING_STOPPED_MSG, traceId);
    }

}
