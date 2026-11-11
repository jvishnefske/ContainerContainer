package org.artifactory.lock;

/**
 * Native DB locks readiness state for a single Artifactory node.
 */
public enum NativeDbLocksMechanismStatus {

    /**
     * Native DB locks are not supported on this node's database (non-PostgreSQL).
     */
    NOT_SUPPORTED,

    /**
     * Locking mechanism is explicitly set to table-based ({@code jfrog_table_based}).
     */
    SUPPORTED_NOT_READY,

    /**
     * Mechanism is AUTO and the node is still on table locks (descriptor flag is false).
     */
    READY_NOT_IN_USE,

    /**
     * Native DB locks are actively in use on this node.
     */
    IN_USE;

    public static NativeDbLocksMechanismStatus fromName(String statusName) {
        if (statusName == null) {
            return null;
        }
        try {
            return valueOf(statusName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
