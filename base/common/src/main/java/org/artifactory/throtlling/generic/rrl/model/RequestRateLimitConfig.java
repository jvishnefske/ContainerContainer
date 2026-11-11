package org.artifactory.throtlling.generic.rrl.model;

import java.util.Set;

/**
 * @author Omri Cohen
 */
public record RequestRateLimitConfig(Set<RequestRateLimiterSettings> rlSettings) implements IRateLimiterConfig {
}
