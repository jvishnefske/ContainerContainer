package org.artifactory.throtlling.common.service;

import org.artifactory.throtlling.common.model.RateLimiterConfig;

import jakarta.annotation.Nonnull;

/**
 * @author Andrey Tashlitsky
 */
public interface IRateLimiterConfigService {

    boolean isFeatureDisabled();

    /**
     * This method retrieves the configuration from the "configs" table.
     * If no config is present, will return the default values
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
}
