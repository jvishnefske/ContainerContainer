package org.artifactory.support.config.federation;

import org.jfrog.support.common.CollectConfiguration;

/**
 * Federation snapshot collection configuration
 */
public class FederationSnapshotConfiguration extends CollectConfiguration {

    /**
     * serialization .ctr
     */
    public FederationSnapshotConfiguration() {
    }

    public FederationSnapshotConfiguration(boolean enabled) {
        super(enabled);
    }
}
