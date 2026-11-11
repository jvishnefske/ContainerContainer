package org.artifactory.addon.retention;

/**
 * @author Oleksandr Legenkiy
 */
public record RetentionStats(RetentionStatsDetails artifactRetentionStats,
                             RetentionStatsDetails packageRetentionStats) {
}
