package org.artifactory.sapi.interceptor;

/**
 * Defines how to proceed with a copy/move operation on a given item
 */
public enum CopyMoveCheckResult {
    ALLOW, // Allow the operation on the current item
    SKIP, // Skip the operation on the current item and everything under it (not an error)
    REJECT // A problem was found with the given item, skip it and everything under it with an error
}
