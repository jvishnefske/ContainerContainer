package org.artifactory.sh.cleanup;

import org.artifactory.storage.GCCandidate;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.jfrog.storage.binstore.ifc.model.BinaryElement;

import java.util.List;

/**
 * @author TamirH
 */
public interface GcBinariesCleaner {

    void deleteBlobInfos(List<String> deletedBinaries);

    boolean executeBinaryCleaner(GCCandidate candidate, GarbageCollectorInfo result);

    boolean executeBinaryCleaner(BinaryElement element, GarbageCollectorInfo result);
}
