package org.artifactory.throttling.qrl.service;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.config.ConfigurationException;
import org.artifactory.lifecycle.storage.db.servers.event.ServersUpdatedEvent;
import org.artifactory.metrics.core.CoreMetricsRegistry;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.common.model.RateLimiterSettings;
import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;
import org.artifactory.throtlling.qrl.model.QueryRateLimiterMetrics;
import org.artifactory.throtlling.qrl.service.LowPriorityRunnable;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.artifactory.throttling.lh.service.LoadHealerService;
import org.artifactory.throttling.qrl.metrics.QueryRateLimiterMetricsJob;
import org.artifactory.throttling.qrl.type.ArtifactoryQueryRateLimiter;
import org.artifactory.throttling.qrl.type.EnabledQueryRateLimiter;
import org.artifactory.throttling.qrl.type.QueryRateLimiterFactory;
import org.artifactory.util.ThreadMarker;
import org.artifactory.webapp.servlet.RequestUtils;
import org.jfrog.storage.qrl.QueryRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.lang.System.currentTimeMillis;
import static java.lang.ThreadLocal.withInitial;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.artifactory.common.ConstantValues.qrlDefaultPermitsPerTimeFrame;
import static org.artifactory.common.ConstantValues.qrlDefaultTimeFrameMillis;
import static org.artifactory.common.ConstantValues.qrlDefaultTimeQuota;
import static org.artifactory.common.ConstantValues.qrlEnabled;
import static org.artifactory.common.ConstantValues.qrlLowPriorityAdditionalList;
import static org.artifactory.common.ConstantValues.qrlLowPriorityDefaultList;
import static org.artifactory.common.ConstantValues.qrlLowPriorityPermitsPerTimeFrame;
import static org.artifactory.common.ConstantValues.qrlLowPriorityTimeFrameMillis;
import static org.artifactory.common.ConstantValues.qrlLowPriorityTimeQuota;
import static org.artifactory.common.ConstantValues.qrlMetricsIntervalSecs;
import static org.artifactory.schedule.TaskUtils.createRepeatingTask;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.hasHeartbeat;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.isRunning;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_QUERY_RATE_LIMITER;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.DEFAULT;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.LOW_PRIORITY;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;
import static org.artifactory.throttling.lh.config.LoadHealerActivity.QRL_LOW_PRIORITY_REQUESTS;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@Service
public class QueryRateLimiterServiceImpl
        implements QueryRateLimiterService, QueryRateLimiter, ApplicationListener<ServersUpdatedEvent>,
        ContextCreationListener {

    @VisibleForTesting
    static final ThreadLocal<QueryRateLimiterType> QRL_TYPE_THREAD_LOCAL = withInitial(() -> DEFAULT);
    private final RateLimiterConfig qrlDefaultConfig = getQrlDefaultConfig();
    private final Pattern lowPriorityPattern = getLowPriorityPattern();
    private final ArtifactoryJdbcHelper jdbcHelper;
    private final AddonsManager addonsManager;
    private final ArtifactoryServersCommonService serversService;
    private final ConfigsService configsService;
    private final TaskService taskService;
    private final CoreMetricsRegistry coreMetricsRegistry;
    private final AtomicReference<List<ArtifactoryServer>> servers = new AtomicReference<>(emptyList());
    private final LoadHealerService loadHealerService;
    private volatile Map<QueryRateLimiterType, ArtifactoryQueryRateLimiter> rateLimiters;
    private Instant lastPrintTime = Instant.now();

    @Autowired
    public QueryRateLimiterServiceImpl(ArtifactoryJdbcHelper jdbcHelper, AddonsManager addonsManager,
            ArtifactoryServersCommonService serversService,
            ConfigsService configsService, TaskService taskService,
            CoreMetricsRegistry coreMetricsRegistry, LoadHealerService loadHealerService) {
        this.jdbcHelper = jdbcHelper;
        this.addonsManager = addonsManager;
        this.serversService = serversService;
        this.configsService = configsService;
        this.taskService = taskService;
        this.coreMetricsRegistry = coreMetricsRegistry;
        this.loadHealerService = loadHealerService;
        setQueryRateLimiter();
    }

    /**
     * This method should run only on startup.
     */
    private void setQueryRateLimiter() {
        if (isFeatureDisabled()) {
            log.info("Query rate limiter is disabled");
            return;
        }
        log.info("Query rate limiter is enabled");
        servers.set(getRunningServers(serversService.getAllArtifactoryServers()));
        recalculate();
        jdbcHelper.setQueryRateLimiter(this);
    }

    @Override
    public boolean isFeatureDisabled() {
        return !qrlEnabled.getBoolean();
    }

    /**
     * <pre>
     * This method wraps code with the Query Rate Limiter (QRL) functionality.
     * By default, the {@link QueryRateLimiterType#DEFAULT} rate limiter is applied,
     * use this method in order to override with a different type.
     * </pre>
     *
     * @param qrlType  Query rate limiter type to use for this wrapped code.
     * @param supplier The code to wrap.
     * @return Result of the supplier.
     */
    public static <T> T withQueryRateLimiter(@Nonnull QueryRateLimiterType qrlType, @Nonnull Supplier<T> supplier) {
        if (shouldBypassQRL(qrlType)) {
            return supplier.get();
        }
        QueryRateLimiterType qrlTypePrevious = QRL_TYPE_THREAD_LOCAL.get();
        try {
            log.trace("Setting query rate limiter type to: {}", qrlType);
            QRL_TYPE_THREAD_LOCAL.set(qrlType);
            return supplier.get();
        } finally {
            log.trace("Restoring query rate limiter type back to: {}", qrlTypePrevious);
            QRL_TYPE_THREAD_LOCAL.set(qrlTypePrevious);
        }
    }

    /**
     * <pre>
     * This method wraps code with the Query Rate Limiter (QRL) functionality.
     * By default, the {@link QueryRateLimiterType#DEFAULT} rate limiter is applied,
     * use this method in order to override with a different type.
     * </pre>
     *
     * @param qrlType  Query rate limiter type to use for this wrapped code.
     * @param runnable The code to wrap.
     */
    public static void withQueryRateLimiter(@Nonnull QueryRateLimiterType qrlType, @Nonnull Runnable runnable) {
        if (shouldBypassQRL(qrlType)) {
            runnable.run();
            return;
        }
        QueryRateLimiterType qrlTypePrevious = QRL_TYPE_THREAD_LOCAL.get();
        try {
            log.trace("Setting query rate limiter type to: {}", qrlType);
            QRL_TYPE_THREAD_LOCAL.set(qrlType);
            runnable.run();
        } finally {
            log.trace("Restoring query rate limiter type back to: {}", qrlTypePrevious);
            QRL_TYPE_THREAD_LOCAL.set(qrlTypePrevious);
        }
    }

    private static boolean shouldBypassQRL(@Nonnull QueryRateLimiterType qrlType) {
        return qrlType == LOW_PRIORITY && ThreadMarker.isMarkedBy(AVOID_QRL_LOW_PRIORITY);
    }

    @Nonnull
    @Override
    public RateLimiterConfig getConfig() {
        if (isFeatureDisabled()) {
            throw new ConfigurationException("Query rate limiter is disabled");
        }
        return Optional.ofNullable(getConfigFromDB()).orElse(qrlDefaultConfig);
    }

    @Nullable
    private RateLimiterConfig getConfigFromDB() {
        RateLimiterConfig qrlConfig = withQueryRateLimiter(SYSTEM,
                () -> configsService.getConfig(ARTIFACTORY_QUERY_RATE_LIMITER, RateLimiterConfig.class));
        log.debug("Getting config from DB: {}", qrlConfig);
        if (qrlConfig != null) {
            updateUndefinedFieldsIfNeeded(qrlConfig);
        }
        return qrlConfig;
    }

    private void updateUndefinedFieldsIfNeeded(final RateLimiterConfig config) {
        log.debug("Updating undefined fields of QRL config: {}", config);
        boolean hasChanges = false;
        for (final RateLimiterSettings settings : config.rlSettings()) {
            if (settings.getTimeQuota() == null) {
                settings.setTimeQuota(settings.getRlType() == DEFAULT ? qrlDefaultTimeQuota.getInt() :
                        qrlLowPriorityTimeQuota.getInt());
                hasChanges = true;
            }
            if (settings.getTimeFrameMillis() == null) {
                settings.setTimeFrameMillis(settings.getRlType() == DEFAULT ? qrlDefaultTimeFrameMillis.getInt() :
                        qrlLowPriorityTimeFrameMillis.getInt());
                hasChanges = true;
            }
            if (settings.getPermitsPerTimeFrame() == null) {
                settings.setPermitsPerTimeFrame(
                        settings.getRlType() == DEFAULT ? qrlDefaultPermitsPerTimeFrame.getInt() :
                                qrlLowPriorityPermitsPerTimeFrame.getInt());
                hasChanges = true;
            }
        }
        if (hasChanges) {
            log.debug("QRL config changed: {}, updating config in DB", config);
            withQueryRateLimiter(SYSTEM,
                    () -> configsService.addOrUpdateConfig(ARTIFACTORY_QUERY_RATE_LIMITER, config,
                            currentTimeMillis()));
        }
    }

    @Override
    public void updateConfig(@Nonnull RateLimiterConfig qrlConfig) {
        if (isFeatureDisabled()) {
            throw new ConfigurationException("Query rate limiter is disabled");
        }
        updateConfigToDB(qrlConfig);
        propagateRecalculation();
        recalculate();
    }

    private void updateConfigToDB(@Nonnull RateLimiterConfig qrlConfig) {
        var qrlSettings = qrlConfig.rlSettings().stream()
                .map(RateLimiterSettings::getRlType)
                .collect(toSet());
        var configurableRateLimiters = Arrays.stream(QueryRateLimiterType.values())
                .filter(qrlType -> qrlType != SYSTEM)
                .collect(toSet());
        configurableRateLimiters.removeAll(qrlSettings);
        if (!configurableRateLimiters.isEmpty()) {
            log.debug("Could not find settings for types: {}", configurableRateLimiters);
            qrlConfig.rlSettings().addAll(getConfig().rlSettings());
        }
        final RateLimiterConfig mergedConfig = mergeNewConfigToExisting(qrlConfig);
        log.debug("Updating config to DB: {}", qrlConfig);
        withQueryRateLimiter(SYSTEM,
                () -> configsService.addOrUpdateConfig(ARTIFACTORY_QUERY_RATE_LIMITER, mergedConfig,
                        currentTimeMillis()));
    }

    /**
     * Merging new QRL configuration with existing to allow partial update.
     *
     * @param newConfig a new qrl configuration
     * @return configuration merge result.
     */
    private RateLimiterConfig mergeNewConfigToExisting(final RateLimiterConfig newConfig) {
        final RateLimiterConfig existing = getConfig();
        for (final RateLimiterSettings stgs : newConfig.rlSettings()) {
            existing.rlSettings().stream().filter(it -> it.getRlType() == stgs.getRlType()).findFirst().ifPresentOrElse(
                    cfg -> {
                        if (stgs.getPermitsPerTimeFrame() != null) {
                            cfg.setPermitsPerTimeFrame(stgs.getPermitsPerTimeFrame());
                        }
                        if (stgs.getTimeFrameMillis() != null) {
                            cfg.setTimeFrameMillis(stgs.getTimeFrameMillis());
                        }
                        if (stgs.getTimeQuota() != null) {
                            cfg.setTimeQuota(stgs.getTimeQuota());
                        } else {
                            cfg.setTimeQuota(cfg.getRlType() == DEFAULT ? qrlDefaultTimeQuota.getInt() :
                                    qrlLowPriorityTimeQuota.getInt());
                        }
                    }, () -> existing.rlSettings().add(stgs));
        }
        return existing;
    }

    @Override
    public void deleteConfig() {
        if (isFeatureDisabled()) {
            throw new ConfigurationException("Query rate limiter is disabled");
        }
        deleteConfigFromDB();
        propagateRecalculation();
        recalculate();
    }

    private void deleteConfigFromDB() {
        log.debug("Deleting config from DB");
        withQueryRateLimiter(SYSTEM, () -> configsService.deleteConfig(ARTIFACTORY_QUERY_RATE_LIMITER.getKeyName()));
    }

    private void propagateRecalculation() {
        log.info("Propagating query rate limiter recalculation");
        addonsManager.addonByType(HaCommonAddon.class).propagateQueryRateLimiterRecalculation();
    }

    @Override
    public void recalculate() {
        if (isFeatureDisabled()) {
            return;
        }
        try {
            log.info("Recalculating query rate limiter");
            rateLimiters = getRateLimiters();
            log.debug("Rate limiters: {}", rateLimiters);
        } catch (Exception e) {
            log.error("Recalculation failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public void acquirePermit() {
        rateLimiters.get(QRL_TYPE_THREAD_LOCAL.get()).acquirePermit();
    }

    @Override
    public void updateExecutionTime(int queryTime) {
        rateLimiters.get(QRL_TYPE_THREAD_LOCAL.get()).updateExecutionTime(queryTime);
    }

    @Override
    public void onApplicationEvent(ServersUpdatedEvent event) {
        if (isFeatureDisabled()) {
            return;
        }
        List<ArtifactoryServer> updatedServers = getRunningServers(event.getUpdatedServers());
        List<ArtifactoryServer> oldServers = servers.getAndSet(updatedServers);
        if (oldServers.size() != updatedServers.size()) {
            log.debug("Running servers count has changed from {} to {}", oldServers.size(), updatedServers.size());
            recalculate();
        }
    }

    @Override
    public void onContextCreated() {
        if (isFeatureDisabled()) {
            return;
        }
        long interval = qrlMetricsIntervalSecs.getLong();
        Task qrlMetricsTask = createRepeatingTask(QueryRateLimiterMetricsJob.class, SECONDS.toMillis(interval), 0);
        log.debug("Scheduling QueryRateLimiterMetricsJob to run every {} seconds", interval);
        taskService.startTask(qrlMetricsTask, false);
        log.debug("Scheduled QueryRateLimiterMetricsJob to run every {} seconds", interval);
    }

    @Nonnull
    private Map<QueryRateLimiterType, ArtifactoryQueryRateLimiter> getRateLimiters() {
        RateLimiterConfig qrlConfig = Optional.ofNullable(getConfigFromDB()).orElse(qrlDefaultConfig);
        var rateLimitersMap = Arrays.stream(QueryRateLimiterType.values())
                .collect(toMap(Function.identity(), qrlType ->
                        QueryRateLimiterFactory.create(qrlType, qrlConfig, getRunningServers(servers.get()))));
        return new EnumMap<>(rateLimitersMap);
    }

    @Nonnull
    private List<ArtifactoryServer> getRunningServers(@Nonnull List<ArtifactoryServer> servers) {
        return servers.stream().filter(isRunning.and(hasHeartbeat)).toList();
    }

    @Nonnull
    private RateLimiterConfig getQrlDefaultConfig() {
        RateLimiterSettings defaultSettings = new RateLimiterSettings(DEFAULT,
                qrlDefaultPermitsPerTimeFrame.getInt(), qrlDefaultTimeFrameMillis.getInt(),
                qrlDefaultTimeQuota.getInt());
        RateLimiterSettings lowPrioritySettings = new RateLimiterSettings(LOW_PRIORITY,
                qrlLowPriorityPermitsPerTimeFrame.getInt(), qrlLowPriorityTimeFrameMillis.getInt(),
                qrlLowPriorityTimeQuota.getInt());
        return new RateLimiterConfig(Set.of(defaultSettings, lowPrioritySettings));
    }

    @Override
    public void collectMetrics() {
        Instant now = Instant.now();
        long duration = Duration.between(lastPrintTime, now).toMillis();
        var currentMetrics = getMetricsMap();
        currentMetrics.forEach((qrlType, qrlMetrics) -> {
            int slowedDownPercentage = calculatePercentage(qrlMetrics.getTotalSlowedDownMillis(), duration);
            int slowedDownTimePercentage =
                    calculatePercentage(qrlMetrics.getTotalSlowedDownByTimeQuotaMillis(), duration);
            qrlMetrics.setTotalSlowedDownPercentage(slowedDownPercentage);
            qrlMetrics.setTotalSlowedDownByTimePercentage(slowedDownTimePercentage);
            EnabledQueryRateLimiter enabledQrl = (EnabledQueryRateLimiter) rateLimiters.get(qrlType);
            if (slowedDownPercentage > 0) {
                log.info(qrlType.getMessage(),
                        enabledQrl.getPermitsPerTimeFrame(), enabledQrl.getTimeFrameMillis(), slowedDownPercentage);
            }
            double factor =
                    (double) SECONDS.toMillis(qrlMetricsIntervalSecs.getInt()) / enabledQrl.getTimeFrameMillis();
            long permitsPerInterval = (long) (enabledQrl.getPermitsPerTimeFrame() * factor);
            qrlMetrics.setTotalPermits(permitsPerInterval);
        });
        coreMetricsRegistry.setQueryRateLimiterMetrics(currentMetrics);
        lastPrintTime = now;
    }

    private int calculatePercentage(long totalValue, long duration) {
        return Math.min(100, (int) (100 * totalValue / Math.max(1, duration)));
    }

    private Map<QueryRateLimiterType, QueryRateLimiterMetrics> getMetricsMap() {
        return rateLimiters.entrySet().stream()
                .filter(entry -> entry.getKey() != SYSTEM)
                .collect(toMap(Map.Entry::getKey, entry -> {
                    EnabledQueryRateLimiter enabledQrl = (EnabledQueryRateLimiter) entry.getValue();
                    return enabledQrl.getMetrics();
                }));
    }

    @Nullable
    private Pattern getLowPriorityPattern() {
        try {
            String defaultList = qrlLowPriorityDefaultList.getString();
            if (defaultList.isBlank()) {
                return null;
            }
            String additionalList = qrlLowPriorityAdditionalList.getString();
            return Pattern.compile(defaultList + (additionalList.isBlank() ? "" : "|" + additionalList));
        } catch (PatternSyntaxException e) {
            log.error("Query rate limiter low priority regex compilation failed: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void wrapRequestWithLowPriority(@Nonnull HttpServletRequest request, @Nonnull LowPriorityRunnable runnable)
            throws ServletException, IOException {
        if (isMatchesLowPriority(request)) {
            loadHealerService.executeWithActivity(QRL_LOW_PRIORITY_REQUESTS, null,
                    () -> handleLowPriorityRequest(runnable));
        } else {
            runnable.run();
        }
    }

    private void handleLowPriorityRequest(@Nonnull LowPriorityRunnable runnable) throws ServletException, IOException {
        if (ThreadMarker.isMarkedBy(AVOID_QRL_LOW_PRIORITY)) {
            runnable.run();
        }
        QueryRateLimiterType qrlTypePrevious = QRL_TYPE_THREAD_LOCAL.get();
        try {
            log.trace("Setting query rate limiter type to: LOW_PRIORITY");
            QRL_TYPE_THREAD_LOCAL.set(LOW_PRIORITY);
            runnable.run();
        } finally {
            log.trace("Restoring query rate limiter type back to: {}", qrlTypePrevious);
            QRL_TYPE_THREAD_LOCAL.set(qrlTypePrevious);
        }
    }

    private boolean isMatchesLowPriority(@Nonnull HttpServletRequest request) {
        if (isFeatureDisabled() || lowPriorityPattern == null) {
            return false;
        }
        String path = RequestUtils.getServletPathFromRequest(request);
        return lowPriorityPattern.matcher(path).matches();
    }
}
