package org.artifactory.throttling.qrl.type;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.config.ConfigurationException;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.common.model.RateLimiterSettings;
import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;

import jakarta.annotation.Nonnull;
import java.util.List;

import static org.artifactory.common.ConstantValues.qrlSimulationModeEnabled;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.SYSTEM;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@UtilityClass
public class QueryRateLimiterFactory {

    @Nonnull
    public ArtifactoryQueryRateLimiter create(@Nonnull QueryRateLimiterType qrlType,
            @Nonnull RateLimiterConfig qrlConfig, @Nonnull List<ArtifactoryServer> servers) {
        if (qrlType == SYSTEM) {
            return new DisabledQueryRateLimiter();
        }
        RateLimiterSettings qrlSettings = qrlConfig.rlSettings().stream()
                .filter(e -> e.getRlType() == qrlType)
                .findFirst()
                .orElseThrow(() -> new ConfigurationException("Could not find settings for type: " + qrlType));
        if (qrlSimulationModeEnabled.getBoolean()) {
            return new SimulationQueryRateLimiter(qrlType,
                    getPermitsPerServer(qrlType, qrlSettings.getPermitsPerTimeFrame(), servers),
                    qrlSettings.getTimeFrameMillis(),
                    getPermitsPerServer(qrlType, qrlSettings.getTimeQuota(), servers));
        }
        return new EnabledQueryRateLimiter(qrlType,
                getPermitsPerServer(qrlType, qrlSettings.getPermitsPerTimeFrame(), servers),
                qrlSettings.getTimeFrameMillis(),
                getPermitsPerServer(qrlType, qrlSettings.getTimeQuota(), servers)
        );
    }

    private int getPermitsPerServer(@Nonnull QueryRateLimiterType qrlType, int permits,
            @Nonnull List<ArtifactoryServer> servers) {
        int permitsPerServer = permits / Math.max(1, servers.size());
        log.debug("{} query rate limiter: Permits per server: {}", qrlType, permitsPerServer);
        return permitsPerServer;
    }
}
