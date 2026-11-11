package org.artifactory.probes.tomcat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.spring.ConditionalOnExpression;
import org.artifactory.throttling.lh.service.LoadHealerReactiveModeMonitorService;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.artifactory.probes.tomcat.ServerReadinessMonitor.SERVER_READINESS_MONITOR_ENABLED_EXPRESSION;

/**
 * @author Igor Usenko
 */
@Slf4j
@Component
@ConditionalOnExpression(SERVER_READINESS_MONITOR_ENABLED_EXPRESSION)
public class TomcatReadinessMonitor implements ServerReadinessMonitor {

    @Getter(lazy = true)
    private final Map<String, ConnectorMetric> metrics = initMetrics();
    private final ConnectorsMonitor connectorsMonitor;
    private final AtomicInteger failedMetricsCount;
    private final TomcatMonitorMode mode;
    private final int reactiveModeFailedCount;
    private final int maxFailedMetricsCount;
    private final int minProcessedRequestsCount;
    private final LoadHealerService loadHealerService;
    private final Set<String> activeConnectorsNames;
    private final LoadHealerReactiveModeMonitorService loadHealerReactiveModeMonitorService;

    @Autowired
    public TomcatReadinessMonitor(
            ConnectorsMonitor connectorsMonitor,
            MeterRegistry meterRegistry,
            @Value("${artifactory.tomcat.monitor.mode}") String mode,
            @Value("${artifactory.tomcat.monitor.reactive.mode.failed.count}") int reactiveModeFailedCount,
            @Value("${artifactory.tomcat.monitor.max.failed.count}") int maxFailedMetricsCount,
            @Value("${artifactory.tomcat.monitor.min.request.count}") int minProcessedRequestsCount,
            LoadHealerService loadHealerService,
            LoadHealerReactiveModeMonitorService loadHealerReactiveModeMonitorService

    ) {
        this.connectorsMonitor = connectorsMonitor;
        this.activeConnectorsNames = connectorsMonitor.getActiveConnectorsMetrics().stream().map(ConnectorMetric::name)
                .collect(Collectors.toSet());
        this.mode = TomcatMonitorMode.fromString(mode);
        this.reactiveModeFailedCount = reactiveModeFailedCount;
        this.maxFailedMetricsCount = maxFailedMetricsCount;
        this.minProcessedRequestsCount = minProcessedRequestsCount;
        this.loadHealerService = loadHealerService;
        this.loadHealerReactiveModeMonitorService = loadHealerReactiveModeMonitorService;
        this.failedMetricsCount = new AtomicInteger(0);
        Gauge.builder("tomcat.failed.metrics", failedMetricsCount::get)
                .description("The number of failed Tomcat metrics at the moment")
                .register(meterRegistry);
        log.info("TomcatReadinessMonitor properties mode: {}, reactiveModeFailedCount: {}, maxFailedMetricsCount: {}, "
                        + "minProcessedRequestsCount: {}",
                mode, reactiveModeFailedCount, maxFailedMetricsCount, minProcessedRequestsCount);
    }

    @Override
    public boolean isReady() {
        if (TomcatMonitorMode.OFF == mode) {
            return true;
        }

        update();

        int count = failedMetricsCount.get();
        if (count > 0) {
            log.info("Tomcat failed metrics count: {}", count);
        }

        if (count >= reactiveModeFailedCount) {
            log.info("Activating reactive mode due Tomcat readiness failure");
            loadHealerReactiveModeMonitorService.activateReactiveMode();
        }

        // Readiness fails at higher threshold (after healing window)
        boolean isReady = count < maxFailedMetricsCount;
        log.debug("Tomcat readiness {}", isReady);
        return TomcatMonitorMode.MONITOR == mode || isReady;
    }

    @PostConstruct
    public void postConstruct() {
        log.info("Server readiness monitor mode {}", mode);
    }

    private void update() {
        boolean failed = false;
        for (String name : getMetrics().keySet()) {
            boolean isReady = updateMetric(name, getMetrics(), this::isReady);
            if (!isReady) {
                log.warn("Connector {} is not ready.", name);
            }
            failed |= !isReady;
        }

        int counter = failed ? failedMetricsCount.incrementAndGet() : failedMetricsCount.getAndSet(0);
        log.debug("Failed metrics counter : {}", counter);
    }

    private boolean isReady(ConnectorMetric oldMetric, ConnectorMetric newMetric) {
        if (activeConnectorsNames.contains(newMetric.name())) {
            newMetric.checkHeavyLoadState(loadHealerService);
        }

        if (oldMetric != null) {
            long processed = newMetric.processedCount() - oldMetric.processedCount();
            return (newMetric.currentThreadsBusy() < newMetric.maxThreads()) ||
                    (processed >= minProcessedRequestsCount);
        } else {
            return true;
        }
    }

    private boolean updateMetric(String name, Map<String, ConnectorMetric> metrics,
            BiPredicate<ConnectorMetric, ConnectorMetric> readinessPredicate) {
        Optional<ConnectorMetric> optional = connectorsMonitor.getConnectorMetric(name);
        if (optional.isEmpty()) {
            log.warn("No metric received for connector {}", name);
            return true;
        }
        ConnectorMetric newMetric = optional.get();
        ConnectorMetric oldMetric = metrics.put(name, newMetric);
        log.debug("Added metric for connector {} : {}", name, newMetric);

        return readinessPredicate.test(oldMetric, newMetric);
    }

    @SuppressWarnings("DataFlowIssue") // connectorsMonitor cannot be null since we reach here through a lazy getter
    private Map<String, ConnectorMetric> initMetrics() {
        Map<String, ConnectorMetric> metricsFound = new HashMap<>();

        connectorsMonitor.getConnectorNames()
                .forEach(name -> updateMetric(name, metricsFound, (oldMetric, newMetric) -> true));

        log.info("Initial connectors metrics");
        metricsFound.keySet().forEach(
                name -> log.info("Connector name {}, metric {}", name, metricsFound.get(name))
        );

        return metricsFound;
    }
}