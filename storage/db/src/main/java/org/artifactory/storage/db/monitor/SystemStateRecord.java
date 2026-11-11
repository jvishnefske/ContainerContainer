package org.artifactory.storage.db.monitor;

/**
 * Represents a single row in the {@code system_states} table.
 * The {@link #id()} field holds the use-case-specific record key (e.g. the route key for
 * exceeding-rows violations).
 *
 * @author Eli Fishman
 */
public record SystemStateRecord(
        String useCaseName,
        String id,
        long timestamp,
        String nodeId,
        String actor,
        String data) {
}
