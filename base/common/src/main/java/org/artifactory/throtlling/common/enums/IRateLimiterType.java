package org.artifactory.throtlling.common.enums;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * @author Igor Gumush
 */
@JsonDeserialize(using = RateLimiterTypeDeserializer.class)
public interface IRateLimiterType {
}
