package org.artifactory.storage.db.policy.cleanup.dao;

import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;

import java.util.Arrays;
import java.util.Objects;

/**
 * Cleanup run summary row for retention migration export.
 *
 * @param summary parsed run summary (includes deserialized {@code runConfig})
 * @param runConfigBytes raw {@code run_config} column bytes for byte-accurate Base64 export; kept alongside parsed
 *                       config because re-serializing {@link CleanupRunSummary#runConfig()} may not preserve
 *                       original JSON field order or formatting
 */
public record CleanupRunSummaryMigrationRow(
        CleanupRunSummary summary,
        byte[] runConfigBytes
) {
    /** Records use reference equality for {@code byte[]}; overrides provide content-based equality. */
    @Override
    public String toString() {
        return "CleanupRunSummaryMigrationRow{" +
                "summary=" + summary +
                ", runConfigBytes=" + Arrays.toString(runConfigBytes) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CleanupRunSummaryMigrationRow that = (CleanupRunSummaryMigrationRow) o;
        return Objects.equals(summary, that.summary)
                && runConfigBytesEqual(runConfigBytes, that.runConfigBytes);
    }

    private static boolean runConfigBytesEqual(byte[] left, byte[] right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Arrays.equals(left, right);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(summary);
        result = 31 * result + runConfigBytesHashCode(runConfigBytes);
        return result;
    }

    private static int runConfigBytesHashCode(byte[] bytes) {
        return bytes == null ? 0 : Arrays.hashCode(bytes);
    }
}
