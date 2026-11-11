package org.artifactory.system.load.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.artifactory.util.TomcatListeningDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.artifactory.system.load.monitor.OutageSelfProbeImpl.CONNECTOR_HTTP;
import static org.artifactory.system.load.monitor.OutageSelfProbeImpl.CONNECTOR_HTTPS;
import static org.artifactory.system.load.monitor.OutageSelfProbeImpl.METRIC_LATENCY;
import static org.artifactory.system.load.monitor.OutageSelfProbeImpl.METRIC_TIMEOUT;
import static org.artifactory.system.load.monitor.OutageSelfProbeImpl.TAG_CONNECTOR;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class OutageSelfProbeTest {

    private static final int TIMEOUT_SECONDS = 25;
    private static final int INTERVAL_SECONDS = 15;

    private CloseableHttpClient httpClient;
    private ScheduledExecutorService mockExecutor;
    private OutageSelfProbeImpl probe;
    private AtomicLong clock;
    private Runnable probeTask;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        httpClient = mock(CloseableHttpClient.class);
        mockExecutor = mock(ScheduledExecutorService.class);
        clock = new AtomicLong(1000L);
        meterRegistry = new SimpleMeterRegistry();

        doAnswer(invocation -> {
            probeTask = invocation.getArgument(0);
            return mock(ScheduledFuture.class);
        }).when(mockExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        probe = new OutageSelfProbeImpl(TIMEOUT_SECONDS, INTERVAL_SECONDS, httpClient, mockExecutor, meterRegistry);
        probe.setTimeSupplier(clock::get);
        probe.start(8081, -1);
    }

    @Test
    void startSchedulesProbeAtConfiguredInterval() {
        verify(mockExecutor).scheduleAtFixedRate(
                any(Runnable.class), eq((long) INTERVAL_SECONDS), eq((long) INTERVAL_SECONDS), eq(TimeUnit.SECONDS));
    }

    @Test
    void safeExecuteProbeSwallowsUnexpectedExceptionsAndContinues() throws IOException {
        probe.setTimeSupplier(() -> {
            throw new RuntimeException("unexpected internal error");
        });
        assertDoesNotThrow(() -> probeTask.run());
        assertEquals(ProbeResult.INITIAL, probe.getLatestResult());

        probe.setTimeSupplier(clock::get);
        CloseableHttpResponse response = successResponse();
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(50);
            return response;
        });

        probeTask.run();

        assertFalse(probe.getLatestResult().timedOut());
        assertEquals(50, probe.getLatestResult().latencyMs());
    }

    @Test
    void safeExecuteProbeDoesNotCatchErrors() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenThrow(new OutOfMemoryError("simulated OOM"));

        assertThrows(OutOfMemoryError.class, () -> probeTask.run());
    }

    @Test
    void initialResultIsNotTimedOut() {
        ProbeResult result = probe.getLatestResult();
        assertFalse(result.timedOut());
        assertEquals(0, result.latencyMs());
    }

    @Test
    void probeSuccessRecordsLatency() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(150);
            return successResponse();
        });

        probeTask.run();

        ProbeResult result = probe.getLatestResult();
        assertFalse(result.timedOut());
        assertEquals(150, result.latencyMs());
        assertTrue(result.timestamp() > 0);
    }

    @Test
    void probeSocketTimeoutRecordsFailure() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(25_000);
            throw new SocketTimeoutException("timed out");
        });

        probeTask.run();

        ProbeResult result = probe.getLatestResult();
        assertTrue(result.timedOut());
        assertEquals(25_000, result.latencyMs());
    }

    @Test
    void probeConnectTimeoutRecordsFailure() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(25_000);
            throw new ConnectTimeoutException("connect timed out");
        });

        probeTask.run();

        ProbeResult result = probe.getLatestResult();
        assertTrue(result.timedOut());
        assertEquals(25_000, result.latencyMs());
    }

    @Test
    void probeNonTimeoutIOExceptionDoesNotMarkAsTimedOut() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(50);
            throw new IOException("Connection refused");
        });

        probeTask.run();

        ProbeResult result = probe.getLatestResult();
        assertFalse(result.timedOut(), "Non-timeout IOException must not mark probe as timed out");
        assertEquals(0, result.latencyMs(), "Initial result must be preserved on non-timeout failure");
    }

    @Test
    void probeNonTimeoutIOExceptionPreservesPreviousSuccess() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(120);
            return successResponse();
        });
        probeTask.run();
        ProbeResult successResult = probe.getLatestResult();

        when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException("Connection refused"));
        probeTask.run();

        ProbeResult result = probe.getLatestResult();
        assertFalse(result.timedOut());
        assertEquals(successResult.latencyMs(), result.latencyMs(), "Previous success latency must be preserved");
        assertEquals(successResult.timestamp(), result.timestamp(), "Previous timestamp must be preserved");
    }

    @Test
    void probeInterruptedIOExceptionPreservesResult() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenThrow(new InterruptedIOException("interrupted"));

        probeTask.run();

        ProbeResult result = probe.getLatestResult();
        assertFalse(result.timedOut(), "InterruptedIOException must not mark probe as timed out");
        assertEquals(0, result.latencyMs(), "Initial result must be preserved");
    }

    @Test
    void stopShutsDownExecutor() {
        probe.stop();
        verify(mockExecutor).shutdownNow();
    }

    @Test
    void springConstructorCreatesUsableProbe() {
        OutageSelfProbeImpl springProbe = new OutageSelfProbeImpl(10, 5, new SimpleMeterRegistry());

        assertEquals(ProbeResult.INITIAL, springProbe.getLatestResult());
        assertDoesNotThrow(springProbe::stop);
    }

    @Test
    void startNoArgDoesNotStartWhenPortNotDetected() {
        ScheduledExecutorService freshExecutor = mock(ScheduledExecutorService.class);
        OutageSelfProbeImpl freshProbe =
                new OutageSelfProbeImpl(TIMEOUT_SECONDS, INTERVAL_SECONDS, httpClient, freshExecutor,
                        new SimpleMeterRegistry());

        try (MockedStatic<TomcatListeningDetector> mocked = mockStatic(TomcatListeningDetector.class)) {
            mocked.when(TomcatListeningDetector::detectMainListeningPort).thenReturn(0);
            mocked.when(TomcatListeningDetector::detectHttpsListeningPort).thenReturn(Optional.empty());
            freshProbe.start();
            verifyNoInteractions(freshExecutor);
        }
    }

    @Test
    void startNoArgStartsWithDetectedHttpPort() {
        ScheduledExecutorService freshExecutor = mock(ScheduledExecutorService.class);
        OutageSelfProbeImpl freshProbe =
                new OutageSelfProbeImpl(TIMEOUT_SECONDS, INTERVAL_SECONDS, httpClient, freshExecutor,
                        new SimpleMeterRegistry());

        try (MockedStatic<TomcatListeningDetector> mocked = mockStatic(TomcatListeningDetector.class)) {
            mocked.when(TomcatListeningDetector::detectMainListeningPort).thenReturn(8082);
            mocked.when(TomcatListeningDetector::detectHttpsListeningPort).thenReturn(Optional.empty());
            freshProbe.start();
            verify(freshExecutor).scheduleAtFixedRate(
                    any(Runnable.class), eq((long) INTERVAL_SECONDS), eq((long) INTERVAL_SECONDS),
                    eq(TimeUnit.SECONDS));
        }
    }

    @Test
    void startNoArgEnablesHttpsProbeWhenHttpsPortDetected() throws IOException {
        ScheduledExecutorService freshExecutor = mock(ScheduledExecutorService.class);
        Runnable[] capturedTask = new Runnable[1];
        doAnswer(inv -> {
            capturedTask[0] = inv.getArgument(0);
            return mock(ScheduledFuture.class);
        })
                .when(freshExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        OutageSelfProbeImpl freshProbe =
                new OutageSelfProbeImpl(TIMEOUT_SECONDS, INTERVAL_SECONDS, httpClient, freshExecutor,
                        new SimpleMeterRegistry());
        freshProbe.setTimeSupplier(clock::get);
        CloseableHttpResponse response = successResponse();
        when(httpClient.execute(any(HttpGet.class))).thenReturn(response);

        try (MockedStatic<TomcatListeningDetector> mocked = mockStatic(TomcatListeningDetector.class)) {
            mocked.when(TomcatListeningDetector::detectMainListeningPort).thenReturn(8081);
            mocked.when(TomcatListeningDetector::detectHttpsListeningPort).thenReturn(Optional.of(8443));
            freshProbe.start();
        }

        verify(freshExecutor).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());

        capturedTask[0].run();
        verify(httpClient).execute(argThat(
                req -> req != null && "http".equals(req.getURI().getScheme()) && req.getURI().getPort() == 8081));
        verify(httpClient).execute(argThat(
                req -> req != null && "https".equals(req.getURI().getScheme()) && req.getURI().getPort() == 8443));
    }

    @Test
    void httpsProbeTimeoutEscalatesToOutageEvenWhenHttpSucceeds() throws IOException {
        OutageSelfProbeImpl dualProbe = createDualProbe(new SimpleMeterRegistry());

        stubByScheme(
                () -> {
                    clock.addAndGet(100);
                    return successResponse();
                },
                () -> {
                    clock.addAndGet(25_000);
                    throw new SocketTimeoutException("timed out");
                });

        probeTask.run();

        ProbeResult result = dualProbe.getLatestResult();
        assertTrue(result.timedOut(), "HTTPS timeout must escalate overall result to outage");
        assertEquals(25_000, result.latencyMs(), "Latency must be the worst-case (HTTPS timeout)");
    }

    @Test
    void httpProbeTimeoutEscalatesToOutageEvenWhenHttpsSucceeds() throws IOException {
        OutageSelfProbeImpl dualProbe = createDualProbe(new SimpleMeterRegistry());

        stubByScheme(
                () -> {
                    clock.addAndGet(25_000);
                    throw new SocketTimeoutException("timed out");
                },
                () -> {
                    clock.addAndGet(100);
                    return successResponse();
                });

        probeTask.run();

        ProbeResult result = dualProbe.getLatestResult();
        assertTrue(result.timedOut(), "HTTP timeout must escalate overall result to outage");
        assertEquals(25_000, result.latencyMs(), "Latency must be the worst-case (HTTP timeout)");
    }

    @Test
    void bothProbesSucceedingResultsInHealthy() throws IOException {
        OutageSelfProbeImpl dualProbe = createDualProbe(new SimpleMeterRegistry());

        stubByScheme(
                () -> {
                    clock.addAndGet(100);
                    return successResponse();
                },
                () -> {
                    clock.addAndGet(200);
                    return successResponse();
                });

        probeTask.run();

        ProbeResult result = dualProbe.getLatestResult();
        assertFalse(result.timedOut());
        assertEquals(200, result.latencyMs(), "Latency must be max of both probes");
    }

    @Test
    void httpsProbeIsSkippedWhenStartedWithoutHttpsPort() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(inv -> {
            clock.addAndGet(100);
            return successResponse();
        });

        probeTask.run();

        verify(httpClient).execute(argThat(req -> req != null && "http".equals(req.getURI().getScheme())));
        verify(httpClient, never())
                .execute(argThat(req -> req != null && "https".equals(req.getURI().getScheme())));
    }

    @Test
    void bothProbesTimingOutIncrementsBothCountersAndReportsOutage() throws IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutageSelfProbeImpl dualProbe = createDualProbe(registry);

        when(httpClient.execute(any(HttpGet.class))).thenAnswer(inv -> {
            clock.addAndGet(25_000);
            throw new SocketTimeoutException("timed out");
        });

        probeTask.run();

        ProbeResult result = dualProbe.getLatestResult();
        assertTrue(result.timedOut(), "Both connectors timing out must report outage");
        assertEquals(25_000, result.latencyMs());

        Counter httpTimeout = registry.find(METRIC_TIMEOUT).tag(TAG_CONNECTOR, CONNECTOR_HTTP).counter();
        Counter httpsTimeout = registry.find(METRIC_TIMEOUT).tag(TAG_CONNECTOR, CONNECTOR_HTTPS).counter();
        assertNotNull(httpTimeout);
        assertNotNull(httpsTimeout);
        assertEquals(1.0, httpTimeout.count(), "HTTP timeout counter must increment when HTTP times out");
        assertEquals(1.0, httpsTimeout.count(), "HTTPS timeout counter must increment when HTTPS times out");
    }

    @Test
    void httpsNonTimeoutIOExceptionWhileHttpSucceedsReportsHealthy() throws IOException {
        OutageSelfProbeImpl dualProbe = createDualProbe(new SimpleMeterRegistry());

        stubByScheme(
                () -> {
                    clock.addAndGet(80);
                    return successResponse();
                },
                () -> {throw new IOException("SSL handshake failed");});

        probeTask.run();

        ProbeResult result = dualProbe.getLatestResult();
        assertFalse(result.timedOut(), "HTTPS non-timeout failure must not escalate to outage");
        assertEquals(80, result.latencyMs(), "Latency must reflect the HTTP probe (HTTPS skipped on IOException)");
    }

    @Test
    void bothConnectorsNonTimeoutIOExceptionPreservesPreviousResult() throws IOException {
        OutageSelfProbeImpl dualProbe = createDualProbe(new SimpleMeterRegistry());

        stubByScheme(
                () -> {
                    clock.addAndGet(120);
                    return successResponse();
                },
                () -> {
                    clock.addAndGet(140);
                    return successResponse();
                });
        probeTask.run();
        ProbeResult previous = dualProbe.getLatestResult();
        assertFalse(previous.timedOut());
        assertEquals(140, previous.latencyMs());

        when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException("Connection refused"));
        probeTask.run();

        ProbeResult result = dualProbe.getLatestResult();
        assertFalse(result.timedOut(), "Both connectors failing with non-timeout IOException must not escalate");
        assertEquals(previous.latencyMs(), result.latencyMs(), "Previous latency must be preserved");
        assertEquals(previous.timestamp(), result.timestamp(), "Previous timestamp must be preserved");
    }

    @Test
    void probeSuccessRecordsLatencyMetricWithConnectorTag() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(150);
            return successResponse();
        });

        probeTask.run();

        Timer latencyTimer = meterRegistry.find(METRIC_LATENCY).tag(TAG_CONNECTOR, CONNECTOR_HTTP).timer();
        assertNotNull(latencyTimer, "Latency timer must be registered for http connector");
        assertEquals(1, latencyTimer.count());
        assertEquals(150.0, latencyTimer.totalTime(TimeUnit.MILLISECONDS), 1.0);
    }

    @Test
    void probeTimeoutIncrementsTimeoutCounterWithConnectorTag() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(TIMEOUT_SECONDS * 1000L);
            throw new SocketTimeoutException("timed out");
        });

        probeTask.run();

        Counter timeoutCounter = meterRegistry.find(METRIC_TIMEOUT).tag(TAG_CONNECTOR, CONNECTOR_HTTP).counter();
        assertNotNull(timeoutCounter, "Timeout counter must be registered for http connector");
        assertEquals(1.0, timeoutCounter.count());
        Timer latencyTimer = meterRegistry.find(METRIC_LATENCY).tag(TAG_CONNECTOR, CONNECTOR_HTTP).timer();
        assertNotNull(latencyTimer);
        assertEquals(1, latencyTimer.count(), "Latency must also be recorded on timeout");
    }

    @Test
    void timeoutCounterDoesNotIncrementOnProbeSuccess() throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(invocation -> {
            clock.addAndGet(100);
            return successResponse();
        });

        probeTask.run();

        Counter timeoutCounter = meterRegistry.find(METRIC_TIMEOUT).tag(TAG_CONNECTOR, CONNECTOR_HTTP).counter();
        assertNotNull(timeoutCounter);
        assertEquals(0.0, timeoutCounter.count(), "Timeout counter must not increment on success");
    }

    @Test
    void httpsProbeTimeoutRecordsMetricsWithHttpsTag() throws IOException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutageSelfProbeImpl dualProbe = createDualProbe(registry);

        stubByScheme(
                () -> {
                    clock.addAndGet(100);
                    return successResponse();
                },
                () -> {
                    clock.addAndGet(25_000);
                    throw new SocketTimeoutException("timed out");
                });

        probeTask.run();

        Counter httpsTimeoutCounter = registry.find(METRIC_TIMEOUT).tag(TAG_CONNECTOR, CONNECTOR_HTTPS).counter();
        assertNotNull(httpsTimeoutCounter);
        assertEquals(1.0, httpsTimeoutCounter.count(), "Timeout counter must increment for https connector");

        Counter httpTimeoutCounter = registry.find(METRIC_TIMEOUT).tag(TAG_CONNECTOR, CONNECTOR_HTTP).counter();
        assertNotNull(httpTimeoutCounter);
        assertEquals(0.0, httpTimeoutCounter.count(), "HTTP timeout counter must not increment when HTTP succeeded");
    }

    private CloseableHttpResponse successResponse() {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getEntity()).thenReturn(null);
        return response;
    }


    @FunctionalInterface
    private interface ResponseSupplier {
        CloseableHttpResponse get() throws IOException;
    }

    private void stubByScheme(ResponseSupplier httpResponse, ResponseSupplier httpsResponse) throws IOException {
        when(httpClient.execute(any(HttpGet.class))).thenAnswer(inv -> {
            HttpGet req = inv.getArgument(0);
            return "https".equals(req.getURI().getScheme()) ? httpsResponse.get() : httpResponse.get();
        });
    }


    private OutageSelfProbeImpl createDualProbe(MeterRegistry registry) {
        ScheduledExecutorService exec = mock(ScheduledExecutorService.class);
        doAnswer(inv -> {
            probeTask = inv.getArgument(0);
            return mock(ScheduledFuture.class);
        }).when(exec).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        OutageSelfProbeImpl p = new OutageSelfProbeImpl(TIMEOUT_SECONDS, INTERVAL_SECONDS, httpClient, exec, registry);
        p.setTimeSupplier(clock::get);
        p.start(8081, 8443);
        return p;
    }
}
