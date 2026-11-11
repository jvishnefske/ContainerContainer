package org.artifactory.api.web;

/**
 * Provides feature flag services for UI components.
 *
 * @author Dvir Twina
 */
public interface FeatureFlagService {

    String getAllFeatureFlags();
    String featureFlagsHash();
}
