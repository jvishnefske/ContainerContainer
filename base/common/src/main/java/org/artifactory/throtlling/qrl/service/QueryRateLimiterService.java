package org.artifactory.throtlling.qrl.service;

import org.artifactory.common.ConstantValues;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.common.service.IRateLimiterConfigService;

import jakarta.annotation.Nonnull;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * @author Andrey Tashlitsky
 */
public interface QueryRateLimiterService extends IRateLimiterConfigService {

    String AVOID_QRL_LOW_PRIORITY = "avoid-query-rate-limiter-low-priority";

    boolean isFeatureDisabled();

    /**
     * This method retrieves the configuration from the "configs" table.
     * If no config is present, will return the default values in
     * {@link ConstantValues.qrlPermitsPerTimeFrame} and {@link ConstantValues.qrlTimeFrameMillis)}.
     */
    RateLimiterConfig getConfig();

    /**
     * This method updates the configuration to the "configs" table.
     */
    void updateConfig(@Nonnull RateLimiterConfig qrlConfig);

    /**
     * This method deletes the configuration from the "configs" table.
     */
    void deleteConfig();

    /**
     * <pre>
     * Trigger rate limiter settings recalculation.
     * Example for cases which this is needed:
     * 1) Configuration has changed via REST API.
     * 2) Running servers count has changed.
     * </pre>
     */
    void recalculate();

    /**
     * Collects the metrics from all active rate limiters and stores it.
     */
    void collectMetrics();

    /**
     * Used to wrap requests inside RequestFilter.
     */
    void wrapRequestWithLowPriority(@Nonnull HttpServletRequest request, @Nonnull LowPriorityRunnable runnable)
            throws ServletException, IOException;
}
