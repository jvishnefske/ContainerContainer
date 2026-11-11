package org.artifactory.ph.selector;

import jakarta.annotation.Nullable;

/**
 * Interface in order to execute packages indexing operations from oss (for example in converters).
 * We need to use this interface as part of the gradual rollout of packages from pi to ph
 */
public interface IndexingSelector {
    /**
     * Trigger async indexing operation on a specific path.
     *
     * @param repoKey - target repo key
     * @param path    - path to package. in case of empty the entire repo will be calculated
     */
    void reindex(String repoKey, @Nullable String path) throws Exception;
}
