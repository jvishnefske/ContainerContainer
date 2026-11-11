package org.artifactory.sh.cleanup;

import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;

/**
 * @author TamirH
 */
public interface GCService extends GcBinariesCleaner {

    GarbageCollectorInfo startGarbageCollect();

    GarbageCollectorInfo forceFull();

    void runShardingBalancerTask();
}
