package org.artifactory.system.load.monitor;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.probes.tomcat.ConnectorMetric;
import org.artifactory.probes.tomcat.ConnectorsMonitor;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.schedule.TaskUtils;
import org.artifactory.spring.ConditionalOnExpression;
import org.artifactory.system.load.event.ConnectorLoadState;
import org.artifactory.system.load.event.SystemLoadEventData;
import org.artifactory.system.load.event.SystemLoadState;
import org.artifactory.system.load.listener.SystemLoadEventListener;
import org.artifactory.system.load.monitor.job.SystemLoadMonitorJob;
import org.artifactory.util.LogUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.artifactory.system.load.event.ConnectorLoadState.HEALTHY;
import static org.artifactory.system.load.event.ConnectorLoadState.OUTAGE;
import static org.artifactory.system.load.monitor.SystemLoadMonitorConditions.SYSTEM_LOAD_MONITOR_ENABLED_EXPRESSION;

@Slf4j
@Service
@ConditionalOnExpression(SYSTEM_LOAD_MONITOR_ENABLED_EXPRESSION)
public class SystemLoadMonitorServiceImpl implements InternalSystemLoadMonitorService, SystemLoadProvider {

    private static final String SYSTEM_LOAD_CHECK_INTERVAL_KEY_NAME = "artifactory.system.load.check.interval.millis";
    private static final int DEFAULT_SYSTEM_LOAD_CHECK_INTERVAL_MILLIS = (int) TimeUnit.SECONDS.toMillis(15);
    private static final int MIN_SYSTEM_LOAD_CHECK_INTERVAL_MILLIS = (int) TimeUnit.SECONDS.toMillis(3);

    private final ConnectorsMonitor connectorsMonitor;
    private final TaskService taskService;
    private final Map<String, ConnectorMetric> connectorNameToMetrics = new ConcurrentHashMap<>();
    private final AtomicReference<SystemLoadState> systemLoadStateAtomicRef =
            new AtomicReference<>(SystemLoadState.HEALTHY);
    private Set<SystemLoadEventListener> listeners;

    @VisibleForTesting
    @Setter(AccessLevel.PACKAGE)
    private LongSupplier timeSupplier = System::currentTimeMillis;

    @VisibleForTesting
    @Setter(AccessLevel.PACKAGE)
    @Getter(AccessLevel.PACKAGE)
    private OutageSelfProbe outageSelfProbe;

    @Autowired
    public SystemLoadMonitorServiceImpl(ConnectorsMonitor connectorsMonitor, TaskService taskService,
            OutageSelfProbe outageSelfProbe) {
        this.connectorsMonitor = connectorsMonitor;
        this.taskService = taskService;
        this.outageSelfProbe = outageSelfProbe;
    }

    @PostConstruct
    public void init() {
        this.listeners = new CopyOnWriteArraySet<>(getSystemLoadEventListeners());
        initConnectorMetrics();
        scheduleSystemLoadMonitoring();
    }

    @Override
    public void monitorSystemLoad() {
        log.debug("System load monitor started");
        Set<ConnectorMetric> activeConnectorsMetrics = connectorsMonitor.getActiveConnectorsMetrics();
        log.debug("Found active connectors to monitor: {}", activeConnectorsMetrics);
        boolean changed = false;
        for (ConnectorMetric newMetric : activeConnectorsMetrics) {
            changed = updateMetric(newMetric) || changed;
        }

        boolean probeTimedOut = isProbeTimedOut();
        SystemLoadState previousState = getSystemLoadState();
        updateSystemLoadState(probeTimedOut);
        SystemLoadState currentState = getSystemLoadState();

        if (changed || previousState != currentState) {
            String reason = probeTimedOut ? "outage self-probe timed out" : "connector(s) load state changed";
            LogUtil.log(log, currentState.getLevel(),
                    "System load state changed ({}). Overall system load state: {}. Notifying listeners.",
                    reason, currentState);
            notifyListeners(currentState);
        }
    }

    @Override
    public SystemLoadState getSystemLoadState() {
        return systemLoadStateAtomicRef.get();
    }

    @Override
    public void registerListener(SystemLoadEventListener listener) {
        listeners.add(listener);
    }

    private void scheduleSystemLoadMonitoring() {
        int intervalMillis = getSystemLoadCheckIntervalMillis();
        log.info("Scheduling SystemLoadMonitorJob to run every {} milliseconds", intervalMillis);
        Task task = TaskUtils.createRepeatingTask(SystemLoadMonitorJob.class, intervalMillis, 0L);
        taskService.startTask(task, false);
        log.info("Scheduled SystemLoadMonitorJob");
    }

    private int getSystemLoadCheckIntervalMillis() {
        return Math.max(ArtifactoryHome.get().getSysConfig()
                        .getInt(SYSTEM_LOAD_CHECK_INTERVAL_KEY_NAME, DEFAULT_SYSTEM_LOAD_CHECK_INTERVAL_MILLIS),
                MIN_SYSTEM_LOAD_CHECK_INTERVAL_MILLIS);
    }

    /**
     * Updates the metric for the connector and checks if the state has changed.
     *
     * @param newMetric the new metric to update
     * @return true if the metric was updated and the state changed, false otherwise
     */
    private boolean updateMetric(ConnectorMetric newMetric) {
        String connectorName = newMetric.name();
        ConnectorMetric oldMetric = connectorNameToMetrics.put(connectorName, newMetric);
        ConnectorLoadState oldConnectorLoadState = oldMetric != null ? oldMetric.getConnectorLoadState() : null;
        ConnectorLoadState newConnectorLoadState = newMetric.getConnectorLoadState();
        log.debug("Checking connector: '{}' metrics. Old metric: '{}, new metric: '{}'", connectorName, oldMetric,
                newMetric);
        if (oldConnectorLoadState != newConnectorLoadState) {
            log.debug("Connector: '{}' state changed from: '{}' to: '{}'. {}", connectorName,
                    oldConnectorLoadState, newConnectorLoadState, newConnectorLoadState.getDescription());
            return true;
        }
        return false;
    }

    @VisibleForTesting
    void updateSystemLoadState() {
        updateSystemLoadState(isProbeTimedOut());
    }

    private void updateSystemLoadState(boolean probeTimedOut) {
        log.debug("Calculating overall system load state based on connector metrics");
        Collection<ConnectorMetric> activeConnectorsMetrics = connectorNameToMetrics.values();
        ConnectorLoadState overallState = HEALTHY;
        for (ConnectorMetric metric : activeConnectorsMetrics) {
            ConnectorLoadState connectorLoadState = metric.getConnectorLoadState();
            if (connectorLoadState.getWeight() > overallState.getWeight()) {
                overallState = connectorLoadState;
            }
        }

        if (probeTimedOut && overallState != OUTAGE) {
            log.debug("Outage self-probe timed out — escalating system load state from {} to OUTAGE", overallState);
            overallState = OUTAGE;
        }

        SystemLoadState systemLoadState = SystemLoadState.fromConnectorLoadState(overallState);
        systemLoadStateAtomicRef.set(systemLoadState);
    }

    private boolean isProbeTimedOut() {
        return outageSelfProbe.getLatestResult().timedOut();
    }

    private void notifyListeners(SystemLoadState newSystemLoadState) {
        for (SystemLoadEventListener listener : listeners) {
            try {
                log.debug("Notifying listener: {}", listener);
                listener.onSystemLoadEvent(
                        new SystemLoadEventData(newSystemLoadState, timeSupplier.getAsLong()));
            } catch (Exception e) {
                log.error("Failed during listener: '{}' system load event change notification", listener, e);
            }
        }
    }

    @VisibleForTesting
    void initConnectorMetrics() {
        Set<ConnectorMetric> allConnectorMetrics = connectorsMonitor.getActiveConnectorsMetrics();
        for (ConnectorMetric metric : allConnectorMetrics) {
            connectorNameToMetrics.put(metric.name(), metric);
            log.debug("Initialized connector metric: {}", metric);
        }
    }

    private static Collection<SystemLoadEventListener> getSystemLoadEventListeners() {
        return ContextHelper.get().beansForType(SystemLoadEventListener.class).values();
    }
}