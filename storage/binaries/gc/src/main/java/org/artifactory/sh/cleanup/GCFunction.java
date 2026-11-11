package org.artifactory.sh.cleanup;

import org.artifactory.storage.GCCandidate;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;

/**
 * A function that performs some action on a {@link GCCandidate} and returns true only when the candidate was actually
 * GC'ed.
 *
 * @author Uriah Levy
 */
@FunctionalInterface
public interface GCFunction {

    /**
     * @return true if the binary candidate were deleted, false otherwise
     */
    boolean accept(GCCandidate candidate, GarbageCollectorInfo result);
}