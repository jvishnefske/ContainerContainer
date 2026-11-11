package org.artifactory.throtlling.generic.rrl.model;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author Omri Cohen
 */
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
public class RequestRateLimiterSettings {
    private String name;
    @EqualsAndHashCode.Include
    @Nonnull
    private String domainSuffix;
    private int permitsPerTimeFrame;
    private int timeFrameMillis;
    private boolean includeHead;
}
