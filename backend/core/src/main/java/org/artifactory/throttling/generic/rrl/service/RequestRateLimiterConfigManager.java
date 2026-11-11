package org.artifactory.throttling.generic.rrl.service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.config.ConfigurationException;
import org.artifactory.storage.fs.service.ConfigsService;
import org.artifactory.throtlling.generic.rrl.model.IRateLimiterConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimitConfig;
import org.artifactory.throtlling.generic.rrl.model.RequestRateLimiterSettings;
import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;

import java.util.Optional;

import static java.lang.System.currentTimeMillis;
import static org.artifactory.storage.fs.service.ConfigsServiceKey.ARTIFACTORY_REQUEST_RATE_LIMITER_V2;
import static org.artifactory.throttling.common.Utils.calculatePermitsPerSecond;
import static org.artifactory.throttling.qrl.service.QueryRateLimiterServiceImpl.withQueryRateLimiter;

/**
 * @author Igor Gumush
 * @author Omri Cohen
 */
@Slf4j
public class RequestRateLimiterConfigManager {

    private final IRateLimiterConfig rateLimitersDefaultConfig;
    private final ConfigsService configsService;
    private final AddonsManager addonsManager;

    protected RequestRateLimiterConfigManager(ConfigsService configsService, AddonsManager addonsManager,
            IRateLimiterConfig defaultConfig) {
        this.configsService = configsService;
        this.addonsManager = addonsManager;
        this.rateLimitersDefaultConfig = defaultConfig;

    }

    @Nonnull
    public IRateLimiterConfig getConfig() {
        return Optional.ofNullable(getConfigFromDB()).orElse(rateLimitersDefaultConfig);
    }

    @Nullable
    private IRateLimiterConfig getConfigFromDB() {
        IRateLimiterConfig rlConfig = withQueryRateLimiter(QueryRateLimiterType.SYSTEM,
                () -> configsService.getConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2, RequestRateLimitConfig.class));
        log.debug("Getting config from DB: {}", rlConfig);
        return rlConfig;
    }

    public void updateConfig(@Nonnull IRateLimiterConfig rlConfig) {
        updateConfigToDB(rlConfig);
        propagateRecalculation();
    }

    private void updateConfigToDB(@Nonnull IRateLimiterConfig rlConfig) {
        if (rlConfig.rlSettings().isEmpty()) {
            throw new ConfigurationException("Settings can not be empty");
        }

        handleIllegalDomainSuffix(rlConfig);
        handleIllegalPermits(rlConfig);

        log.info("Updating config to DB: {}", rlConfig);
        withQueryRateLimiter(QueryRateLimiterType.SYSTEM, () -> configsService.addOrUpdateConfig(
                ARTIFACTORY_REQUEST_RATE_LIMITER_V2, rlConfig, currentTimeMillis()));
    }

    private static void handleIllegalPermits(@Nonnull IRateLimiterConfig rlConfig) {
        RequestRateLimiterSettings illegalPermits = rlConfig.rlSettings().stream()
                .filter(settings -> {
                    int permitsPerSecond =
                            calculatePermitsPerSecond(settings.getPermitsPerTimeFrame(), settings.getTimeFrameMillis());
                    boolean isNonValidTimeFrame = 0 >= permitsPerSecond;
                    log.debug("{} requests rate limiter: Converting {}/{} to {} permits per second",
                            settings.getDomainSuffix(), settings.getPermitsPerTimeFrame(),
                            settings.getTimeFrameMillis(), permitsPerSecond);
                    return isNonValidTimeFrame;
                })
                .findFirst()
                .orElse(null);
        if (illegalPermits != null) {
            throw new ConfigurationException(
                    "Illegal permitsPerTimeFrame/timeFrameMillis values for: " + illegalPermits);
        }
    }

    private static void handleIllegalDomainSuffix(@Nonnull IRateLimiterConfig rlConfig) {
        RequestRateLimiterSettings illegalDomainSuffix = rlConfig.rlSettings().stream()
                .filter(settings -> settings.getDomainSuffix().isBlank())
                .findFirst()
                .orElse(null);
        if (illegalDomainSuffix != null) {
            throw new ConfigurationException("domainSuffix can not be empty for: " + illegalDomainSuffix.getName());
        }
    }

    public void deleteConfig() {
        deleteConfigFromDB();
        propagateRecalculation();
    }

    private void deleteConfigFromDB() {
        log.info("Deleting config from DB");
        configsService.deleteConfig(ARTIFACTORY_REQUEST_RATE_LIMITER_V2.getKeyName());
    }

    private void propagateRecalculation() {
        log.info("Propagating rate limiter recalculation");
        addonsManager.addonByType(HaCommonAddon.class).propagateRequestRateLimiterRecalculation();
    }
}
