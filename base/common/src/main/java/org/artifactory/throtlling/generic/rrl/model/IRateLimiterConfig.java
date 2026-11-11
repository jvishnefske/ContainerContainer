package org.artifactory.throtlling.generic.rrl.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Set;

/**
 * @author Omri Cohen
 */
@JsonDeserialize(as = RequestRateLimitConfig.class)
public interface IRateLimiterConfig {
    Set<RequestRateLimiterSettings> rlSettings();
}
