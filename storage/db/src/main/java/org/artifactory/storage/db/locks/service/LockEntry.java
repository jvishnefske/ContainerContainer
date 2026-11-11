package org.artifactory.storage.db.locks.service;

/**
 * Immutable snapshot of a single advisory lock held by this JVM at a given point in time.
 * Populated on {@code acquireLock} and cleared on {@code unlock} / {@code forceUnlock}.
 */
public record LockEntry(String category, String key, long lockedSinceMs, long threadId, String threadName) {
}
