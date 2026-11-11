package org.artifactory.sh.cleanup;

/**
 * @author dudim
 */
public enum GarbageCollectorStrategy {
    FULL,
    EVENTS_GC,
    TRASH_AND_BINARIES,
    TRASH,
    TRASH_BINARIES_MARKED_FOR_GC
}
