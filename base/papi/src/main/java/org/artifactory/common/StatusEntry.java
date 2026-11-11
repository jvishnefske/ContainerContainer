package org.artifactory.common;

import jakarta.annotation.Nullable;
import org.artifactory.common.status.StatusEntryLevel;

/**
 * This class acts as a bridge for plugins backward compatibility.
 * (e.g., see <a href="https://github.com/jfrog/config-import"/>)
 * <p>
 * Use {@link org.artifactory.common.status.StatusEntry} instead.
 */
@Deprecated
public class StatusEntry extends org.artifactory.common.status.StatusEntry {

    public StatusEntry(
            int statusCode,
            @Nullable String message) {
        super(statusCode, message);
    }

    public StatusEntry(int statusCode,
            StatusEntryLevel level,
            @Nullable String message) {
        super(statusCode, level, message);
    }

    public StatusEntry(
            int statusCode,
            @Nullable String message,
            @Nullable Throwable exception) {
        super(statusCode, message, exception);
    }

    public StatusEntry(
            int statusCode,
            StatusEntryLevel level,
            @Nullable String message,
            @Nullable Throwable exception) {
        super(statusCode, level, message, exception);
    }
}