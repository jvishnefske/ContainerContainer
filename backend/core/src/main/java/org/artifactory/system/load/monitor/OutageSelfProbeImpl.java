package org.artifactory.system.load.monitor;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.artifactory.spring.ConditionalOnExpression;
import org.artifactory.util.TomcatListeningDetector;
import org.jfrog.client.http.HttpBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.artifactory.api.rest.constant.SystemRestConstants.PATH_READINESS;
import static org.artifactory.system.load.monitor.SystemLoadMonitorConditions.OUTAGE_SELF_PROBE_ENABLED_EXPRESSION;

/**
 * Active implementation of {@link OutageSelfProbe}.
 * <p>
 * Runs on a virtual thread via a scheduled executor (not a Tomcat worker) to avoid self-deadlock
 * when all Tomcat threads are exhausted. If the probe cannot complete within the configured
 * timeout on any active connector, the system is effectively in outage from the client's perspective.
 * <p>
 * Both the HTTP connector and the HTTPS connector (if present) are probed each cycle. Outage is
 * declared when either connector probe times out.
 *
 * @author Amir Avidan
 */
@Slf4j
@Component
@ConditionalOnExpression(OUTAGE_SELF_PROBE_ENABLED_EXPRESSION)
public class OutageSelfProbeImpl implements OutageSelfProbe {

    static final String METRIC_LATENCY = "system.load.probe.latency";
    static final String METRIC_TIMEOUT = "system.load.probe.timeout";
    static final String TAG_CONNECTOR = "connector";
    static final String CONNECTOR_HTTP = "http";
    static final String CONNECTOR_HTTPS = "https";

    private static final int NO_HTTPS_PORT = -1;

    private final AtomicReference<ProbeResult> latestResult = new AtomicReference<>(ProbeResult.INITIAL);
    private final int timeoutSeconds;
    private final CloseableHttpClient httpClient;
    private final ScheduledExecutorService executor;
    private final int intervalSeconds;
    private final Timer httpLatency;
    private final Timer httpsLatency;
    private final Counter httpTimeoutCounter;
    private final Counter httpsTimeoutCounter;

    private String httpUrl;
    private String httpsUrl;

    @VisibleForTesting
    @Setter(AccessLevel.PACKAGE)
    private LongSupplier timeSupplier = System::currentTimeMillis;

    @Autowired
    OutageSelfProbeImpl(
            @Value("${artifactory.system.load.outage.probe.timeout.seconds:25}") int timeoutSeconds,
            @Value("${artifactory.system.load.outage.probe.interval.seconds:15}") int intervalSeconds,
            MeterRegistry meterRegistry) {
        this(timeoutSeconds, intervalSeconds, buildHttpClient(timeoutSeconds), createDefaultExecutor(), meterRegistry);
    }

    @VisibleForTesting
    OutageSelfProbeImpl(int timeoutSeconds, int intervalSeconds, CloseableHttpClient httpClient,
            ScheduledExecutorService executor, MeterRegistry meterRegistry) {
        this.timeoutSeconds = timeoutSeconds;
        this.intervalSeconds = intervalSeconds;
        this.httpClient = httpClient;
        this.executor = executor;
        this.httpLatency = buildLatencyTimer(meterRegistry, CONNECTOR_HTTP);
        this.httpsLatency = buildLatencyTimer(meterRegistry, CONNECTOR_HTTPS);
        this.httpTimeoutCounter = buildTimeoutCounter(meterRegistry, CONNECTOR_HTTP);
        this.httpsTimeoutCounter = buildTimeoutCounter(meterRegistry, CONNECTOR_HTTPS);
    }

    private static Timer buildLatencyTimer(MeterRegistry registry, String connector) {
        return Timer.builder(METRIC_LATENCY)
                .tag(TAG_CONNECTOR, connector)
                .description("Self-diagnostic probe round-trip latency per cycle")
                .register(registry);
    }

    private static Counter buildTimeoutCounter(MeterRegistry registry, String connector) {
        return Counter.builder(METRIC_TIMEOUT)
                .tag(TAG_CONNECTOR, connector)
                .description("Number of self-diagnostic probe timeouts")
                .register(registry);
    }

    @Override
    @PostConstruct
    public void start() {
        int httpPort = TomcatListeningDetector.detectMainListeningPort();
        if (httpPort <= 0) {
            log.warn("Could not detect Tomcat HTTP listening port — outage self-probe will not start");
            return;
        }
        int httpsPort = TomcatListeningDetector.detectHttpsListeningPort().orElse(NO_HTTPS_PORT);
        start(httpPort, httpsPort);
    }

    @VisibleForTesting
    void start(int httpPort, int httpsPort) {
        this.httpUrl = buildUrl(CONNECTOR_HTTP, httpPort);
        this.httpsUrl = httpsPort > 0 ? buildUrl(CONNECTOR_HTTPS, httpsPort) : null;
        log.info("Starting outage self-diagnostic probe: interval={}s, timeout={}s, http={}, https={}",
                intervalSeconds, timeoutSeconds, httpUrl, httpsUrl != null ? httpsUrl : "disabled");
        executor.scheduleAtFixedRate(this::safeExecuteProbe, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    @PreDestroy
    public void stop() {
        log.info("Shutting down outage self-diagnostic probe");
        executor.shutdownNow();
    }

    @Override
    public ProbeResult getLatestResult() {
        return latestResult.get();
    }

    /**
     * Wraps {@link #executeProbe()} to guarantee no {@link Exception} escapes into the
     * {@link ScheduledExecutorService}. An uncaught exception in a scheduled task silently
     * kills future executions with no restart — this wrapper prevents that.
     * {@link Error}s (e.g. OOM) are intentionally left uncaught and propagate upward.
     */
    private void safeExecuteProbe() {
        try {
            executeProbe();
        } catch (Exception e) {
            log.error("Unexpected error in outage self-probe, probe will continue on next cycle", e);
        }
    }

    private void executeProbe() {
        ConnectorProbeOutcome httpOutcome = runConnectorProbe(httpUrl, CONNECTOR_HTTP, httpLatency, httpTimeoutCounter);
        ConnectorProbeOutcome httpsOutcome = httpsUrl != null
                ? runConnectorProbe(httpsUrl, CONNECTOR_HTTPS, httpsLatency, httpsTimeoutCounter)
                : ConnectorProbeOutcome.SKIPPED;

        // Every probe failed with a non-timeout IOException (e.g. connection refused). We have no
        // new measurement — keep the previous ProbeResult instead of publishing a fake (0ms, healthy)
        // reading that would mask the failure from downstream consumers.
        if (httpOutcome.skipped() && httpsOutcome.skipped()) {
            return;
        }
        boolean timedOut = httpOutcome.timedOut() || httpsOutcome.timedOut();
        long latencyMs = Math.max(httpOutcome.latencyMs(), httpsOutcome.latencyMs());
        latestResult.set(new ProbeResult(latencyMs, timedOut, timeSupplier.getAsLong()));
    }

    private ConnectorProbeOutcome runConnectorProbe(String url, String connectorTag, Timer latency,
            Counter timeoutCounter) {
        long startTime = timeSupplier.getAsLong();
        try (CloseableHttpResponse response = httpClient.execute(new HttpGet(url))) {
            EntityUtils.consume(response.getEntity());
            long latencyMs = timeSupplier.getAsLong() - startTime;
            latency.record(latencyMs, TimeUnit.MILLISECONDS);
            log.debug("Self-diagnostic probe [{}] completed in {}ms", connectorTag, latencyMs);
            return new ConnectorProbeOutcome(latencyMs, false, false);
        } catch (ConnectTimeoutException | SocketTimeoutException e) {
            long latencyMs = timeSupplier.getAsLong() - startTime;
            latency.record(latencyMs, TimeUnit.MILLISECONDS);
            timeoutCounter.increment();
            log.warn("Self-diagnostic probe [{}] timed out after {}ms (threshold={}s, cause={}: {}) — treating as " +
                            "outage", connectorTag, latencyMs, timeoutSeconds, e.getClass().getSimpleName(),
                    e.getMessage());
            return new ConnectorProbeOutcome(latencyMs, true, false);
        } catch (IOException e) {
            log.warn("Outage probe [{}] failed (non-timeout) — leaving previous result unchanged", connectorTag, e);
            return ConnectorProbeOutcome.SKIPPED;
        }
    }

    private static String buildUrl(String scheme, int port) {
        return scheme + "://localhost:" + port + PATH_READINESS;
    }

    /**
     * Builds the HTTP client used for both HTTP and HTTPS probes. Follows the same pattern as
     * {@code RetentionHttpUtils.buildRetentionHttpClient()}: {@code trustSelfSignCert} and
     * {@code noHostVerification} are required because Tomcat's HTTPS connector always uses a
     * self-signed certificate on localhost. The SSL settings are ignored for plain HTTP connections.
     */
    private static CloseableHttpClient buildHttpClient(int timeoutSeconds) {
        int timeoutMs = (int) TimeUnit.SECONDS.toMillis(timeoutSeconds);
        return new HttpBuilder()
                .connectionTimeout(timeoutMs)
                .socketTimeout(timeoutMs)
                .trustSelfSignCert(true)
                .noHostVerification(true)
                .noRetry()
                .build();
    }

    private static ScheduledExecutorService createDefaultExecutor() {
        return Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual()
                .name("outage-self-probe")
                .factory());
    }

    private record ConnectorProbeOutcome(long latencyMs, boolean timedOut, boolean skipped) {
        static final ConnectorProbeOutcome SKIPPED = new ConnectorProbeOutcome(0, false, true);
    }
}
