package org.artifactory.throtlling.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.artifactory.throtlling.common.enums.IRateLimiterType;

import java.util.Objects;

/**
 * @author Andrey Tashlitsky
 */
@Data
@AllArgsConstructor
public class RateLimiterSettings {

    private IRateLimiterType rlType;
    private Integer permitsPerTimeFrame;
    private Integer timeFrameMillis;
    private Integer timeQuota;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RateLimiterSettings that = (RateLimiterSettings) o;
        return rlType == that.rlType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rlType);
    }
}
