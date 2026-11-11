package org.artifactory.throtlling.generic.rrl.service;

import org.artifactory.download.RateLimiterException;
import org.artifactory.throtlling.generic.rrl.model.IRateLimiterConfig;

import jakarta.annotation.Nonnull;
import java.util.Map;

/**
 * @author Omri Cohen
 */
public interface IRequestRateLimiterService {

    boolean isFeatureDisabled();

    /**
     * This method retrieves the configuration from the "configs" table.
     * If no config is present, will return the default values
     */
    IRateLimiterConfig getConfig();

    /**
     * This method updates the configuration in the "configs" table.
     */
    void updateConfig(@Nonnull IRateLimiterConfig rlConfig);

    /**
     * This method deletes the current configuration from the "configs" table.
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
     * Acquires a permit for a specific domain of request rate limiter.
     * If a permit cannot be acquired, it throws a RateLimiterException.
     *
     * @param domain The domain of the request that rateLimiter should acquirePermit.
     * @param isHeadRequest True if the request is a HEAD request, false otherwise.
     * @throws RateLimiterException If a permit cannot be acquired.
     */
    void acquirePermit(String domain, boolean isHeadRequest) throws RateLimiterException;

    /**
     * Retrieves the metrics related to the rate limiting.
     *
     * @return A map where the key is a String domainSuffix and the value is another map.
     * The inner map's key is a string representing a specific metric and the value is a long representing the
     * metrics value.
     */
    Map<String, Map<String, Long>> getMetrics();
}
