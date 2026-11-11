package org.artifactory.throttling.generic.rrl.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.config.ConfigurationException;
import org.artifactory.download.RateLimiterException;
import org.artifactory.lifecycle.storage.db.servers.event.ServersUpdatedEvent;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.throtlling.generic.rrl.model.IRateLimiterConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimitConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimiterSettings;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.artifactory.throttling.rrl.strategy.GenericRequestRateLimiter;
import org.artifactory.throttling.rrl.strategy.InternalRequestRateLimiter;

import static org.artifactory.throttling.common.Utils.MONITORING_UNLIMITED_PERMITS;
import static org.artifactory.throttling.common.Utils.MONITORING_TIMEFRAME_MILLIS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.artifactory.common.ConstantValues.rrlGenericEnabled;
import static org.artifactory.common.ConstantValues.rrlDockerPermitsDefaultConfig;
import static org.artifactory.common.ConstantValues.rrlDockerTimeFrameDefaultConfig;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.hasHeartbeat;
import static org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService.isRunning;

/**
 * @author Omri Cohen
 * @author Igor Gumush
 */
@Slf4j
@Service
public class RequestRateLimiterServiceV2Impl
        implements IRequestRateLimiterService, ApplicationListener<ServersUpdatedEvent> {

    private final ArtifactoryServersCommonService serversService;
    private final AtomicReference<List<ArtifactoryServer>> servers = new AtomicReference<>(emptyList());
    private ConcurrentHashMap<String, InternalRequestRateLimiter> rateLimiters;
    private final RequestRateLimiterConfigManager configManager;

    @Autowired
    public RequestRateLimiterServiceV2Impl(AddonsManager addonsManager,
            ArtifactoryServersCommonService serversService,
            ConfigsService configsService) {

        this.serversService = serversService;
        this.configManager = new RequestRateLimiterConfigManager(configsService, addonsManager, getRlDefaultConfig());
        setRateLimiter();
    }

    /**
     * This method should run only on startup.
     */
    private void setRateLimiter() {
        if (isFeatureDisabled()) {
            log.info("OutboundRequest rate limiter is disabled");
            return;
        }
        log.info("OutboundRequest rate limiter is enabled");
        servers.set(getRunningServers(serversService.getAllArtifactoryServers()));
        recalculate();
    }

    @Nonnull
    private List<ArtifactoryServer> getRunningServers(List<ArtifactoryServer> servers) {
        return servers.stream().filter(isRunning.and(hasHeartbeat)).toList();
    }

    @Nonnull
    private static IRateLimiterConfig getRlDefaultConfig() {
        RequestRateLimiterSettings dockerHubSettings = new RequestRateLimiterSettings(
                "DockerHub",
                ".docker.io",
                rrlDockerPermitsDefaultConfig.getInt(),
                rrlDockerTimeFrameDefaultConfig.getInt(),
                true
        );
        RequestRateLimiterSettings apacheMavenSettings = new RequestRateLimiterSettings(
                "ApacheMaven",
                "repository.apache.org",
                MONITORING_UNLIMITED_PERMITS,
                MONITORING_TIMEFRAME_MILLIS,
                true
        );
        RequestRateLimiterSettings apacheDownloadsSettings = new RequestRateLimiterSettings(
                "ApacheDownloads",
                "downloads.apache.org",
                MONITORING_UNLIMITED_PERMITS,
                MONITORING_TIMEFRAME_MILLIS,
                true
        );
        RequestRateLimiterSettings dhiSettings = new RequestRateLimiterSettings(
                "DHI",
                "dhi.io",
                MONITORING_UNLIMITED_PERMITS,
                MONITORING_TIMEFRAME_MILLIS,
                true
        );
        RequestRateLimiterSettings npmSettings = new RequestRateLimiterSettings(
                "npm",
                "registry.npmjs.com",
                MONITORING_UNLIMITED_PERMITS,
                MONITORING_TIMEFRAME_MILLIS,
                true
        );
        RequestRateLimiterSettings golangSettings = new RequestRateLimiterSettings(
                "Golang",
                "proxy.golang.org",
                MONITORING_UNLIMITED_PERMITS,
                MONITORING_TIMEFRAME_MILLIS,
                true
        );
        RequestRateLimiterSettings huggingFaceSettings = new RequestRateLimiterSettings(
                "HuggingFace",
                "huggingface.co",
                MONITORING_UNLIMITED_PERMITS,
                MONITORING_TIMEFRAME_MILLIS,
                true
        );
        return new RequestRateLimitConfig(Set.of(
                dockerHubSettings,
                apacheMavenSettings,
                apacheDownloadsSettings,
                dhiSettings,
                npmSettings,
                golangSettings,
                huggingFaceSettings
        ));
    }

    @Override
    public boolean isFeatureDisabled() {
        return !rrlGenericEnabled.getBoolean();
    }

    @Override
    public IRateLimiterConfig getConfig() {
        assertFeatureEnabled();
        return configManager.getConfig();
    }

    @Override
    public void updateConfig(@Nonnull IRateLimiterConfig rlConfig) {
        assertFeatureEnabled();
        configManager.updateConfig(rlConfig);
        recalculate();
    }

    @Override
    public void deleteConfig() {
        assertFeatureEnabled();
        configManager.deleteConfig();
        recalculate();
    }

    @Override
    public void recalculate() {
        if (isFeatureDisabled()) {
            return;
        }
        log.info("Recalculating OutboundRequest rate limiter");
        rateLimiters = createRateLimitersFromConf();
        log.debug("Rate limiters: {}", rateLimiters);
    }

    @Override
    public void acquirePermit(String domain, boolean isHeadRequest) throws RateLimiterException {
        if (isFeatureDisabled()) {
            return;
        }
        String domainSuffix = rateLimiters.keySet().stream()
                .filter(rateLimiterSuffixDomain -> domain.endsWith(rateLimiterSuffixDomain) ||
                        domain.endsWith(rateLimiterSuffixDomain + "/"))
                .findFirst()
                .orElse(null);
        if (domainSuffix == null) {
            return;
        }

        InternalRequestRateLimiter rateLimiter = rateLimiters.get(domainSuffix);

        if (!rateLimiter.shouldCountHeadRequest() && isHeadRequest) {
            return;
        }

        rateLimiter.acquirePermit();
    }

    @Override
    public Map<String, Map<String, Long>> getMetrics() {
        return rateLimiters.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getMetrics()
                ));
    }

    private void assertFeatureEnabled() {
        if (isFeatureDisabled()) {
            throw new ConfigurationException("OutboundRequest rate limiter is disabled");
        }
    }

    @Nonnull
    private ConcurrentHashMap<String, InternalRequestRateLimiter> createRateLimitersFromConf() {
        IRateLimiterConfig rrlConfig = configManager.getConfig();
        List<ArtifactoryServer> runningServers = getRunningServers(servers.get());
        Map<String, InternalRequestRateLimiter> rateLimitersMap = rrlConfig.rlSettings().stream()
                .collect(Collectors.toMap(
                        RequestRateLimiterSettings::getDomainSuffix,
                        settings -> this.rateLimiterBuilder(settings, runningServers.size())
                ));

        return new ConcurrentHashMap<>(rateLimitersMap);
    }

    @Nonnull
    private InternalRequestRateLimiter rateLimiterBuilder(@Nonnull RequestRateLimiterSettings settings,
            int numRunningServers) {
        int permitsPerServer = getPermitsPerServer(settings.getPermitsPerTimeFrame(), numRunningServers);
        return GenericRequestRateLimiter.create(settings.getName(), settings.getDomainSuffix(),
                permitsPerServer, settings.getTimeFrameMillis(), settings.isIncludeHead());
    }

    /**
     * Calculate permits per server for HA support.
     * Divides the total permits by the number of running servers.
     */
    private int getPermitsPerServer(int totalPermits, int numRunningServers) {
        if (totalPermits >= MONITORING_UNLIMITED_PERMITS) {
            return MONITORING_UNLIMITED_PERMITS;
        }
        int permitsPerServer = totalPermits / Math.max(1, numRunningServers);
        log.debug("Calculating permits per server: {} total permits / {} servers = {} permits per server",
                totalPermits, numRunningServers, permitsPerServer);
        return permitsPerServer;
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
}
