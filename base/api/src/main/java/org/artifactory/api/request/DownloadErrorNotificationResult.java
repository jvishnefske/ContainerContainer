package org.artifactory.api.request;

import java.util.Objects;

/**
 * Outcome of {@link DownloadService#notifyDownloadError}, after After Download Error plugins and workers run.
 */
public record DownloadErrorNotificationResult(int status, String message) {

    public boolean differsFrom(int originalStatus, String originalMessage) {
        return status != originalStatus || !Objects.equals(message, originalMessage);
    }
}
