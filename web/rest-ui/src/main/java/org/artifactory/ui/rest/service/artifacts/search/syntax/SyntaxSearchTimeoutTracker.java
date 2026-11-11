package org.artifactory.ui.rest.service.artifacts.search.syntax;

import lombok.experimental.UtilityClass;

/**
 * @author Amir Avidan
 */
@UtilityClass
class SyntaxSearchTimeoutTracker {

    private static final ThreadLocal<Boolean> hasTimeout = ThreadLocal.withInitial(() -> false);

    /**
     * Resets the timeout flag for the current thread.
     * Should be called at the start of each search operation.
     */
    static void reset() {
        hasTimeout.set(false);
    }

    /**
     * Checks if any timeout occurred during the search and cleans up the ThreadLocal.
     * Should be called at the end of search execution.
     *
     * @return true if a timeout occurred during search execution
     */
    static boolean hasTimeout() {
        try {
            return hasTimeout.get();
        } finally {
            hasTimeout.remove();
        }
    }

    /**
     * Marks that a timeout has occurred in the current thread.
     * Should be called when a SQL timeout exception is caught.
     */
    static void markTimeout() {
        hasTimeout.set(true);
    }
}

