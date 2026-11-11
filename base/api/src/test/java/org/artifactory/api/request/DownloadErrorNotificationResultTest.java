package org.artifactory.api.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadErrorNotificationResultTest {

    @Test
    void differsFrom_returnsTrueWhenStatusDiffers() {
        DownloadErrorNotificationResult result = new DownloadErrorNotificationResult(409, "conflict");
        assertTrue(result.differsFrom(404, "conflict"));
    }

    @Test
    void differsFrom_returnsTrueWhenMessageDiffers() {
        DownloadErrorNotificationResult result = new DownloadErrorNotificationResult(404, "updated");
        assertTrue(result.differsFrom(404, "original"));
    }

    @Test
    void differsFrom_returnsFalseWhenStatusAndMessageMatch() {
        DownloadErrorNotificationResult result = new DownloadErrorNotificationResult(404, "missing");
        assertFalse(result.differsFrom(404, "missing"));
    }

    @Test
    void differsFrom_returnsFalseWhenBothMessagesAreNull() {
        DownloadErrorNotificationResult result = new DownloadErrorNotificationResult(404, null);
        assertFalse(result.differsFrom(404, null));
    }

    @Test
    void differsFrom_returnsTrueWhenOriginalMessageNullButResultIsNot() {
        DownloadErrorNotificationResult result = new DownloadErrorNotificationResult(404, "worker message");
        assertTrue(result.differsFrom(404, null));
    }
}
