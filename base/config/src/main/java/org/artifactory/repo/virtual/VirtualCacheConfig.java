package org.artifactory.repo.virtual;

import lombok.Data;
import org.jfrog.common.config.diff.GenerateDiffFunction;

import java.util.Objects;

/**
 * @author Dan Feldman
 */
@Data
@GenerateDiffFunction
public class VirtualCacheConfig {
    private long virtualRetrievalCachePeriodSecs = 600;

    public VirtualCacheConfig() {
    }

    public VirtualCacheConfig(VirtualCacheConfig virtualCacheConfig) {
        this.virtualRetrievalCachePeriodSecs = virtualCacheConfig.getVirtualRetrievalCachePeriodSecs();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VirtualCacheConfig)) {
            return false;
        }
        VirtualCacheConfig that = (VirtualCacheConfig) o;
        return getVirtualRetrievalCachePeriodSecs() == that.getVirtualRetrievalCachePeriodSecs();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getVirtualRetrievalCachePeriodSecs());
    }
}
