package org.artifactory.throtlling.common.model;

import java.util.Set;

/**
 * @author Andrey Tashlitsky
 */
public record RateLimiterConfig(Set<RateLimiterSettings> rlSettings) {

}
