package org.artifactory.threadlocal.cache;

import lombok.NonNull;

/**
 * Activates a request-scoped virtual repository config cache overlay for the duration of a virtual resolve.
 */
public interface VirtualResolveScopeRunner {

    <T, Y extends Exception> T run(@NonNull String virtualRepoKey, @NonNull SupplierWithError<T, Y> supplier)
            throws Y;
}
