package org.artifactory.throttling.lh.mode;

import java.time.Instant;

/**
 * Outcome of a Load Healer mode-update request.
 *
 * @author Amir Avidan
 */
public record ModeUpdateResult(ModeUpdateStatus status, Instant revertAt) {

    public boolean changed() {
        return status == ModeUpdateStatus.UPDATED || status == ModeUpdateStatus.EXTENDED;
    }
}
