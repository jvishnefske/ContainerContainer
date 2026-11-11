package org.artifactory.storage.db.aql.stats;

import org.artifactory.common.ConstantValues;

/**
 * Immutable payload posted by AQL request threads onto the {@link AqlQueryStatsManager} queue.
 * Carries everything the worker needs so the request thread can release all references after
 * {@code offer()}.
 *
 * <p><b>Bounded payload:</b> {@code template} and {@code sampleQuery} are normalized at
 * construction time -- {@code null} is replaced by {@code ""} and over-cap strings are truncated
 * with {@link #LOG_TRUNCATED_SUFFIX}. This bounds the bytes pinned in the queue and the stats
 * map: at most {@code aqlStatisticsLogFieldMaxChars} chars per field, regardless of how large
 * the originating query was. A runtime change of the cap only affects events captured after the
 * change -- already-captured events keep their capture-time truncation.
 *
 * <p>{@code sampleQuery} is captured once on the first event for a checksum and never overwritten.
 *
 * @author Amir Avidan
 */
public record AqlStatsEvent(String checksum, String template, String sampleQuery,
                            long durationMs, long rowsCount) {

    static final String LOG_TRUNCATED_SUFFIX = "...[truncated]";

    public AqlStatsEvent {
        template = capForStorage(template);
        sampleQuery = capForStorage(sampleQuery);
    }

    private static String capForStorage(String s) {
        if (s == null) {
            return "";
        }
        int cap = ConstantValues.aqlStatisticsLogFieldMaxChars.getInt();
        if (s.length() <= cap) {
            return s;
        }
        return s.substring(0, cap) + LOG_TRUNCATED_SUFFIX;
    }
}
